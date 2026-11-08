package com.mux.android.files

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val TOOL_TABS = listOf("LARGE", "JUNK", "TRASH", "WI-FI")
private var toolTab by mutableIntStateOf(0)

@Composable
fun ToolsScreen() {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(38.dp)) {
            TOOL_TABS.forEachIndexed { i, t ->
                HudButton(t, Modifier.weight(1f).fillMaxSize(), KeyKind.OP, selected = i == toolTab, fontSize = 11.sp) { toolTab = i }
            }
        }
        Spacer(Modifier.height(6.dp))
        when (toolTab) {
            0 -> LargeTool()
            1 -> JunkTool()
            2 -> TrashTool()
            else -> ShareTool()
        }
    }
}

private fun skip(f: File): Boolean {
    val p = f.absolutePath
    return p.startsWith(Trash.dir.absolutePath) || p.startsWith(File(Fs.ROOT, "Android").absolutePath)
}

@Composable
private fun LargeTool() {
    val c = Hud.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<File>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var scanned by remember { mutableIntStateOf(0) }
    var done by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HudText(
                if (busy) "SCANNING... $scanned FILES" else if (done) "TOP ${list.size} LARGEST FILES" else "FIND FILES EATING YOUR STORAGE",
                color = c.primary, size = 11.sp, modifier = Modifier.weight(1f)
            )
            HudButton("SCAN", Modifier.width(80.dp).height(38.dp), KeyKind.ACCENT, enabled = !busy, fontSize = 12.sp) {
                busy = true; done = false; scanned = 0; list = emptyList()
                scope.launch {
                    val res = withContext(Dispatchers.IO) {
                        val top = ArrayList<File>()
                        fun walk(d: File) {
                            val kids = d.listFiles() ?: return
                            for (k in kids) {
                                if (skip(k)) continue
                                if (k.isDirectory) walk(k)
                                else {
                                    scanned++
                                    top.add(k)
                                    if (top.size > 200) {
                                        top.sortByDescending { it.length() }
                                        while (top.size > 50) top.removeAt(top.size - 1)
                                    }
                                }
                            }
                        }
                        walk(Fs.ROOT)
                        top.sortedByDescending { it.length() }.take(50)
                    }
                    list = res; busy = false; done = true
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        if (done && list.isEmpty()) EmptyNote("NO FILES FOUND")
        LazyColumn(Modifier.weight(1f)) {
            items(list, key = { it.absolutePath }) { f ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    FileThumb(f, false, 40.dp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        HudText(f.name, color = c.text, size = 12.sp, maxLines = 1)
                        HudText(f.parent ?: "", color = c.dim, size = 9.sp, maxLines = 1)
                    }
                    HudText(Fs.human(f.length()), color = c.accent, size = 11.sp, bold = true)
                    Spacer(Modifier.width(4.dp))
                    HudButton("✕", Modifier.width(40.dp).height(36.dp), KeyKind.DANGER, fontSize = 11.sp) {
                        Ov.deleting = listOf(f)
                    }
                }
            }
        }
    }
}

private val JUNK_EXT = setOf("tmp", "temp", "bak", "log", "old", "dmp", "crdownload", "part")
private val JUNK_NAMES = setOf("thumbs.db", ".ds_store")

@Composable
private fun JunkTool() {
    val c = Hud.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<File>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    val total = list.sumOf { if (it.isFile) it.length() else 0L }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HudText(
                if (busy) "SCANNING..." else if (done) "${list.size} ITEMS · ${Fs.human(total)}" else "TEMP, LOG, BACKUP FILES AND EMPTY FOLDERS",
                color = c.primary, size = 11.sp, modifier = Modifier.weight(1f)
            )
            HudButton("SCAN", Modifier.width(80.dp).height(38.dp), KeyKind.ACCENT, enabled = !busy, fontSize = 12.sp) {
                busy = true; done = false; list = emptyList()
                scope.launch {
                    list = withContext(Dispatchers.IO) {
                        val out = ArrayList<File>()
                        fun walk(d: File) {
                            val kids = d.listFiles() ?: return
                            for (k in kids) {
                                if (skip(k)) continue
                                if (k.isDirectory) {
                                    if (k.name == ".thumbnails") { out.add(k); continue }
                                    walk(k)
                                    if ((k.listFiles()?.isEmpty()) == true) out.add(k)
                                } else if (k.length() == 0L || Fs.ext(k) in JUNK_EXT || k.name.lowercase() in JUNK_NAMES) out.add(k)
                            }
                        }
                        walk(Fs.ROOT)
                        out
                    }
                    busy = false; done = true
                }
            }
        }
        if (done && list.isNotEmpty()) {
            HudButton("DELETE ALL ${list.size} ITEMS", Modifier.fillMaxWidth().height(42.dp), KeyKind.DANGER, fontSize = 12.sp) {
                Ov.deleting = list
            }
        }
        Spacer(Modifier.height(4.dp))
        if (done && list.isEmpty()) EmptyNote("NOTHING TO CLEAN")
        LazyColumn(Modifier.weight(1f)) {
            items(list, key = { it.absolutePath }) { f ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        HudText((if (f.isDirectory) "▣ " else "") + f.name, color = c.text, size = 12.sp, maxLines = 1)
                        HudText(f.parent ?: "", color = c.dim, size = 9.sp, maxLines = 1)
                    }
                    HudText(if (f.isFile) Fs.human(f.length()) else "FOLDER", color = c.accent, size = 10.sp)
                    Spacer(Modifier.width(4.dp))
                    HudButton("✕", Modifier.width(40.dp).height(36.dp), KeyKind.DANGER, fontSize = 11.sp) {
                        Ov.deleting = listOf(f)
                        list = list - f
                    }
                }
            }
        }
    }
}

