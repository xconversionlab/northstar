package com.prithvi.northstar.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Xml
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import org.xmlpull.v1.XmlPullParser

object DiscoverySync {
    private const val MAX_BYTES_PER_QUERY = 320_000
    private const val MAX_BYTES_PER_RUN = 1_100_000
    private val searches = listOf(
        "creator stories independent YouTube creators storytelling", 
        "technology artificial intelligence useful ideas science discovery", 
        "founder biography career reinvention resilience true story", 
        "creative work psychology habits learning unusual history"
    )

    data class SyncResult(val articles: Int, val bytes: Int, val message: String)

    fun refresh(context: Context): SyncResult {
        val store = NorthstarStore(context)
        val dailyLimit = store.getInt(NorthstarStore.KEY_DAILY_LIMIT_MB, 5) * 1024 * 1024
        val allowCellular = store.getBoolean(NorthstarStore.KEY_ALLOW_CELLULAR, true)
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivity.activeNetwork
        val capabilities = activeNetwork?.let { connectivity.getNetworkCapabilities(it) }
        if (capabilities == null || !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return SyncResult(0, 0, "No internet connection is available.")
        }
        val isUnmetered = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        if (!allowCellular && !isUnmetered) {
            return SyncResult(0, 0, "Mobile data is disabled for discovery. Connect to unmetered Wi-Fi or change Settings.")
        }
        val remaining = dailyLimit - store.bytesUsedToday()
        if (remaining <= 0) return SyncResult(0, 0, "Daily data limit reached. Change it in Settings.")

        val collected = mutableListOf<DiscoveryArticle>()
        var totalBytes = 0
        for ((index, query) in searches.withIndex()) {
            val budget = minOf(MAX_BYTES_PER_QUERY, MAX_BYTES_PER_RUN - totalBytes, remaining - totalBytes)
            if (budget <= 0) break
            val result = fetch(query, budget, categoryFor(index))
            totalBytes += result.second
            collected += result.first
        }

        if (collected.isNotEmpty()) {
            val merged = (collected + store.loadArticles())
                .distinctBy { it.url }
                .take(80)
            store.saveArticles(merged)
        }
        if (totalBytes > 0) store.markSync(totalBytes)
        val msg = if (collected.isEmpty()) "No new stories found. Try again later." else "Found ${collected.size} new stories."
        return SyncResult(collected.size, totalBytes, msg)
    }

    private fun categoryFor(index: Int): String = when (index) {
        0 -> "CREATOR STORIES"
        1 -> "TECH & SCIENCE"
        2 -> "PEOPLE & CAREERS"
        else -> "IDEAS & CULTURE"
    }

    private fun fetch(query: String, maxBytes: Int, category: String): Pair<List<DiscoveryArticle>, Int> {
        var connection: HttpURLConnection? = null
        return try {
            val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
            val url = URL("https://news.google.com/rss/search?q=$encoded&hl=en-IN&gl=IN&ceid=IN:en")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 10_000
                setRequestProperty("User-Agent", "NorthstarPersonalReader/0.1 (Android)")
            }
            val input = connection.inputStream
            val buffer = ByteArray(8_192)
            val output = java.io.ByteArrayOutputStream()
            var count = 0
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                val allowed = minOf(read, maxBytes - count)
                if (allowed <= 0) break
                output.write(buffer, 0, allowed)
                count += allowed
                if (allowed < read || count >= maxBytes) break
            }
            input.close()
            parse(output.toByteArray(), category) to count
        } catch (_: Exception) {
            emptyList<DiscoveryArticle>() to 0
        } finally {
            connection?.disconnect()
        }
    }

    private fun parse(bytes: ByteArray, category: String): List<DiscoveryArticle> {
        if (bytes.isEmpty()) return emptyList()
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), "UTF-8")
        val results = mutableListOf<DiscoveryArticle>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT && results.size < 10) {
            if (event == XmlPullParser.START_TAG && parser.name == "item") {
                var title = ""
                var link = ""
                var description = ""
                var published = ""
                var source = "Google News"
                val depth = parser.depth
                var inner = parser.next()
                while (!(inner == XmlPullParser.END_TAG && parser.depth == depth)) {
                    if (inner == XmlPullParser.START_TAG) {
                        when (parser.name) {
                            "title" -> title = parser.nextText().trim()
                            "link" -> link = parser.nextText().trim()
                            "description" -> description = parser.nextText().replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
                            "pubDate" -> published = parser.nextText().trim()
                            "source" -> source = parser.nextText().trim()
                        }
                    }
                    inner = parser.next()
                }
                if (title.isNotBlank() && link.startsWith("http")) {
                    results += DiscoveryArticle(
                        id = UUID.nameUUIDFromBytes(link.toByteArray()).toString(),
                        title = title,
                        summary = description.take(240),
                        url = link,
                        source = source,
                        category = category,
                        published = published
                    )
                }
            }
            event = parser.next()
        }
        return results
    }
}
