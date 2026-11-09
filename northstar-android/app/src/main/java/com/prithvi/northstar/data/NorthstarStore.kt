package com.prithvi.northstar.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class NorthstarStore(context: Context) {
    private val prefs = context.getSharedPreferences("northstar_private", Context.MODE_PRIVATE)

    fun getString(key: String, fallback: String = ""): String = prefs.getString(key, fallback) ?: fallback
    fun putString(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    fun getBoolean(key: String, fallback: Boolean): Boolean = prefs.getBoolean(key, fallback)
    fun putBoolean(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }
    fun getInt(key: String, fallback: Int): Int = prefs.getInt(key, fallback)
    fun putInt(key: String, value: Int) { prefs.edit().putInt(key, value).apply() }

    fun saveArticles(articles: List<DiscoveryArticle>) {
        val array = JSONArray()
        articles.forEach { article ->
            array.put(JSONObject().apply {
                put("id", article.id)
                put("title", article.title)
                put("summary", article.summary)
                put("url", article.url)
                put("source", article.source)
                put("category", article.category)
                put("published", article.published)
            })
        }
        prefs.edit().putString(KEY_ARTICLES, array.toString()).apply()
    }

    fun loadArticles(): List<DiscoveryArticle> {
        val raw = prefs.getString(KEY_ARTICLES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                DiscoveryArticle(
                    id = item.optString("id"),
                    title = item.optString("title"),
                    summary = item.optString("summary"),
                    url = item.optString("url"),
                    source = item.optString("source"),
                    category = item.optString("category"),
                    published = item.optString("published")
                )
            }
        }.getOrDefault(emptyList())
    }

    fun markSync(bytes: Int) {
        val today = java.time.LocalDate.now().toString()
        val savedDate = getString(KEY_USAGE_DATE)
        val current = if (savedDate == today) getInt(KEY_USAGE_BYTES, 0) else 0
        prefs.edit().putString(KEY_USAGE_DATE, today).putInt(KEY_USAGE_BYTES, current + bytes).apply()
        prefs.edit().putString(KEY_LAST_SYNC, java.time.Instant.now().toString()).apply()
    }

    fun bytesUsedToday(): Int {
        val today = java.time.LocalDate.now().toString()
        return if (getString(KEY_USAGE_DATE) == today) getInt(KEY_USAGE_BYTES, 0) else 0
    }

    fun lastSync(): String = getString(KEY_LAST_SYNC, "Not refreshed yet")

    fun categoryFeedback(category: String): Int = prefs.getInt("feedback_${category.hashCode()}", 0)

    fun recordFeedback(category: String, helpful: Boolean) {
        val key = "feedback_${category.hashCode()}"
        val updated = (prefs.getInt(key, 0) + if (helpful) 1 else -1).coerceIn(-4, 6)
        prefs.edit().putInt(key, updated).apply()
    }


    companion object {
        const val KEY_ALLOW_CELLULAR = "allow_cellular"
        const val KEY_DAILY_LIMIT_MB = "daily_limit_mb"
        const val KEY_REFRESH_HOURS = "refresh_hours"
        const val KEY_MOOD = "mood"
        const val KEY_ENERGY = "energy"
        const val KEY_GOAL = "goal"
        const val KEY_HABITS = "habits"
        const val KEY_API_URL = "api_url"
        const val KEY_APP_TOKEN = "app_token"
        private const val KEY_ARTICLES = "articles"
        private const val KEY_USAGE_DATE = "usage_date"
        private const val KEY_USAGE_BYTES = "usage_bytes"
        private const val KEY_LAST_SYNC = "last_sync"
    }
}
