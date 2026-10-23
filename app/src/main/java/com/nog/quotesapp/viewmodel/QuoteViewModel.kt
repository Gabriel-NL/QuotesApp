package com.nog.quotesapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nog.quotesapp.data.repository.QuoteStore
import com.nog.quotesapp.data.repository.TagStore
import com.nog.quotesapp.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class QuotesUiState(
    val quotes: List<Quote> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val links: List<QuoteTagLink> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val deleting: Boolean = false,
    val deletedVersion: Int = 0
)

class QuoteViewModel(private val quotes: QuoteStore, private val tags: TagStore) : ViewModel() {
    private val mutableState = MutableStateFlow(QuotesUiState())
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(quotes.observeQuotes(), tags.observeTags(), quotes.observeLinks()) { q, t, l -> Triple(q,t,l) }
                .catch { error ->
                    if (error is CancellationException) throw error
                    mutableState.update { it.copy(loading = false, error = "Could not load quotes or tags.") }
                }.collect { (q,t,l) ->
                    mutableState.update { it.copy(quotes = q, tags = t, links = l, loading = false) }
                }
        }
    }

    fun deleteQuotes(ids: List<Int>) {
        if (state.value.deleting || ids.isEmpty()) return
        mutableState.update { it.copy(deleting = true, error = null) }
        viewModelScope.launch {
            try {
                quotes.deleteQuotes(ids)
                mutableState.update { it.copy(deletedVersion = it.deletedVersion + 1) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutableState.update { it.copy(error = "Could not delete quotes.") } }
            finally { mutableState.update { it.copy(deleting = false) } }
        }
    }
}
