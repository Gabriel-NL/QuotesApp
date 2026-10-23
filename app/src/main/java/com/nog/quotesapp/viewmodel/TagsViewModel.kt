package com.nog.quotesapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nog.quotesapp.data.repository.TagStore
import com.nog.quotesapp.model.Tag
import com.nog.quotesapp.model.SettingsStore
import com.nog.quotesapp.model.EntryLimits
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class TagsUiState(
    val tagLimit:Int=EntryLimits.TAG_NAME_LENGTH,
    val tags: List<Tag> = emptyList(), val loading: Boolean = true,
    val error: String? = null, val editorError: String? = null,
    val saving: Boolean = false, val deleting: Boolean = false,
    val savedVersion: Int = 0, val deletedVersion: Int = 0
)

class TagsViewModel(private val tags: TagStore, private val settings:SettingsStore?=null) : ViewModel() {
    private val mutableState = MutableStateFlow(TagsUiState())
    val state = mutableState.asStateFlow()
    init {
        settings?.let { store -> viewModelScope.launch {
            store.observe().catch { error -> if(error is CancellationException) throw error;mutableState.update { it.copy(error="Could not load limits.") } }
                .collect { value -> mutableState.update { it.copy(tagLimit=value.tagLimit) } }
        } }
        viewModelScope.launch {
            tags.observeTags().catch { error ->
                if (error is CancellationException) throw error
                mutableState.update { it.copy(loading = false, error = "Could not load tags.") }
            }.collect { rows -> mutableState.update { it.copy(tags = rows, loading = false) } }
        }
    }
    fun clearEditorError() { mutableState.update { it.copy(editorError = null) } }
    fun saveTag(id: Int?, name: String) {
        if (state.value.saving || name.isBlank()) return
        mutableState.update { it.copy(saving = true, editorError = null) }
        viewModelScope.launch {
            try {
                tags.saveTag(id, name)
                mutableState.update { it.copy(savedVersion = it.savedVersion + 1) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutableState.update { it.copy(editorError = "Could not save the tag.") } }
            finally { mutableState.update { it.copy(saving = false) } }
        }
    }
    fun deleteTags(ids: List<Int>) {
        if (state.value.deleting || ids.isEmpty()) return
        mutableState.update { it.copy(deleting = true, error = null) }
        viewModelScope.launch {
            try {
                tags.deleteTags(ids)
                mutableState.update { it.copy(deletedVersion = it.deletedVersion + 1) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutableState.update { it.copy(error = "Could not delete tags.") } }
            finally { mutableState.update { it.copy(deleting = false) } }
        }
    }
}
