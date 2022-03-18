package impl

import Instrument
import InstrumentEvent
import Quote
import QuoteEvent
import io.mockk.every
import io.mockk.mockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap


class CandlestickManagerImplTest {

    @Test
    fun `when delay between quotes more than minute expect they are in different candlesticks`() {
        val codeAndQuotesEvent = generateCodeAndQuoteEvents(3)

        val isin = codeAndQuotesEvent.first

        val instrumentCache = ConcurrentHashMap(mutableMapOf(Pair(isin, Instrument(isin, isin))))

        val candleStickManager = CandlestickManagerImpl(instrumentCache, ConcurrentHashMap())

        var startTime = Instant.parse("2022-03-05T15:30:45.123Z")
        val delta = Duration.ofSeconds(62)

        mockkStatic("java.time.Instant")
        val quotesWithInstants = codeAndQuotesEvent.second.map {
            InstantWithQuoteEvent(startTime, it).also { startTime = startTime.plus(delta) }
        }.toList()

        quotesWithInstants.forEach {
            every { Instant.now() } returns it.instant
            candleStickManager.handleQuote(it.event.data)
        }

        assertEquals(3, candleStickManager.getCandlesticks(isin).size)
    }

    @Test
    fun `when instrument is deleted expect empty result`() {
        val codeAndQuotesEvent = generateCodeAndQuoteEvents(3)
        val isinToDelete = codeAndQuotesEvent.first

        val instrumentToDelete = Instrument(isinToDelete, isinToDelete)
        val instrumentCache = ConcurrentHashMap(mutableMapOf(Pair(isinToDelete, instrumentToDelete)))
        val candleStickManager = CandlestickManagerImpl(instrumentCache, ConcurrentHashMap())

        codeAndQuotesEvent.second.forEach { candleStickManager.handleQuote(it.data) }

        assertEquals(1, candleStickManager.getCandlesticks(isinToDelete).size)

        candleStickManager.handleInstrument(instrumentToDelete, InstrumentEvent.Type.DELETE)

        assertEquals(0, candleStickManager.getCandlesticks(isinToDelete).size)
    }

    @Test
    fun `when instrument cache does not contain isin expect empty result`() {
        val codeQuotesEvent = generateCodeAndQuoteEvents(2)
        val isin = codeQuotesEvent.first

        val instrumentCache = ConcurrentHashMap(mutableMapOf<String, Instrument>())
        val candleStickManager = CandlestickManagerImpl(instrumentCache, ConcurrentHashMap())

        codeQuotesEvent.second.forEach { candleStickManager.handleQuote(it.data) }

        assertEquals(0, candleStickManager.getCandlesticks(isin).size)
    }

    @Test
    fun `when add elements in 2 minutes expect there will be 2 elements (happy path unique isin)`() {
        val codeAndQuotesEvent = generateCodeAndQuoteEvents(10)
        val isin = codeAndQuotesEvent.first

        val instrumentCache = ConcurrentHashMap(mutableMapOf(Pair(isin, Instrument(isin, isin))))
        val candleStickManager = CandlestickManagerImpl(instrumentCache, ConcurrentHashMap())


        var startTime = Instant.parse("2022-03-05T15:30:45.123Z")
        val delta = Duration.ofSeconds(10)

        mockkStatic("java.time.Instant")

        val quotesWithInstants = codeAndQuotesEvent.second.map {
            InstantWithQuoteEvent(startTime, it).also { startTime = startTime.plus(delta) }
        }.toList()

        // send every 10 seconds
        quotesWithInstants.forEach {
            every { Instant.now() } returns it.instant
            candleStickManager.handleQuote(it.event.data)
        }

        assertEquals(2, candleStickManager.getCandlesticks(isin).size)
    }

    @Test
    fun `happy path 2 isins`() {
        val codeAndQuotesEvent1 = generateCodeAndQuoteEvents(10)
        val codeAndQuotesEvent2 = generateCodeAndQuoteEvents(20)

        val isin1 = codeAndQuotesEvent1.first
        val isin2 = codeAndQuotesEvent2.first

        val isin1List = codeAndQuotesEvent1.second
        val isin2List = codeAndQuotesEvent2.second

        val instrumentCache = ConcurrentHashMap(
            mutableMapOf(
                Pair(isin1, Instrument(isin1, isin1)),
                Pair(isin2, Instrument(isin2, isin2))
            )
        )

        val candleStickManager = CandlestickManagerImpl(instrumentCache, ConcurrentHashMap())

        mockkStatic("java.time.Instant")

        var ts1 = Instant.parse("2022-03-05T15:30:45.123Z")
        var ts2 = Instant.parse("2022-03-05T15:30:50.123Z")
        val delta = Duration.ofSeconds(10)


        val quotesWithInstants1 = isin1List.map {
            InstantWithQuoteEvent(ts1, it).also { ts1 = ts1.plus(delta) }
        }.toList()

        val quotesWithInstants2 = isin2List.map {
            InstantWithQuoteEvent(ts2, it).also { ts2 = ts2.plus(delta) }
        }.toList()

        suspend fun run(
            list: List<InstantWithQuoteEvent>,
            list1: List<InstantWithQuoteEvent>
        ): List<Any> = withContext(Dispatchers.IO) {
            listOf(
                launch {
                    list.forEach {
                        every { Instant.now() } returns it.instant
                        candleStickManager.handleQuote(it.event.data)
                    }
                },
                launch {
                    list1.forEach {
                        every { Instant.now() } returns it.instant
                        candleStickManager.handleQuote(it.event.data)
                    }
                })
        }

        runBlocking {
            run(quotesWithInstants1, quotesWithInstants2)
        }

        assertEquals(2, candleStickManager.getCandlesticks(isin1).size)
        assertEquals(4, candleStickManager.getCandlesticks(isin2).size)
    }

    @Test
    fun `when 31 minutes pass expect earliest element result size is 30 and earliest candlestick is not in list`() {
        val codeAndQuoteEvents = generateCodeAndQuoteEvents(1520)
        val isin = codeAndQuoteEvents.first
        val list = codeAndQuoteEvents.second

        // set up instrument cache
        val instrumentCache = ConcurrentHashMap(mutableMapOf(Pair(isin, Instrument(isin, isin))))

        val candleStickManager = CandlestickManagerImpl(instrumentCache, ConcurrentHashMap())


        // start from time
        var startTime = Instant.parse("2022-03-05T15:30:45.123Z")
        val delta = Duration.ofSeconds(10)

        mockkStatic("java.time.Instant")

        val quotesWithInstants = list.map {
            InstantWithQuoteEvent(startTime, it).also { startTime = startTime.plus(delta) }
        }.toList()

        // send every 10 seconds
        quotesWithInstants.forEach {
            every { Instant.now() } returns it.instant
            candleStickManager.handleQuote(it.event.data)
        }

        val candlesticks = candleStickManager.getCandlesticks(isin)
        assertEquals(30, candlesticks.size)
        assertNull(candlesticks.find { it.openTimestamp == startTime })
    }

}


/**
 * Generate pair of ISIN and list of quote events
 */
private fun generateCodeAndQuoteEvents(amount: Int): Pair<String, List<QuoteEvent>> {
    // generate a code
    val code = "Q" + amount.toString() + "T"
    return Pair(code, (1..amount).map {
        QuoteEvent(
            Quote(
                code,
                // generate different prices
                (if (it % 20 == 0) it - 1 else it + 2).toDouble()
            )
        )
    }.toList())
}

data class InstantWithQuoteEvent(
    val instant: Instant,
    val event: QuoteEvent
)
