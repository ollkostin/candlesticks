package impl

import Candlestick
import CandlestickManager
import Instrument
import InstrumentEvent
import Quote
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentMap

/**
 * CandlestickManager implementation.
 * Uses two storages for instruments and quotes.
 */
class CandlestickManagerImpl(
    private val instrumentCache: ConcurrentMap<String, Instrument>,
    private val quotesCache: ConcurrentMap<String, MutableList<Candlestick>>,
) : CandlestickManager {

    override fun getCandlesticks(isin: String): List<Candlestick> {
        return quotesCache.getOrDefault(isin, emptyList())
    }

    override fun handleInstrument(instrument: Instrument, eventType: InstrumentEvent.Type) {
        println("$eventType Instrument $instrument")
        if (eventType == InstrumentEvent.Type.ADD) {
            instrumentCache[instrument.isin] = instrument
        } else if (eventType == InstrumentEvent.Type.DELETE) {
            instrumentCache.remove(instrument.isin)
            quotesCache.remove(instrument.isin)
        }
    }

    override fun handleQuote(quote: Quote) {
        println("Check if instrument is known ${quote.isin}")
        if (instrumentCache.containsKey(quote.isin)) {
            val candles: MutableList<Candlestick> =
                quotesCache.getOrDefault(quote.isin, ArrayList())

            val quoteTimestamp = Instant.now()
            println("Now $quoteTimestamp. Get last candlestick")

            var lastQuote: Candlestick? = candles.findLast {
                Duration.between(it.openTimestamp, quoteTimestamp) < Duration.ofMinutes(1)
            }

            if (lastQuote == null) {
                println("No candlestick within a minute. Create new one")
                lastQuote = Candlestick(
                    openTimestamp = quoteTimestamp,
                    closeTimestamp = quoteTimestamp,
                    openPrice = quote.price,
                    highPrice = quote.price,
                    lowPrice = quote.price,
                    closingPrice = quote.price
                )
                candles.add(lastQuote)
            } else {
                println("Found candlestick within a minute. Update")
                lastQuote.highPrice = if (quote.price > lastQuote.highPrice) quote.price else lastQuote.highPrice
                lastQuote.lowPrice = if (quote.price < lastQuote.lowPrice) quote.price else lastQuote.lowPrice
                lastQuote.closeTimestamp = quoteTimestamp
                lastQuote.closingPrice = quote.price
                candles[candles.indexOf(lastQuote)] = lastQuote
            }
            if (candles.size > 30) {
                candles.minByOrNull { it.openTimestamp }
                    ?.also {
                        candles.remove(it)
                    }
            }
            quotesCache[quote.isin] = candles
        } else {
            println("No isin [${quote.isin}] found")
        }
    }
}