@Composable
private fun TrashTool() {
    val c = Hud.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var list by remember { mutableStateOf<List<TrashItem>>(emptyList()) }
    androidx.compose.runtime.LaunchedEffect(tick, Br.tick, Tasks.busy) {
        list = withContext(Dispatchers.IO) { Trash.items() }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HudText("${list.size} ITEMS IN RECYCLE BIN", color = c.primary, size = 11.sp, modifier = Modifier.weight(1f))
            HudButton("EMPTY", Modifier.width(80.dp).height(38.dp), KeyKind.DANGER, enabled = list.isNotEmpty(), fontSize = 12.sp) {
                scope.launch { withContext(Dispatchers.IO) { Trash.empty() }; tick++; Util.toast(ctx, "RECYCLE BIN EMPTIED") }
            }
        }
        if (!Prefs.useTrash) HudText("Recycle bin is OFF in settings: deletes are permanent.", color = c.accent, size = 10.sp)
        Spacer(Modifier.height(4.dp))
        if (list.isEmpty()) EmptyNote("RECYCLE BIN IS EMPTY")
        LazyColumn(Modifier.weight(1f)) {
            items(list, key = { it.id }) { t ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        HudText(File(t.original).name, color = c.text, size = 12.sp, maxLines = 1)
                        HudText("${File(t.original).parent} · ${Fs.dateShort(t.time)}", color = c.dim, size = 9.sp, maxLines = 1)
                    }
                    HudButton("RESTORE", Modifier.width(78.dp).height(36.dp), KeyKind.GOOD, fontSize = 9.sp) {
                        scope.launch {
                            val m = withContext(Dispatchers.IO) { Trash.restore(t) }
                            Util.toast(ctx, m.uppercase()); tick++; Br.reload()
                        }
                    }
                    HudButton("✕", Modifier.width(40.dp).height(36.dp), KeyKind.DANGER, fontSize = 11.sp) {
                        scope.launch { withContext(Dispatchers.IO) { Trash.remove(t) }; tick++ }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareTool() {
    val c = Hud.colors
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        HudPanel(Modifier.fillMaxWidth(), "WI-FI TRANSFER") {
            HudText(
                "Browse, download and upload files from a computer on the same Wi-Fi. Open the address below in the computer's browser.",
                color = c.text, size = 12.sp
            )
            Spacer(Modifier.height(8.dp))
            if (WebShare.running) {
                HudText(WebShare.url, color = c.accent, size = 16.sp, bold = true)
                Spacer(Modifier.height(4.dp))
                HudText("The number in the address is a PIN. Anyone on this network who knows it can read and write files in the shared folder.", color = c.dim, size = 10.sp)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    HudButton("COPY ADDRESS", Modifier.weight(1f).height(44.dp), KeyKind.OP, fontSize = 11.sp) { Util.copy(ctx, WebShare.url) }
                    HudButton("STOP", Modifier.weight(1f).height(44.dp), KeyKind.DANGER, fontSize = 12.sp) { WebShare.stop() }
                }
            } else {
                HudButton("START: SHARE ${Fs.ROOT.name.uppercase()}", Modifier.fillMaxWidth().height(46.dp), KeyKind.GOOD, fontSize = 12.sp) {
                    WebShare.start(Fs.ROOT)
                }
                if (WebShare.note.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    HudText(WebShare.note, color = c.danger, size = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        HudPanel(Modifier.fillMaxWidth(), "GOOD TO KNOW") {
            HudText("• Plain HTTP, not encrypted: use only on your home or a trusted network.", color = c.dim, size = 11.sp)
            HudText("• Works only while this app is open. The screen is kept awake while sharing.", color = c.dim, size = 11.sp)
            HudText("• Mobile hotspot works too: connect the computer to your phone's hotspot.", color = c.dim, size = 11.sp)
        }
    }
}
