import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import impl.CandlestickManagerImpl
import java.util.concurrent.ConcurrentHashMap

fun main() {
    println("starting up")

    val server = Server()
    val instrumentStream = InstrumentStream()
    val quoteStream = QuoteStream()
    val instrumentCache = ConcurrentHashMap<String, Instrument>()
    val quotesCache = ConcurrentHashMap<String, MutableList<Candlestick>>()
    val candlestickManager = CandlestickManagerImpl(instrumentCache, quotesCache)


    instrumentStream.connect { event ->
        println(event)
        candlestickManager.handleInstrument(event.data, event.type)
    }

    quoteStream.connect { event ->
        println(event)
        candlestickManager.handleQuote(event.data)
    }

    server.service = candlestickManager

    server.start()

}

val jackson: ObjectMapper =
    jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
