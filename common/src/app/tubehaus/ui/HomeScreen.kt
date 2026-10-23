package app.tubehaus.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.tubehaus.engine.Item
import app.tubehaus.rec.Learn
import kotlinx.coroutines.launch

/** Remembered across visits to the tab so going back to Home doesn't reshuffle it. */
private object HomeCache {
    val items = mutableStateListOf<Item>()
    var at = 0L
}

@Composable
fun HomeScreen(app: AppState) {
    var askTopics by remember { mutableStateOf(app.rec.needsTopics()) }
    if (askTopics) {
        TopicPicker(app) { askTopics = false }
        return
    }
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    suspend fun load(more: Boolean) {
        if (loading) return
        loading = true; error = null
        try {
            val skip = if (more) HomeCache.items.map { it.id }.toSet() else emptySet()
            val got = app.rec.home(40, skip)
            if (!more) HomeCache.items.clear()
            HomeCache.items.addAll(got.filter { g -> HomeCache.items.none { it.id == g.id } })
            HomeCache.at = System.currentTimeMillis()
        } catch (e: Exception) {
            error = e.message ?: tr("Something went wrong")
        } finally { loading = false }
    }
    LaunchedEffect(Unit) {
        if (HomeCache.items.isEmpty() || System.currentTimeMillis() - HomeCache.at > 30 * 60_000) load(false)
    }
    when {
        HomeCache.items.isEmpty() && loading -> Busy()
        HomeCache.items.isEmpty() && error != null -> Problem(error!!) { scope.launch { load(false) } }
        HomeCache.items.isEmpty() -> Empty(tr("Nothing to show yet. Search for something you like"))
        else -> ItemGrid(
            HomeCache.items, app, loading = loading,
            onEnd = { load(true) },
            header = {
                Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp), contentAlignment = Alignment.CenterEnd) {
                    TextButton(onClick = { app.rec.clearCache(); scope.launch { load(false) } }) { Text(tr("Refresh")) }
                }
            },
        )
    }
}

/** First start: a few topics give the recommender something to begin with. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TopicPicker(app: AppState, done: () -> Unit) {
    val picked = remember { mutableStateListOf<String>() }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(tr("What do you like to watch?"), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(
                tr("Pick a few topics to start. Your recommendations are worked out on this device, from what you watch - nothing leaves it."),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (t in app.rec.topics) {
                    val on = t in picked
                    FilterChip(on, onClick = { if (on) picked.remove(t) else picked.add(t) }, label = { Text(tr(t)) })
                }
            }
            Button(
                onClick = { Learn.pickTopics(app.store, picked.toList()); done() },
                enabled = picked.isNotEmpty(), modifier = Modifier.padding(top = 24.dp),
            ) { Text(tr("Continue")) }
            OutlinedButton(onClick = { app.go(Screen.Settings) }, modifier = Modifier.padding(top = 8.dp), border = BorderStroke(0.dp, MaterialTheme.colorScheme.background)) {
                Text(tr("Import subscriptions from another app"))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LanguagePicker(app: AppState) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 480.dp).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Logo(Modifier.padding(bottom = 24.dp))
            Text("Language", style = MaterialTheme.typography.titleLarge)
            FlowRow(
                Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for ((code, name) in LANGS) {
                    OutlinedButton(onClick = { app.store.settings.update { it.copy(lang = code) } }) { Text(name) }
                }
            }
        }
    }
}
