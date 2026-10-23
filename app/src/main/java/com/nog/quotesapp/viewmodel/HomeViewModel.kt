package com.nog.quotesapp.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nog.quotesapp.data.repository.*
import com.nog.quotesapp.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HomeUiState(
    val dailyQuote: Quote? = null,
    val quotes: List<Quote> = emptyList(),
    val results: List<Quote> = emptyList(),
    val query: String = "",
    val loading: Boolean = true,
    val rerolling: Boolean = false,
    val error: String? = null
)

@OptIn(FlowPreview::class)
class HomeViewModel(
    private val quotes: QuoteStore,
    private val daily: DailyQuoteStore,
    private val savedState: SavedStateHandle,
    settings: SettingsStore? = null,
    tags: TagStore? = null,
    private val onRerolled:suspend ()->Unit = {}
) : ViewModel() {
    private val query = MutableStateFlow(savedState.get<String>("query") ?: "")
    private val mutableState = MutableStateFlow(HomeUiState(query = query.value))
    val state = mutableState.asStateFlow()
    init {
        viewModelScope.launch {
            val eligible = combine(quotes.observeQuotes(), quotes.observeLinks(),
                tags?.observeTags() ?: flowOf(emptyList()), settings?.observe() ?: flowOf(AppSettings())) { rows, links, availableTags, configuration ->
                filterDailyQuotes(rows,availableTags,links,configuration)
            }
            combine(eligible, daily.observeDailyQuote(), query.debounce(200)) { rows, selected, search ->
                Triple(rows, selected?.takeIf { chosen -> rows.any { it.id == chosen.id } }, rankEntries(rows, search) { it.text })
            }.catch { error ->
                if (error is CancellationException) throw error
                mutableState.update { it.copy(loading = false, error = "Could not load quotes.") }
            }.collect { (rows, selected, results) ->
                mutableState.update { it.copy(quotes = rows, dailyQuote = selected, results = results, loading = false) }
            }
        }
        refresh()
    }
    fun changeQuery(value: String) {
        savedState["query"] = value
        query.value = value
        mutableState.update { it.copy(query = value) }
    }
    fun refresh() { select(false) }
    fun reroll() {
        if (state.value.loading || state.value.quotes.isEmpty()) return
        select(true)
    }
    private fun select(force: Boolean) {
        if (state.value.rerolling) return
        mutableState.update { it.copy(rerolling = true, error = null) }
        viewModelScope.launch {
            try { daily.refresh(force);if(force) onRerolled() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutableState.update { it.copy(error = "Could not select a quote. Please try again.") } }
            finally { mutableState.update { it.copy(rerolling = false) } }
        }
    }
}
