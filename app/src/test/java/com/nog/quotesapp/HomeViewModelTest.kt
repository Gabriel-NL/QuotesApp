package com.nog.quotesapp

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.nog.quotesapp.data.repository.*
import com.nog.quotesapp.model.*
import com.nog.quotesapp.viewmodel.HomeViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var model: HomeViewModel
    private val quotes = FakeQuotes()
    private val daily = FakeDaily()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { if(::model.isInitialized) model.viewModelScope.cancel(); Dispatchers.resetMain() }
    @Test fun loadsDailyAndDebouncesSearchWithoutDroppingOtherResults() = runTest(dispatcher) {
        quotes.rows.value = listOf(Quote(1,"Alpha"),Quote(2,"Blue sun"),Quote(3,"Sunshine"))
        daily.selected.value = quotes.rows.value[1]
        model=HomeViewModel(quotes,daily,SavedStateHandle())
        advanceTimeBy(201); runCurrent()
        assertFalse(model.state.value.loading)
        assertEquals(2,model.state.value.dailyQuote?.id)
        model.changeQuery("sun"); runCurrent()
        assertEquals("sun",model.state.value.query)
        assertEquals("Alpha",model.state.value.results.first().text)
        advanceTimeBy(201); runCurrent()
        assertEquals(listOf(2,3,1),model.state.value.results.map { it.id })
        assertEquals(3,model.state.value.results.size)
    }
    @Test fun emptyDatabaseDoesNotReroll() = runTest(dispatcher) {
        model=HomeViewModel(quotes,daily,SavedStateHandle()); advanceTimeBy(201); runCurrent()
        model.reroll(); runCurrent()
        assertTrue(model.state.value.quotes.isEmpty())
        assertEquals(0,daily.rerolls)
    }
    @Test fun onlySuccessfulHomeRerollRequestsANewNotification()=runTest(dispatcher) {
        quotes.rows.value=listOf(Quote(1,"First"));daily.selected.value=quotes.rows.value.first()
        var deliveries=0
        model=HomeViewModel(quotes,daily,SavedStateHandle(),onRerolled={deliveries++})
        advanceTimeBy(201);runCurrent();assertEquals(0,deliveries)
        daily.next=quotes.rows.value.first();model.reroll();runCurrent();assertEquals(1,deliveries)
        daily.fail=true;model.reroll();runCurrent();assertEquals(1,deliveries)
    }
    @Test fun filterChangesUpdateHomeResultsAndHideDisallowedDaily()=runTest(dispatcher) {
        quotes.rows.value=listOf(Quote(1,"Work"),Quote(2,"Other"))
        quotes.links.value=listOf(QuoteTagLink(1,7))
        daily.selected.value=quotes.rows.value.first()
        val configuration=MutableStateFlow(AppSettings())
        val settings=object:SettingsStore {
            override fun observe()=configuration
            override suspend fun current()=configuration.value
            override suspend fun save(settings:AppSettings) { configuration.value=settings }
            override suspend fun clearHistory()=Unit
        }
        val tags=object:TagStore {
            override fun observeTags()=flowOf(listOf(Tag(7,"Work")))
            override suspend fun saveTag(id:Int?,name:String)=Unit
            override suspend fun deleteTags(ids:List<Int>)=Unit
        }
        model=HomeViewModel(quotes,daily,SavedStateHandle(),settings,tags)
        advanceTimeBy(201);runCurrent()
        configuration.value=AppSettings(dailyTagNames=setOf("Work"));runCurrent()
        assertEquals(listOf(2),model.state.value.results.map { it.id })
        assertNull(model.state.value.dailyQuote)
        configuration.value=configuration.value.copy(dailyWhitelist=true);runCurrent()
        assertEquals(listOf(1),model.state.value.results.map { it.id })
        assertEquals(1,model.state.value.dailyQuote?.id)
    }
    @Test fun rerollUpdatesSharedDailyAndFailureKeepsPreviousQuote() = runTest(dispatcher) {
        quotes.rows.value=listOf(Quote(1,"First"),Quote(2,"Second"))
        daily.selected.value=Quote(1,"First")
        model=HomeViewModel(quotes,daily,SavedStateHandle()); advanceTimeBy(201); runCurrent()
        daily.next=Quote(2,"Second"); model.reroll(); runCurrent()
        assertEquals(2,model.state.value.dailyQuote?.id)
        daily.fail=true; model.reroll(); runCurrent()
        assertNotNull(model.state.value.error)
        assertEquals(2,model.state.value.dailyQuote?.id)
        assertFalse(model.state.value.rerolling)
    }
    private class FakeDaily : DailyQuoteStore {
        val selected=MutableStateFlow<Quote?>(null)
        var next:Quote?=null; var rerolls=0; var fail=false
        override fun observeDailyQuote()=selected
        override suspend fun refresh(force:Boolean):Quote? {
            check(!fail)
            if(force) { rerolls++; selected.value=next }
            return selected.value
        }
    }
    private class FakeQuotes : QuoteStore {
        val links=MutableStateFlow(emptyList<QuoteTagLink>())
        val rows=MutableStateFlow(emptyList<Quote>())
        override fun observeQuotes()=rows
        override fun observeLinks()=links
        override suspend fun loadQuote(id:Int):QuoteDraft=error("unused")
        override suspend fun createQuote(text:String,tagIds:List<Int>):Long=error("unused")
        override suspend fun updateQuote(id:Int,text:String,tagIds:List<Int>)=Unit
        override suspend fun deleteQuotes(ids:List<Int>)=Unit
    }
}
