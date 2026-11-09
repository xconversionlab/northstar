package com.prithvi.northstar.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.prithvi.northstar.data.RoutineTask
import com.prithvi.northstar.data.RoutineStore
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

object QuoteLibrary {
    private val quotes = listOf(
        "You do not need a perfect beginning. You need an honest next step.",
        "Build a life that gives your attention somewhere meaningful to go.",
        "A quiet, repeated effort can change the direction of an entire year.",
        "Do one thing today that tomorrow-you will be glad you started.",
        "Discipline is keeping a small promise to yourself, especially on ordinary days.",
        "Your past can explain you without being allowed to define your future.",
        "Let your actions become louder than the doubts you woke up with.",
        "A useful day is not always a spectacular day.",
        "Protect your attention. It is one of the most valuable things you own.",
        "You are allowed to begin again without punishing yourself for yesterday.",
        "Learn something. Make something. Help someone. Repeat.",
        "Do not measure your worth by somebody else's attention.",
        "Confidence grows when your promises and actions begin to agree.",
        "A healthy life is built through small choices made more often than not.",
        "Keep the goal ambitious and the next step manageable.",
        "The work you do when nobody is watching still counts.",
        "You can feel uncertain and still take a useful step.",
        "Spend less time waiting to feel ready and more time practising.",
        "One difficult morning does not get to decide the shape of your whole day.",
        "Make room for faith, learning, movement, honest work and rest.",
        "A new skill is a door you build with repeated practice.",
        "Your attention is a garden. Choose what you keep watering.",
        "Progress is not a straight line. Returning to the path is progress too.",
        "Build a reputation with yourself for following through.",
        "You cannot control every outcome, but you can prepare for the next action.",
        "Choose the kind action, the useful action, and the courageous action.",
        "A meaningful life is assembled one ordinary day at a time.",
        "Rest is part of the plan, not a reward you must earn.",
        "Do not confuse being busy with building something that matters.",
        "Keep becoming someone you can respect when the day is over."
    )

    fun today(): String {
        val index = Math.floorMod(LocalDate.now().toEpochDay(), quotes.size.toLong()).toInt()
        return quotes[index]
    }
}

object RoutineScheduler {
    fun scheduleAll(context: Context) {
        val store = RoutineStore(context)
        store.tasks().forEach { task ->
            cancelTask(context, task.id)
            if (task.enabled) scheduleTask(context, task)
        }
    }

    fun scheduleTask(context: Context, task: RoutineTask) {
        cancelTask(context, task.id)
        if (!task.enabled) return
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(task.hour, task.minute)
        if (!next.isAfter(now)) next = next.plusDays(1)
        scheduleAt(context, task, next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), false)
    }

    fun scheduleSnooze(context: Context, task: RoutineTask, minutes: Int = 10) {
        val fireAt = System.currentTimeMillis() + minutes.coerceIn(5, 60) * 60_000L
        scheduleAt(context, task, fireAt, true)
    }

    private fun scheduleAt(context: Context, task: RoutineTask, triggerAt: Long, snoozed: Boolean) {
        val intent = Intent(context, RoutineReminderReceiver::class.java).apply {
            action = if (snoozed) ACTION_SNOOZED_REMINDER else ACTION_REMINDER
            data = Uri.parse("northstar://reminder/${task.id}/${if (snoozed) "snooze" else "daily"}")
            putExtra(EXTRA_TASK_ID, task.id)
            putExtra(EXTRA_SNOOZED, snoozed)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode(task.id, snoozed),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val canUseExactAlarm = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (task.id == "wake_up" && canUseExactAlarm) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    private fun cancelTask(context: Context, taskId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        listOf(false, true).forEach { snoozed ->
            val intent = Intent(context, RoutineReminderReceiver::class.java).apply {
                action = if (snoozed) ACTION_SNOOZED_REMINDER else ACTION_REMINDER
                data = Uri.parse("northstar://reminder/$taskId/${if (snoozed) "snooze" else "daily"}")
            }
            val pending = PendingIntent.getBroadcast(
                context,
                requestCode(taskId, snoozed),
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pending != null) alarmManager.cancel(pending)
        }
    }

    private fun requestCode(taskId: String, snoozed: Boolean) = taskId.hashCode() xor if (snoozed) 0x5A5A5A5A else 0x13572468
}

class RoutineReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val taskId = intent?.getStringExtra(EXTRA_TASK_ID) ?: return
        val isSnoozed = intent.getBooleanExtra(EXTRA_SNOOZED, false)
        val task = RoutineStore(context).tasks().firstOrNull { it.id == taskId && it.enabled } ?: return
        if (intent.action == ACTION_SNOOZE) {
            RoutineScheduler.scheduleSnooze(context, task)
            return
        }
        if (!isSnoozed) RoutineScheduler.scheduleTask(context, task)
        RoutineNotifications.show(context, task)
    }
}

class CompleteTaskReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val taskId = intent?.getStringExtra(EXTRA_TASK_ID) ?: return
        val task = RoutineStore(context).tasks().firstOrNull { it.id == taskId } ?: return
        val date = runCatching { LocalDate.parse(intent.getStringExtra(EXTRA_DATE)) }.getOrDefault(LocalDate.now())
        RoutineStore(context).setComplete(taskId, date, true)
        val manager = NotificationManagerCompat.from(context)
        val notification = NotificationCompat.Builder(context, RoutineNotifications.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.checkbox_on_background)
            .setContentTitle("Completed: ${task.title}")
            .setContentText("Marked complete for today. Keep your next step small and steady.")
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { manager.notify(task.id.hashCode(), notification) }
    }
}

class RoutineBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> RoutineScheduler.scheduleAll(context)
        }
    }
}

object RoutineNotifications {
    const val CHANNEL_ID = "northstar_daily_routine"

    fun show(context: Context, task: RoutineTask) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        createChannel(context)
        val store = RoutineStore(context)
        val body = when (task.kind) {
            "bible" -> "Today's reading: ${store.currentBibleChapter()}. Read slowly and note one thought that stays with you."
            "quote" -> QuoteLibrary.today()
            else -> task.description
        }
        val completeIntent = Intent(context, CompleteTaskReceiver::class.java).apply {
            action = ACTION_COMPLETE
            data = Uri.parse("northstar://complete/${task.id}/${LocalDate.now()}")
            putExtra(EXTRA_TASK_ID, task.id)
            putExtra(EXTRA_DATE, LocalDate.now().toString())
        }
        val completePending = PendingIntent.getBroadcast(
            context,
            task.id.hashCode() xor 0x24681357,
            completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snoozeIntent = Intent(context, RoutineReminderReceiver::class.java).apply {
            action = ACTION_SNOOZE
            data = Uri.parse("northstar://snooze/${task.id}/${System.currentTimeMillis()}")
            putExtra(EXTRA_TASK_ID, task.id)
        }
        val snoozePending = PendingIntent.getBroadcast(
            context,
            task.id.hashCode() xor 0x76543210,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(task.title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(android.R.drawable.checkbox_on_background, "Confirm complete", completePending)
            .addAction(android.R.drawable.ic_lock_idle_alarm, "Remind in 10 min", snoozePending)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(task.id.hashCode(), notification) }
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Daily routine reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Your chosen routine, reading and daily encouragement reminders"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
    }
}

const val ACTION_REMINDER = "com.prithvi.northstar.action.REMINDER"
const val ACTION_SNOOZED_REMINDER = "com.prithvi.northstar.action.SNOOZED_REMINDER"
const val ACTION_SNOOZE = "com.prithvi.northstar.action.SNOOZE"
const val ACTION_COMPLETE = "com.prithvi.northstar.action.COMPLETE"
const val EXTRA_TASK_ID = "task_id"
const val EXTRA_SNOOZED = "is_snoozed"
const val EXTRA_DATE = "completion_date"
