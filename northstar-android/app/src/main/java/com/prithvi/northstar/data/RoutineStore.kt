package com.prithvi.northstar.data

import android.content.Context
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

data class RoutineTask(
    val id: String,
    val title: String,
    val description: String,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true,
    val kind: String = "habit"
)

object BibleReadingPlan {
    private val books = listOf(
        "John" to 21,
        "Acts" to 28,
        "Romans" to 16,
        "Proverbs" to 31,
        "Psalms" to 150
    )

    fun chapterFor(index: Int): String {
        var remaining = index.coerceAtLeast(0)
        for ((book, chapters) in books) {
            if (remaining < chapters) return "$book ${remaining + 1}"
            remaining -= chapters
        }
        return "Psalms ${(remaining % 150) + 1}"
    }
}

class RoutineStore(context: Context) {
    private val prefs = context.getSharedPreferences("northstar_routines", Context.MODE_PRIVATE)

    fun tasks(): List<RoutineTask> {
        val raw = prefs.getString(KEY_TASKS, null)
        if (raw.isNullOrBlank()) return defaults().also(::saveTasks)
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                RoutineTask(
                    id = item.optString("id"),
                    title = item.optString("title"),
                    description = item.optString("description"),
                    hour = item.optInt("hour", 8).coerceIn(0, 23),
                    minute = item.optInt("minute", 0).coerceIn(0, 59),
                    enabled = item.optBoolean("enabled", true),
                    kind = item.optString("kind", "habit")
                )
            }.filter { it.id.isNotBlank() && it.title.isNotBlank() }
        }.getOrElse { defaults().also(::saveTasks) }
    }

    fun saveTasks(tasks: List<RoutineTask>) {
        val array = JSONArray()
        tasks.forEach { task ->
            array.put(JSONObject().apply {
                put("id", task.id)
                put("title", task.title)
                put("description", task.description)
                put("hour", task.hour)
                put("minute", task.minute)
                put("enabled", task.enabled)
                put("kind", task.kind)
            })
        }
        prefs.edit().putString(KEY_TASKS, array.toString()).commit()
    }

    fun isComplete(taskId: String, date: LocalDate = LocalDate.now()): Boolean =
        prefs.getBoolean(doneKey(taskId, date), false)

    fun setComplete(taskId: String, date: LocalDate = LocalDate.now(), complete: Boolean) {
        val key = doneKey(taskId, date)
        val wasComplete = prefs.getBoolean(key, false)
        if (wasComplete == complete) return
        val editor = prefs.edit().putBoolean(key, complete)
        if (taskId == "bible_reading") {
            val current = prefs.getInt(KEY_BIBLE_INDEX, 0)
            editor.putInt(KEY_BIBLE_INDEX, (current + if (complete) 1 else -1).coerceAtLeast(0))
        }
        editor.commit()
    }

    fun currentBibleChapter(): String = BibleReadingPlan.chapterFor(prefs.getInt(KEY_BIBLE_INDEX, 0))

    fun completedCount(date: LocalDate = LocalDate.now()): Int = tasks().count { isComplete(it.id, date) }

    private fun doneKey(taskId: String, date: LocalDate) = "done_${date}_$taskId"

    private fun defaults() = listOf(
        RoutineTask("wake_up", "Wake up and begin gently", "Get out of bed, open the curtains and make your bed.", 6, 30, true, "habit"),
        RoutineTask("morning_soup", "Have your morning soup", "Prepare and enjoy your morning soup without rushing.", 7, 0, true, "habit"),
        RoutineTask("bath", "Take a bath and get ready", "Take a bath, brush your teeth and get dressed for the day.", 7, 30, true, "habit"),
        RoutineTask("bible_reading", "Read today's Bible chapter", "Read one chapter slowly and write down one thought.", 8, 0, true, "bible"),
        RoutineTask("daily_quote", "Your daily encouragement", "Pause for a moment and carry one useful thought into the day.", 8, 15, true, "quote"),
        RoutineTask("movement", "Move your body", "Take a comfortable walk, stretch or do a short workout.", 18, 0, true, "habit"),
        RoutineTask("financial_step", "Take one financial step", "Review one expense, send a proposal, apply for a role or improve a portfolio asset.", 19, 0, true, "habit"),
        RoutineTask("career_focus", "Build your future for 25 minutes", "Practise a useful skill or make one small step on your YouTube project.", 20, 0, true, "habit"),
        RoutineTask("reflection", "Review your day", "Write one win, one lesson and tomorrow's first step.", 21, 30, true, "habit"),
        RoutineTask("wind_down", "Prepare for restful sleep", "Dim the lights, put your phone aside and begin winding down.", 22, 15, true, "habit")
    )

    companion object {
        private const val KEY_TASKS = "routine_tasks"
        private const val KEY_BIBLE_INDEX = "bible_reading_index"
    }
}
