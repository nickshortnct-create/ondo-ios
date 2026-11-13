package com.nickshortnct.ondo.ondo

import ondo.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import ondo.composeapp.generated.resources.action_undo_upgrade
import ondo.composeapp.generated.resources.title_undo_upgrade
import ondo.composeapp.generated.resources.action_cancel
import ondo.composeapp.generated.resources.notice_undo_upgrade
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nickshortnct.ondo.util.Logger
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.koin.compose.koinInject

// Results of decknas' daily lossless upgrader (~/tools/lossless-upgrader),
// served by the same /ytfallback API the Squid and YouTube integrations use.
// Upgrades happen on decknas on their own; this screen shows what was swapped
// or turned down and why, and lets any upgrade be undone within 30 days.
@Composable
fun UpgradesPanel() {
    val api = koinInject<OndoDownloads>().api
    var data by remember { mutableStateOf<JsonObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var showRejected by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var busy by remember { mutableStateOf<Set<String>>(emptySet()) }
    var undo by remember { mutableStateOf<JsonElement?>(null) }

    fun act(id: String, label: String, block: suspend () -> Unit) {
        if (id in busy) return
        busy = busy + id
        scope.launch {
        try { block(); message = label; refresh++ }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { message = e.message }
        finally { busy = busy - id }
        }
    }

    LaunchedEffect(refresh, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        while (isActive) {
            try { data = api.call("Squid", "/upgrades").obj(); error = null }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { Logger.e("OndoUpgrades", "upgrades fetch failed", e); error = e.message }
            delay(if (data?.get("running").let { it != null && it !is JsonNull }) 10000 else 60000)
        }
    }

    }

    undo?.let { row ->
        AlertDialog(onDismissRequest = { undo = null }, title = { Text(stringResource(Res.string.title_undo_upgrade)) },
            text = { Text(stringResource(Res.string.notice_undo_upgrade, row.str("title"))) },
            confirmButton = { TextButton(onClick = { undo = null; act(row.str("id"), "Undo requested") { api.call("Squid", "/upgrades/${row.str("id")}/revert", "POST") } }) { Text(stringResource(Res.string.action_undo_upgrade)) } },
            dismissButton = { TextButton(onClick = { undo = null }) { Text(stringResource(Res.string.action_cancel)) } })
    }

    val d = data
    val running = d?.get("running")?.takeUnless { it is JsonNull }
    val upgrades = d?.get("upgrades")?.arr().orEmpty()
    val rejections = d?.get("rejections")?.arr().orEmpty()

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Every day decknas looks for lossless (FLAC) copies of your lossy songs. A copy only replaces " +
                "the old file if its audio fingerprint matches, it's truly lossless, and your playlists and likes stay attached.")
        }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        message?.let { item { Text(it) } }
        if (d == null && error == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (d != null) item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                d["totals"]?.takeUnless { it is JsonNull }?.let { t ->
                    Text("${t.num("lossless").toInt()} lossless · ${t.num("lossy").toInt()} lossy · ${t.num("upgradedAllTime").toInt()} upgraded so far",
                        style = MaterialTheme.typography.titleSmall)
                }
                if (running != null) {
                    Text("Checking now: ${running.str("phase")}…")
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else d["lastRun"]?.takeUnless { it is JsonNull }?.let { r ->
                    val when_ = r.str("finishedAt").replace('T', ' ').removeSuffix("Z").take(16)
                    Text(if (r.str("error").isNotBlank()) "Last check $when_ UTC failed: ${r.str("error")}"
                        else "Last check $when_ UTC: searched ${r.num("searched").toInt()}, upgraded ${r.num("upgraded").toInt()}, turned down ${r.num("rejected").toInt()}")
                }
                if (running == null) TextButton(enabled = "check" !in busy, onClick = { act("check", "Check started") { api.call("Squid", "/upgrades/run", "POST") } }) { Text("Check now") }
            } }
        }
        if (d != null && upgrades.isEmpty()) item { Text("No upgrades yet.") }
        items(upgrades, key = { "u" + it.str("id") }) { u ->
            val reverted = u.str("reverted").isNotBlank()
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                Text(u.str("title"), style = MaterialTheme.typography.titleSmall)
                Text("${u.str("artist")} · ${u.str("album")}")
                Text("${u.str("oldFormat")} → ${u.str("newFormat")}" + if (reverted) " · undone" else "")
                val c = u.obj()["checks"]?.obj()
                if (c != null) Text("Fingerprint match ${(c.num("fingerprint") * 100).toInt()}% · from ${u.str("source")}",
                    style = MaterialTheme.typography.bodySmall)
                Text(u.str("at").replace('T', ' ').removeSuffix("Z").take(16) + " UTC", style = MaterialTheme.typography.bodySmall)
                if (!reverted && !u.flag("final")) TextButton(enabled = u.str("id") !in busy, onClick = { undo = u }) { Text(stringResource(Res.string.action_undo_upgrade)) }
            } }
        }
        if (rejections.isNotEmpty()) item {
            TextButton(onClick = { showRejected = !showRejected }) {
                Text(if (showRejected) "Hide turned-down copies" else "Show ${rejections.size} turned-down copies")
            }
        }
        if (showRejected) items(rejections) { r ->
            Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text("${r.str("artist")} — ${r.str("title")}", style = MaterialTheme.typography.bodyMedium)
                Text(r.str("reason"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
