package com.demo.wealth.domain.market

import com.demo.wealth.data.StockCandle

interface MarketDataProvider {
    suspend fun loadDailyCandles(symbol: String): List<StockCandle>
}

object StockCsvParser {
    fun parse(symbol: String, text: String): List<StockCandle> =
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .dropWhile { it.contains("date", ignoreCase = true) || it.contains("日期") }
            .mapNotNull { parseLine(symbol, it) }
            .sortedBy { it.date }
            .toList()

    private fun parseLine(symbol: String, line: String): StockCandle? {
        val parts = line.split(",", "\t", ";").map { it.trim() }
        if (parts.size < 6) return null
        return StockCandle(
            symbol = symbol,
            date = parts[0],
            open = parts[1].toDoubleOrNull() ?: return null,
            high = parts[2].toDoubleOrNull() ?: return null,
            low = parts[3].toDoubleOrNull() ?: return null,
            close = parts[4].toDoubleOrNull() ?: return null,
            volume = parts[5].toDoubleOrNull() ?: 0.0
        )
    }
}

class CsvDataProvider(
    private val symbol: String,
    private val csvText: String
) : MarketDataProvider {
    override suspend fun loadDailyCandles(symbol: String): List<StockCandle> =
        StockCsvParser.parse(this.symbol.ifBlank { symbol }, csvText)
}

class HttpDailyProvider(
    private val endpoint: String,
    private val fetchText: suspend (String) -> String
) : MarketDataProvider {
    override suspend fun loadDailyCandles(symbol: String): List<StockCandle> {
        val url = endpoint.replace("{symbol}", symbol)
        return StockCsvParser.parse(symbol, fetchText(url))
    }
}
