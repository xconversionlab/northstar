package com.prithvi.northstar.data

/** A small, transparent ranking model: feedback + mood-aware weighting + category diversity. */
object PersonalizationEngine {
    fun rank(items: List<DiscoveryArticle>, store: NorthstarStore, mood: String): List<DiscoveryArticle> {
        if (items.isEmpty()) return items
        val byCategory = items.groupBy { it.category }.mapValues { (_, list) ->
            list.toMutableList() // Preserve source ordering (RSS normally arrives newest-first).
        }.toMutableMap()
        val output = mutableListOf<DiscoveryArticle>()
        val categoryCounts = mutableMapOf<String, Int>()

        while (byCategory.values.any { it.isNotEmpty() }) {
            val nextCategory = byCategory.entries
                .filter { it.value.isNotEmpty() }
                .maxByOrNull { (category, list) ->
                    val feedback = store.categoryFeedback(category).toDouble()
                    val moodWeight = when (mood) {
                        "Low" -> when (category) {
                            "PEOPLE & CAREERS" -> 0.65
                            "IDEAS & CULTURE" -> 0.55
                            "TECH & SCIENCE" -> 0.15
                            else -> 0.10
                        }
                        "Energised" -> when (category) {
                            "CREATOR STORIES" -> 0.75
                            "TECH & SCIENCE" -> 0.45
                            "PEOPLE & CAREERS" -> 0.30
                            else -> 0.10
                        }
                        "Good" -> if (category == "CREATOR STORIES") 0.25 else 0.15
                        else -> 0.20
                    }
                    val repetitionPenalty = (categoryCounts[category] ?: 0) * 0.8
                    val explorationBonus = if ((categoryCounts[category] ?: 0) == 0) 0.35 else 0.0
                    feedback + moodWeight + explorationBonus - repetitionPenalty
                }?.key ?: break
            val list = byCategory[nextCategory] ?: break
            output += list.removeAt(0)
            categoryCounts[nextCategory] = (categoryCounts[nextCategory] ?: 0) + 1
        }
        return output
    }
}
