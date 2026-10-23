package com.nog.quotesapp

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nog.quotesapp.data.repository.*
import com.nog.quotesapp.model.*
import com.nog.quotesapp.viewmodel.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = mutableListOf<ViewModel>()
    private val quotes = FakeQuotes()
    private val tags = FakeTags()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() {
        models.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }
    private fun <T : ViewModel> track(model: T): T { models += model; return model }

    @Test fun quoteListReceivesRepositoryUpdatesAndDeleteCompletion() = runTest(dispatcher) {
        val model = track(QuoteViewModel(quotes, tags))
        runCurrent()
        assertFalse(model.state.value.loading)
        quotes.rows.value = listOf(Quote(1, "Stored"))
        tags.rows.value = listOf(Tag(2, "Tag"))
        quotes.links.value = listOf(QuoteTagLink(1,2))
        runCurrent()
        assertEquals("Stored", model.state.value.quotes.single().text)
        assertEquals(2, model.state.value.links.single().tagId)
        model.deleteQuotes(listOf(1))
        runCurrent()
        assertTrue(model.state.value.quotes.isEmpty())
        assertEquals(1, model.state.value.deletedVersion)
        assertFalse(model.state.value.deleting)
    }
    @Test fun failedDeletePreservesListAndDoesNotSignalSuccess() = runTest(dispatcher) {
        quotes.rows.value = listOf(Quote(1,"Keep"))
        val model = track(QuoteViewModel(quotes,tags))
        runCurrent(); quotes.failWrites = true
        model.deleteQuotes(listOf(1)); runCurrent()
        assertEquals("Keep", model.state.value.quotes.single().text)
        assertEquals(0,model.state.value.deletedVersion)
        assertNotNull(model.state.value.error)
        assertFalse(model.state.value.deleting)
    }
    @Test fun tagSaveAndRenameAreRepositoryActions() = runTest(dispatcher) {
        val model = track(TagsViewModel(tags))
        runCurrent(); model.saveTag(null,"Created"); runCurrent()
        assertEquals("Created",model.state.value.tags.single().name)
        val id = model.state.value.tags.single().id
        model.saveTag(id,"Renamed"); runCurrent()
        assertEquals(id,model.state.value.tags.single().id)
        assertEquals("Renamed",model.state.value.tags.single().name)
        assertEquals(2,model.state.value.savedVersion)
        model.deleteTags(listOf(id)); runCurrent()
        assertTrue(model.state.value.tags.isEmpty())
        assertEquals(1,model.state.value.deletedVersion)
    }
    @Test fun failedTagSaveKeepsPopupErrorAndDoesNotSignalClose() = runTest(dispatcher) {
        val model = track(TagsViewModel(tags)); runCurrent()
        tags.failWrites = true
        model.saveTag(null,"Draft"); runCurrent()
        assertEquals(0,model.state.value.savedVersion)
        assertNotNull(model.state.value.editorError)
        assertFalse(model.state.value.saving)
    }
    @Test fun editorLoadsTextAndLinksThenResetsDraft() = runTest(dispatcher) {
        quotes.rows.value = listOf(Quote(1,"Original"))
        quotes.links.value = listOf(QuoteTagLink(1,2))
        val model = track(QuoteEditorViewModel(quotes,tags,1,SavedStateHandle()))
        runCurrent()
        assertFalse(model.state.value.loading)
        assertEquals("Original",model.state.value.text)
        assertEquals(listOf(2),model.state.value.selectedTagIds)
        assertFalse(model.state.value.dirty)
        model.changeText("Changed"); model.changeTags(listOf(3))
        assertTrue(model.state.value.dirty)
        model.reset()
        assertEquals("Original",model.state.value.text)
        assertEquals(listOf(2),model.state.value.selectedTagIds)
        assertFalse(model.state.value.dirty)
        assertEquals("Original",quotes.rows.value.single().text)
    }
    @Test fun draftSurvivesSavedStateAndFailedSaveRemainsEditable() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val original = track(QuoteEditorViewModel(quotes,tags,null,saved))
        runCurrent(); original.changeText("Restored draft"); original.changeTags(listOf(4))
        val restored = track(QuoteEditorViewModel(quotes,tags,null,saved))
        runCurrent()
        assertEquals("Restored draft",restored.state.value.text)
        assertEquals(listOf(4),restored.state.value.selectedTagIds)
        quotes.failWrites = true; restored.save(); runCurrent()
        assertFalse(restored.state.value.saved)
        assertFalse(restored.state.value.saving)
        assertEquals("Restored draft",restored.state.value.text)
        assertNotNull(restored.state.value.error)
    }
    @Test fun duplicateSaveClicksCreateOnlyOneRecord() = runTest(dispatcher) {
        val model = track(QuoteEditorViewModel(quotes,tags,null,SavedStateHandle()))
        runCurrent(); model.changeText("New"); model.changeTags(listOf(2))
        model.save(); model.save(); runCurrent()
        assertTrue(model.state.value.saved)
        assertEquals(1,quotes.rows.value.size)
        assertEquals(listOf(QuoteTagLink(1,2)),quotes.links.value)
    }
    @Test fun loadFailureIsReportedWithoutAnEditableEmptyQuote() = runTest(dispatcher) {
        val model = track(QuoteEditorViewModel(quotes,tags,999,SavedStateHandle()))
        runCurrent()
        assertTrue(model.state.value.loading)
        assertNotNull(model.state.value.error)
    }

    private class FakeQuotes : QuoteStore {
        val rows = MutableStateFlow(emptyList<Quote>())
        val links = MutableStateFlow(emptyList<QuoteTagLink>())
        var failWrites = false
        override fun observeQuotes() = rows
        override fun observeLinks() = links
        override suspend fun loadQuote(id: Int) = QuoteDraft(rows.value.first { it.id == id },
            links.value.filter { it.quoteId == id }.map { it.tagId })
        override suspend fun createQuote(text: String, tagIds: List<Int>): Long {
            check(!failWrites)
            val id = (rows.value.maxOfOrNull { it.id } ?: 0)+1
            rows.value += Quote(id,text)
            links.value += tagIds.map { QuoteTagLink(id,it) }
            return id.toLong()
        }
        override suspend fun updateQuote(id: Int,text: String,tagIds: List<Int>) {
            check(!failWrites)
            rows.value = rows.value.map { if(it.id == id) it.copy(text=text) else it }
            links.value = links.value.filter { it.quoteId != id }+tagIds.map { QuoteTagLink(id,it) }
        }
        override suspend fun deleteQuotes(ids: List<Int>) {
            check(!failWrites)
            rows.value = rows.value.filter { it.id !in ids }
            links.value = links.value.filter { it.quoteId !in ids }
        }
    }
    private class FakeTags : TagStore {
        val rows = MutableStateFlow(emptyList<Tag>())
        var failWrites = false
        override fun observeTags() = rows
        override suspend fun saveTag(id: Int?,name: String) {
            check(!failWrites)
            if(id == null) rows.value += Tag((rows.value.maxOfOrNull { it.id } ?: 0)+1,name)
            else rows.value = rows.value.map { if(it.id == id) it.copy(name=name) else it }
        }
        override suspend fun deleteTags(ids: List<Int>) {
            check(!failWrites)
            rows.value = rows.value.filter { it.id !in ids }
        }
    }
}
