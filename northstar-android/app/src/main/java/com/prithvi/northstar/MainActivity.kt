package com.prithvi.northstar

import android.Manifest
import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.prithvi.northstar.data.DiscoveryArticle
import com.prithvi.northstar.data.DiscoverySync
import com.prithvi.northstar.data.NorthstarStore
import com.prithvi.northstar.data.PersonalizationEngine
import com.prithvi.northstar.data.SecretVault
import com.prithvi.northstar.data.RoutineStore
import com.prithvi.northstar.data.RoutineTask
import com.prithvi.northstar.reminders.QuoteLibrary
import com.prithvi.northstar.reminders.RoutineScheduler
import com.prithvi.northstar.worker.DiscoveryWorker
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private val Ink = Color(0xFF0A0F19)
private val Panel = Color(0xFF141D2A)
private val Panel2 = Color(0xFF1B2736)
private val Gold = Color(0xFFD8B978)
private val Soft = Color(0xFFE9E5DC)
private val Muted = Color(0xFF9AA5B3)
private val Green = Color(0xFF8FC6A3)

class MainActivity : ComponentActivity() {
    private val notificationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) RoutineScheduler.scheduleAll(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scheduleDiscovery(this)
        RoutineScheduler.scheduleAll(this)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            val permissionPrefs = getSharedPreferences("northstar_permissions", MODE_PRIVATE)
            if (!permissionPrefs.getBoolean("notification_prompted", false)) {
                permissionPrefs.edit().putBoolean("notification_prompted", true).apply()
                notificationPermissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        setContent {
            val scheme = darkColorScheme(
                primary = Gold,
                onPrimary = Ink,
                secondary = Green,
                background = Ink,
                onBackground = Soft,
                surface = Panel,
                onSurface = Soft,
                surfaceVariant = Panel2,
                onSurfaceVariant = Muted
            )
            MaterialTheme(colorScheme = scheme) { NorthstarApp() }
        }
    }
}

