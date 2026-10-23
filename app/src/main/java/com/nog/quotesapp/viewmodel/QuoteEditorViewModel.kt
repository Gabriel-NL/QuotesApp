package com.nog.quotesapp.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nog.quotesapp.data.repository.QuoteStore
import com.nog.quotesapp.data.repository.TagStore
import com.nog.quotesapp.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class QuoteEditorUiState(
    val quoteLimit:Int = EntryLimits.QUOTE_LENGTH,
    val loading: Boolean = true, val isEditing: Boolean = false,
    val initialText: String = "", val initialTagIds: List<Int> = emptyList(),
    val text: String = "", val selectedTagIds: List<Int> = emptyList(),
    val tags: List<Tag> = emptyList(), val tagsError: String? = null,
    val saving: Boolean = false, val saved: Boolean = false, val error: String? = null
) {
    val dirty: Boolean get() = text != initialText || selectedTagIds.toSet() != initialTagIds.toSet()
}

class QuoteEditorViewModel(
    private val quotes: QuoteStore,
    private val tags: TagStore,
    private val quoteId: Int?,
    private val savedState: SavedStateHandle,
    private val settings: SettingsStore? = null
) : ViewModel() {
    private val mutableState = MutableStateFlow(QuoteEditorUiState(isEditing = quoteId != null))
    val state = mutableState.asStateFlow()
    init {
        settings?.let { store -> viewModelScope.launch {
            store.observe().catch { error -> if(error is CancellationException) throw error;mutableState.update { it.copy(error="Could not load limits.") } }
                .collect { value -> mutableState.update { it.copy(quoteLimit=value.quoteLimit) } }
        } }
        viewModelScope.launch {
            tags.observeTags().catch { error ->
                if (error is CancellationException) throw error
                mutableState.update { it.copy(tagsError = "Could not load tags.") }
            }.collect { rows -> mutableState.update { it.copy(tags = rows) } }
        }
        viewModelScope.launch {
            try {
                val draft = quoteId?.let { quotes.loadQuote(it) }
                val originalText = draft?.quote?.text ?: ""
                val originalTags = draft?.tagIds ?: emptyList()
                mutableState.update { it.copy(
                    loading = false, initialText = originalText, initialTagIds = originalTags,
                    text = savedState.get<String>("text") ?: originalText,
                    selectedTagIds = savedState.get<ArrayList<Int>>("tagIds")?.toList() ?: originalTags
                ) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutableState.update { it.copy(error = "Could not load this quote. It may have been deleted.") } }
        }
    }
    fun changeText(text: String) {
        if (state.value.saving) return
        val value = text.replace("\r", "").replace("\n", " ")
        savedState["text"] = value
        mutableState.update { it.copy(text = value) }
    }
    fun changeTags(ids: List<Int>) {
        if (state.value.saving) return
        savedState["tagIds"] = ArrayList(ids.distinct())
        mutableState.update { it.copy(selectedTagIds = ids.distinct()) }
    }
    fun reset() {
        if(state.value.saving) return
        savedState["text"] = state.value.initialText
        savedState["tagIds"] = ArrayList(state.value.initialTagIds)
        mutableState.update { it.copy(text=it.initialText, selectedTagIds=it.initialTagIds) }
    }
    fun save() {
        val draft = state.value
        if (draft.loading || draft.saving || draft.saved || draft.text.isBlank() || (draft.text!=draft.initialText && draft.text.length>draft.quoteLimit)) return
        mutableState.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                if (quoteId == null) quotes.createQuote(draft.text, draft.selectedTagIds)
                else quotes.updateQuote(quoteId, draft.text, draft.selectedTagIds)
                mutableState.update { it.copy(saved = true) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutableState.update { it.copy(error = "Could not save. Check whether a selected tag was deleted and try again.") } }
            finally { mutableState.update { it.copy(saving = false) } }
        }
    }
}
