import java.time.Instant

data class InstrumentEvent(val type: Type, val data: Instrument) {
    enum class Type {
        ADD,
        DELETE
    }
}

data class QuoteEvent(val data: Quote)

data class Instrument(val isin: ISIN, val description: String)
typealias ISIN = String

data class Quote(val isin: ISIN, val price: Price)
typealias Price = Double


interface CandlestickManager {
    /**
     * Get candlesticks by isin for last 30 minutes
     */
    fun getCandlesticks(isin: String): List<Candlestick>

    /**
     * Handle instrument event.
     * ADD - add element to storage
     * DELETE - delete element from storage
     */
    fun handleInstrument(instrument: Instrument, eventType: InstrumentEvent.Type)

    /**
     * Handle quote
     */
    fun handleQuote(quote: Quote)
}

data class Candlestick(
    val openTimestamp: Instant,
    var closeTimestamp: Instant,
    val openPrice: Price,
    var highPrice: Price,
    var lowPrice: Price,
    var closingPrice: Price
)