private enum class Destination(val label: String) {
    TODAY("Today"), DISCOVER("Discover"), RESET("Reset"), CREATOR("Creator"), PROGRESS("Progress"), BUDDY("Buddy")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NorthstarApp() {
    val context = LocalContext.current
    val store = remember { NorthstarStore(context) }
    var destination by remember { mutableStateOf(Destination.TODAY) }
    var settingsOpen by remember { mutableStateOf(false) }
    var mood by remember { mutableStateOf(store.getString(NorthstarStore.KEY_MOOD, "Steady")) }
    var energy by remember { mutableIntStateOf(store.getInt(NorthstarStore.KEY_ENERGY, 2)) }
    var articles by remember { mutableStateOf(store.loadArticles()) }
    var refreshing by remember { mutableStateOf(false) }
    var refreshMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = Ink,
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text("STARBOY", color = Gold, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                        Text("A BETTER YOU, ONE DAY AT A TIME. · BY PRITHVI", color = Muted, fontSize = 8.sp, letterSpacing = 0.7.sp)
                    }
                },
                actions = {
                    IconButton(onClick = { destination = Destination.BUDDY }) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "Personal AI buddy", tint = Gold)
                    }
                    IconButton(onClick = { settingsOpen = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Soft)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Ink)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Panel, contentColor = Soft) {
                val items = listOf(
                    Triple(Destination.TODAY, Icons.Default.Home, "Today"),
                    Triple(Destination.DISCOVER, Icons.Default.Explore, "Discover"),
                    Triple(Destination.RESET, Icons.Default.Spa, "Reset"),
                    Triple(Destination.CREATOR, Icons.Default.VideoLibrary, "Creator"),
                    Triple(Destination.PROGRESS, Icons.Default.Insights, "Progress")
                )
                items.forEach { (target, icon, label) ->
                    NavigationBarItem(
                        selected = destination == target,
                        onClick = { destination = target },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Gold,
                            selectedTextColor = Gold,
                            indicatorColor = Panel2,
                            unselectedIconColor = Muted,
                            unselectedTextColor = Muted
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (destination) {
                Destination.TODAY -> TodayScreen(
                    mood = mood,
                    energy = energy,
                    onMood = { mood = it; store.putString(NorthstarStore.KEY_MOOD, it) },
                    onEnergy = { energy = it; store.putInt(NorthstarStore.KEY_ENERGY, it) },
                    onReset = { destination = Destination.RESET },
                    onCreator = { destination = Destination.CREATOR },
                    onBuddy = { destination = Destination.BUDDY },
                    onRoutine = { destination = Destination.PROGRESS },
                    lastSync = store.lastSync()
                )
                Destination.DISCOVER -> DiscoverScreen(
                    articles = articles,
                    mood = mood,
                    store = store,
                    refreshing = refreshing,
                    message = refreshMessage,
                    onRefresh = {
                        refreshing = true
                        refreshMessage = "Searching selected topics…"
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { DiscoverySync.refresh(context) }
                            articles = store.loadArticles()
                            refreshMessage = "${result.message}  ·  ${(result.bytes / 1024)} KB used"
                            refreshing = false
                        }
                    },
                    onOpen = { url -> openUrl(context, url) },
                    onFeedback = { category, helpful -> store.recordFeedback(category, helpful) }
                )
                Destination.RESET -> ResetScreen(mood = mood, onDone = { destination = Destination.TODAY })
                Destination.CREATOR -> CreatorScreen(onAskBuddy = { destination = Destination.BUDDY })
                Destination.PROGRESS -> ProgressScreen(store)
                Destination.BUDDY -> BuddyScreen(store)
            }
        }
    }

    if (settingsOpen) {
        SettingsDialog(store = store, onDismiss = { settingsOpen = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodayScreen(
    mood: String,
    energy: Int,
    onMood: (String) -> Unit,
    onEnergy: (Int) -> Unit,
    onReset: () -> Unit,
    onCreator: () -> Unit,
    onBuddy: () -> Unit,
    onRoutine: () -> Unit,
    lastSync: String
) {
    val context = LocalContext.current
    val routineStore = remember { RoutineStore(context) }
    val routineTasks = remember { routineStore.tasks().filter { it.enabled }.sortedWith(compareBy<RoutineTask> { it.hour }.thenBy { it.minute }) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("A new day. A new direction.", color = Soft, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
        Text("You don't have to solve your whole life today. Just choose the next honest step.", color = Muted, fontSize = 14.sp, lineHeight = 21.sp)

        Surface(color = Panel, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, Color(0xFF293648))) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionEyebrow("PERSONAL CHECK-IN")
                Text("How are you arriving today?", color = Soft, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("Low", "Steady", "Good", "Energised").forEach { option ->
                        val selected = mood == option
                        Surface(
                            modifier = Modifier.weight(1f).clickable { onMood(option) },
                            color = if (selected) Color(0xFF3A3427) else Panel2,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, if (selected) Gold else Color.Transparent)
                        ) {
                            Text(option, Modifier.padding(vertical = 10.dp), color = if (selected) Gold else Muted, fontSize = 10.sp, fontWeight = FontWeight.Medium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
                Text("Energy for today", color = Muted, fontSize = 12.sp)
                Slider(value = energy.toFloat(), onValueChange = { onEnergy(it.toInt().coerceIn(1, 4)) }, valueRange = 1f..4f, steps = 2, colors = SliderDefaults.colors(thumbColor = Gold, activeTrackColor = Gold))
                Text(listOf("A little is enough", "Manageable", "Plenty", "Full charge")[energy - 1], color = Gold, fontSize = 12.sp)
            }
        }

        Surface(color = Color(0xFF202B38), shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, Color(0xFF3C4A59))) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionEyebrow("TODAY'S NORTH STAR")
                    Spacer(Modifier.weight(1f))
                    Text("01 / 03", color = Gold, fontSize = 10.sp)
                }
                Text("Build a life you are excited to wake up to.", color = Soft, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, lineHeight = 27.sp)
                Text("One small action beats another hour of waiting for motivation.", color = Muted, fontSize = 13.sp, lineHeight = 20.sp)
                Button(onClick = onCreator, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink), shape = RoundedCornerShape(12.dp)) {
                    Text("Create something today", fontWeight = FontWeight.Bold)
                }
            }
        }

        Text("YOUR THREE DAILY MOVES", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        ActionRow("01", "Take care of yourself", "Walk, stretch, eat properly or rest without guilt.")
        ActionRow("02", "Feed your curiosity", "Read one useful story. Save one idea worth exploring.")
        ActionRow("03", "Build your future", "Spend 20 focused minutes on a skill or a video idea.")

        Surface(color = Panel, shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionEyebrow("YOUR DAILY ROUTINE")
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onRoutine, contentPadding = PaddingValues(0.dp)) { Text("MANAGE", color = Gold, fontSize = 10.sp) }
                }
                routineTasks.take(5).forEach { task ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(String.format(java.util.Locale.getDefault(), "%02d:%02d", task.hour, task.minute), color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(task.title, color = Soft, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Text("Your reminders appear as notifications with a Confirm complete action.", color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
            }
        }

        Surface(color = Panel, shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                SectionEyebrow("TODAY'S THOUGHT")
                Text("“${QuoteLibrary.today()}”", color = Soft, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
            }
        }

        Surface(color = Panel, shape = RoundedCornerShape(18.dp)) {
            Row(Modifier.fillMaxWidth().clickable(onClick = onReset).padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("↗", color = Gold, fontSize = 24.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("A difficult moment?", color = Soft, fontWeight = FontWeight.SemiBold)
                    Text("Open your reset space. No judgement, no pressure.", color = Muted, fontSize = 12.sp)
                }
                Text("OPEN", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        Surface(color = Panel, shape = RoundedCornerShape(18.dp)) {
            Row(Modifier.fillMaxWidth().clickable(onClick = onBuddy).padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, null, tint = Gold)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Talk it through", color = Soft, fontWeight = FontWeight.SemiBold)
                    Text("Think clearly, plan the next move, or explore an idea.", color = Muted, fontSize = 12.sp)
                }
                Text("BUDDY", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text("Last discovery refresh: $lastSync", color = Muted, fontSize = 10.sp)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DiscoverScreen(
    articles: List<DiscoveryArticle>,
    mood: String,
    store: NorthstarStore,
    refreshing: Boolean,
    message: String,
    onRefresh: () -> Unit,
    onOpen: (String) -> Unit,
    onFeedback: (String, Boolean) -> Unit
) {
    var feedbackVersion by remember { mutableIntStateOf(0) }
    val rankedArticles = remember(articles, mood, feedbackVersion) { PersonalizationEngine.rank(articles, store, mood) }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 8.dp)) {
        Text("The world is bigger than your feed.", color = Soft, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(5.dp))
        Text("A deliberately mixed collection of stories, ideas and discoveries.", color = Muted, fontSize = 13.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(13.dp))
        Button(onClick = onRefresh, enabled = !refreshing, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            if (refreshing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Ink)
            else Icon(Icons.Default.Refresh, null)
            Spacer(Modifier.width(8.dp))
            Text(if (refreshing) "Searching…" else "Search for new discoveries", fontWeight = FontWeight.Bold)
        }
        if (message.isNotBlank()) {
            Text(message, color = Green, fontSize = 11.sp, modifier = Modifier.padding(vertical = 8.dp))
        }
        Spacer(Modifier.height(4.dp))
        if (articles.isEmpty()) {
            EmptyState("Your discovery shelf is ready", "Tap Search for new discoveries. STARBOY will look for creator stories, technology, biographies and fresh ideas.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(rankedArticles, key = { it.id }) { article -> ArticleCard(article, onClick = { onOpen(article.url) }, onFeedback = { helpful -> onFeedback(article.category, helpful); feedbackVersion++ }) }
            }
        }
    }
}

@Composable
private fun ArticleCard(article: DiscoveryArticle, onClick: () -> Unit, onFeedback: (Boolean) -> Unit) {
    Surface(color = Panel, shape = RoundedCornerShape(17.dp), border = BorderStroke(1.dp, Color(0xFF263346)), modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(article.category, color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Text(article.title, color = Soft, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 21.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (article.summary.isNotBlank()) Text(article.summary, color = Muted, fontSize = 12.sp, lineHeight = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(listOf(article.source, article.published.take(16)).filter { it.isNotBlank() }.joinToString(" · "), color = Green, fontSize = 9.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("READ STORY  ↗", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onFeedback(true) }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) { Text("Useful", color = Green, fontSize = 10.sp) }
                TextButton(onClick = { onFeedback(false) }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) { Text("Show me less", color = Muted, fontSize = 10.sp) }
            }
        }
    }
}

@Composable
private fun ResetScreen(mood: String, onDone: () -> Unit) {
    var running by remember { mutableStateOf(false) }
    var seconds by remember { mutableIntStateOf(60) }
    var completed by remember { mutableStateOf(false) }
    var trigger by remember { mutableStateOf("I feel pulled toward the past") }
    LaunchedEffect(running) {
        if (running) {
            while (seconds > 0) {
                delay(1_000)
                seconds -= 1
            }
            running = false
            completed = true
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionEyebrow("THE RESET SPACE")
        Text("Pause. Come back to yourself.", color = Soft, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
        Text("This is not about forcing yourself to feel nothing. It is about creating a little space between a feeling and an action.", color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
        Surface(color = Panel, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("RIGHT NOW", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Text("What feels closest?", color = Soft, fontWeight = FontWeight.SemiBold)
                listOf("I feel pulled toward the past", "I feel lonely", "I feel anxious or restless", "I feel angry", "I feel hopeful but uncertain").forEach { option ->
                    Row(Modifier.fillMaxWidth().clickable { trigger = option }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = trigger == option, onClick = { trigger = option }, colors = RadioButtonDefaults.colors(selectedColor = Gold, unselectedColor = Muted))
                        Text(option, color = if (trigger == option) Soft else Muted, fontSize = 13.sp)
                    }
                }
            }
        }
        Surface(color = Panel2, shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("60-SECOND RESET", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
                Text(if (completed) "You made space." else seconds.toString(), color = Soft, fontSize = 48.sp, fontWeight = FontWeight.Light)
                Text(if (completed) "Notice what changed. You do not need to act on every feeling." else if (running) "Breathe slowly. Relax your jaw. Let your shoulders drop." else "Inhale gently. Exhale a little longer. Let the urge pass without obeying it.", color = Muted, fontSize = 13.sp, lineHeight = 20.sp)
                Button(onClick = {
                    if (completed) { seconds = 60; completed = false }
                    running = !running
                }, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink), shape = RoundedCornerShape(12.dp)) {
                    Text(if (completed) "Reset again" else if (running) "Pause" else "Start breathing reset", fontWeight = FontWeight.Bold)
                }
            }
        }
        Text("ONE QUESTION TO WRITE DOWN", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        Text("What am I hoping will happen if I follow this urge? What is one kinder thing I can do for myself for the next ten minutes?", color = Soft, fontSize = 15.sp, lineHeight = 23.sp)
        Text("Current check-in: $mood · Trigger: $trigger", color = Muted, fontSize = 11.sp)
        OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, Gold), colors = ButtonDefaults.outlinedButtonColors(contentColor = Gold)) { Text("Back to today") }
        Text("A tool, not a substitute for supportive people or professional care when you need it.", color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun CreatorScreen(onAskBuddy: () -> Unit) {
    val ideas = listOf(
        "The quiet work of becoming someone you respect",
        "What I learned from starting again with limited resources",
        "Why consuming motivation is easier than building a life",
        "A practical 30-day experiment to learn a valuable skill",
        "The difference between being alone and building solitude"
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionEyebrow("CREATOR LAB")
        Text("Turn what moves you into something you make.", color = Soft, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 30.sp)
        Text("Study the craft. Build your own voice. Publish consistently instead of waiting to feel ready.", color = Muted, fontSize = 13.sp, lineHeight = 20.sp)
        Surface(color = Panel, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("A STORY STRUCTURE TO PRACTISE", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                CreatorStep("01", "The honest hook", "Start with a specific tension, question or moment.")
                CreatorStep("02", "The human story", "Show what happened. Use concrete details, not generic advice.")
                CreatorStep("03", "The shift", "Explain what changed in your understanding.")
                CreatorStep("04", "The useful ending", "Give the viewer one practical thought or action to carry away.")
            }
        }
        Text("ORIGINAL VIDEO IDEA STARTERS", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
        ideas.forEachIndexed { index, idea ->
            Surface(color = Panel2, shape = RoundedCornerShape(13.dp)) {
                Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("0${index + 1}", color = Gold, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Spacer(Modifier.width(12.dp))
                    Text(idea, color = Soft, fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
        }
        Surface(color = Color(0xFF263143), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("YOUR FIRST VIDEO", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("Record a 60-second story: one thing you are learning, one mistake you made, and one thing someone else can use.", color = Soft, fontSize = 14.sp, lineHeight = 21.sp)
                Button(onClick = onAskBuddy, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) { Text("Build a script with Buddy") }
            }
        }
        Text("STARBOY uses creator work as inspiration for original formats; it does not reproduce another creator's scripts or voice.", color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun CreatorStep(number: String, title: String, description: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(number, color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = Soft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProgressScreen(store: NorthstarStore) {
    val context = LocalContext.current
    val routineStore = remember { RoutineStore(context) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var tasks by remember { mutableStateOf(routineStore.tasks()) }
    var refreshVersion by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                RoutineScheduler.scheduleAll(context)
                tasks = routineStore.tasks()
                refreshVersion++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val enabledTasks = tasks.filter { it.enabled }
    val completed = remember(tasks, refreshVersion) { enabledTasks.count { routineStore.isComplete(it.id) } }
    val notificationsEnabled = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
    val alarmManager = context.getSystemService(android.content.Context.ALARM_SERVICE) as AlarmManager
    val exactWakeupEnabled = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionEyebrow("YOUR LIFE, BUILT DAY BY DAY")
        Text("Keep the promises you make to yourself.", color = Soft, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Text("Start with the basics. Adjust the routine to fit your real life. A missed task is information, not failure.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)

        Surface(color = Panel, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$completed", color = Gold, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                    Text(" / ${enabledTasks.size} tasks completed", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp, start = 6.dp))
                    Spacer(Modifier.weight(1f))
                    Text("TODAY", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                LinearProgressIndicator(progress = { if (enabledTasks.isEmpty()) 0f else completed.toFloat() / enabledTasks.size }, modifier = Modifier.fillMaxWidth().height(7.dp), color = Gold, trackColor = Panel2)
            }
        }

        Surface(color = Panel, shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionEyebrow("DAILY ENCOURAGEMENT")
                Text("“${QuoteLibrary.today()}”", color = Soft, fontSize = 14.sp, lineHeight = 21.sp)
                Text("Quotes rotate daily. They are original reminders, not promises that success happens automatically.", color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("YOUR REMINDERS", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
                Text("Tap a time to change it. Switch off anything you don't need.", color = Muted, fontSize = 11.sp)
            }
            TextButton(onClick = {
                RoutineScheduler.scheduleAll(context)
                tasks = routineStore.tasks()
                refreshVersion++
            }) { Text("RESCHEDULE", color = Gold, fontSize = 10.sp) }
        }

        if (!exactWakeupEnabled) {
            Surface(color = Color(0xFF332A20), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Make the wake-up reminder more precise", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("Android requires special alarm access for precise wake-up reminders. Without it, the system may delay the reminder to save battery.", color = Soft, fontSize = 11.sp, lineHeight = 16.sp)
                    Button(onClick = {
                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                        context.startActivity(intent)
                    }, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) { Text("Enable precise wake-up") }
                }
            }
        }

        tasks.sortedWith(compareBy<RoutineTask> { it.hour }.thenBy { it.minute }).forEach { task ->
            val isDone = routineStore.isComplete(task.id)
            Surface(color = Panel, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, if (isDone) Color(0xFF385443) else Color(0xFF293648))) {
                Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(task.title, color = if (isDone) Green else Soft, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                when (task.kind) {
                                    "bible" -> "Today's reading: ${routineStore.currentBibleChapter()}"
                                    "quote" -> QuoteLibrary.today()
                                    else -> task.description
                                },
                                color = Muted, fontSize = 11.sp, lineHeight = 16.sp
                            )
                        }
                        Switch(
                            checked = task.enabled,
                            onCheckedChange = { enabled ->
                                val updated = task.copy(enabled = enabled)
                                tasks = tasks.map { if (it.id == task.id) updated else it }
                                routineStore.saveTasks(tasks)
                                RoutineScheduler.scheduleTask(context, updated)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Gold, checkedTrackColor = Color(0xFF66552F))
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = {
                                TimePickerDialog(context, { _, hour, minute ->
                                    val updated = task.copy(hour = hour, minute = minute)
                                    tasks = tasks.map { if (it.id == task.id) updated else it }
                                    routineStore.saveTasks(tasks)
                                    RoutineScheduler.scheduleTask(context, updated)
                                }, task.hour, task.minute, false).show()
                            },
                            enabled = task.enabled,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(String.format(java.util.Locale.getDefault(), "◷ %02d:%02d · CHANGE", task.hour, task.minute), color = if (task.enabled) Gold else Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = {
                                routineStore.setComplete(task.id, complete = !isDone)
                                refreshVersion++
                            },
                            enabled = task.enabled,
                            colors = ButtonDefaults.buttonColors(containerColor = if (isDone) Panel2 else Gold, contentColor = if (isDone) Green else Ink),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) { Text(if (isDone) "COMPLETED ✓" else "CONFIRM COMPLETE", fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }

        if (!notificationsEnabled) {
            Surface(color = Color(0xFF332A20), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Notifications are turned off", color = Gold, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("Allow STARBOY notifications in Android Settings so your scheduled reminders can appear.", color = Soft, fontSize = 11.sp, lineHeight = 16.sp)
                    Button(onClick = {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        context.startActivity(intent)
                    }, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) { Text("Open notification settings") }
                }
            }
        }

        Text("The suggested routine is a starting point, not a medical or spiritual prescription. You control the times, tasks and pace. Android battery settings may delay reminders.", color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun BuddyScreen(store: NorthstarStore) {
    val context = LocalContext.current
    var prompt by remember { mutableStateOf("") }
    var reply by remember { mutableStateOf("I'm here to help you think clearly and take the next useful step. What's on your mind?") }
    var sending by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf("companion") }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionEyebrow("YOUR PRIVATE THINKING PARTNER")
        Text("Talk it through.", color = Soft, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
        Text("A practical assistant for reflection, learning and making things happen.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("companion" to "Life", "creator" to "YouTube", "career" to "Career", "research" to "Research").forEach { (value, label) ->
                FilterChip(selected = mode == value, onClick = { mode = value }, label = { Text(label) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Panel2, selectedLabelColor = Gold))
            }
        }
        Surface(color = Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.weight(1f, fill = true)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("STARBOY · BY PRITHVI", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
                Text(reply, color = Soft, fontSize = 14.sp, lineHeight = 22.sp)
                if (!sending && store.getString(NorthstarStore.KEY_API_URL).isBlank()) {
                    Text("Offline mode is active. Set your private Gemini connection in Settings to enable personalised AI replies. Your Google API key stays on the server, never inside this APK.", color = Muted, fontSize = 11.sp, lineHeight = 17.sp)
                }
            }
        }
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("What's on your mind?", color = Muted) },
            minLines = 2,
            maxLines = 4,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Gold, unfocusedBorderColor = Color(0xFF3B4858), focusedTextColor = Soft, unfocusedTextColor = Soft)
        )
        Button(
            onClick = {
                val question = prompt.trim()
                if (question.isNotEmpty() && !sending) {
                    sending = true
                    prompt = ""
                    scope.launch {
                        reply = withContext(Dispatchers.IO) { callCompanion(context, store, question, mode) }
                        sending = false
                    }
                }
            },
            enabled = prompt.isNotBlank() && !sending,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (sending) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Ink)
            else Text("Send to Buddy", fontWeight = FontWeight.Bold)
        }
    }
}

private fun callCompanion(context: android.content.Context, store: NorthstarStore, message: String, mode: String): String {
    val endpoint = store.getString(NorthstarStore.KEY_API_URL).trim()
    val token = SecretVault(context).read()
    return if (endpoint.isBlank()) {
        when (mode) {
            "research" -> "I can help shape a good research question and identify what evidence to look for. Add your private Gemini connection in Settings to enable AI research with live Google News results."
            "creator" -> "Let's make this concrete. Start with one real experience, one specific lesson, and one viewer who could benefit. Write a 60-second version before trying to make a perfect video.\n\nTo generate a tailored script with Gemini, configure your private server endpoint in Settings."
            "career" -> "Choose one skill that can create value in the next 30 days. Break it into a small daily practice, one proof-of-work project, and one way to show the result.\n\nConnect your private Gemini endpoint in Settings for a personalised plan."
            else -> "Let's slow this down. Name what is in your control today, what is not, and one action that protects your time and self-respect. You don't need to solve everything in one sitting.\n\nConnect your private Gemini endpoint in Settings for an AI conversation."
        }
    } else if (!endpoint.startsWith("https://", ignoreCase = true)) {
        "Your private assistant endpoint must use HTTPS. Update it in Settings."
    } else {
        runCatching {
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 30_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                if (token.isNotBlank()) setRequestProperty("x-app-token", token)
            }
            val json = JSONObject().put("message", message).put("mode", mode)
                .put("mood", store.getString(NorthstarStore.KEY_MOOD, "Steady"))
                .put("energy", store.getInt(NorthstarStore.KEY_ENERGY, 2))
            connection.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val response = stream.bufferedReader().use { it.readText() }
            connection.disconnect()
            if (response.isBlank()) "The assistant returned an empty reply. Try again." else JSONObject(response).optString("reply", "I couldn't read that response. Please try again.")
        }.getOrElse { "I couldn't reach your Gemini service. Check the server URL and private access token, or try again when you're online." }
    }
}

@Composable
private fun SettingsDialog(store: NorthstarStore, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val vault = remember { SecretVault(context) }
    var allowCellular by remember { mutableStateOf(store.getBoolean(NorthstarStore.KEY_ALLOW_CELLULAR, true)) }
    var limit by remember { mutableIntStateOf(store.getInt(NorthstarStore.KEY_DAILY_LIMIT_MB, 5)) }
    var hours by remember { mutableIntStateOf(store.getInt(NorthstarStore.KEY_REFRESH_HOURS, 12)) }
    var endpoint by remember { mutableStateOf(store.getString(NorthstarStore.KEY_API_URL)) }
    var token by remember { mutableStateOf(vault.read()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text("STARBOY settings", color = Soft, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("DISCOVERY & MOBILE DATA", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Allow mobile data", color = Soft, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text("When enabled, background search may use cellular data.", color = Muted, fontSize = 10.sp)
                    }
                    Switch(checked = allowCellular, onCheckedChange = { allowCellular = it }, colors = SwitchDefaults.colors(checkedThumbColor = Gold, checkedTrackColor = Color(0xFF66552F)))
                }
                Text("Discovery response budget: $limit MB/day", color = Soft, fontSize = 12.sp)
                Text("Approximate feed-body bytes only; network overhead and external browsing are not included.", color = Muted, fontSize = 9.sp, lineHeight = 13.sp)
                Slider(value = limit.toFloat(), onValueChange = { limit = it.toInt().coerceIn(1, 25) }, valueRange = 1f..25f, steps = 23, colors = SliderDefaults.colors(thumbColor = Gold, activeTrackColor = Gold))
                Text("Refresh interval: every $hours hours", color = Soft, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(6, 12, 24).forEach { h ->
                        FilterChip(selected = hours == h, onClick = { hours = h }, label = { Text("${h}h") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Panel2, selectedLabelColor = Gold))
                    }
                }
                Text("Last sync: ${store.lastSync()} · Used today: ${store.bytesUsedToday() / 1024} KB", color = Muted, fontSize = 10.sp)
                Divider(color = Color(0xFF334154))
                Text("PRIVATE GEMINI CONNECTION", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                Text("Enter your STARBOY HTTPS API endpoint and private device token. Your Gemini API key stays on the server. Research uses Google News RSS; Gemini free-tier quotas apply.", color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
                OutlinedTextField(value = endpoint, onValueChange = { endpoint = it }, label = { Text("Server URL (https://…/api/companion)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Gold, unfocusedBorderColor = Color(0xFF3B4858), focusedTextColor = Soft, unfocusedTextColor = Soft))
                OutlinedTextField(value = token, onValueChange = { token = it }, label = { Text("Private device access token") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Gold, unfocusedBorderColor = Color(0xFF3B4858), focusedTextColor = Soft, unfocusedTextColor = Soft))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                store.putBoolean(NorthstarStore.KEY_ALLOW_CELLULAR, allowCellular)
                store.putInt(NorthstarStore.KEY_DAILY_LIMIT_MB, limit)
                store.putInt(NorthstarStore.KEY_REFRESH_HOURS, hours)
                store.putString(NorthstarStore.KEY_API_URL, endpoint.trim())
                vault.save(token.trim())
                scheduleDiscovery(context)
                onDismiss()
            }) { Text("Save settings", color = Gold, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Muted) } }
    )
}

private fun scheduleDiscovery(context: android.content.Context) {
    val store = NorthstarStore(context)
    val allowCellular = store.getBoolean(NorthstarStore.KEY_ALLOW_CELLULAR, true)
    val interval = store.getInt(NorthstarStore.KEY_REFRESH_HOURS, 12).coerceAtLeast(1)
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(if (allowCellular) NetworkType.CONNECTED else NetworkType.UNMETERED)
        .build()
    val request = PeriodicWorkRequestBuilder<DiscoveryWorker>(interval.toLong(), TimeUnit.HOURS)
        .setConstraints(constraints)
        .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "northstar-discovery-refresh",
        ExistingPeriodicWorkPolicy.UPDATE,
        request
    )
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        val uri = Uri.parse(url)
        if (uri.scheme == "https" && !uri.host.isNullOrBlank()) {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    }
}

@Composable
private fun ActionRow(number: String, title: String, description: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(color = Panel2, shape = CircleShape, modifier = Modifier.size(28.dp)) {
            Box(contentAlignment = Alignment.Center) { Text(number, color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = Soft, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun SectionEyebrow(text: String) {
    Text(text, color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
}

@Composable
private fun EmptyState(title: String, text: String) {
    Surface(color = Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.MenuBook, null, tint = Gold, modifier = Modifier.size(28.dp))
            Text(title, color = Soft, fontWeight = FontWeight.SemiBold)
            Text(text, color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}
