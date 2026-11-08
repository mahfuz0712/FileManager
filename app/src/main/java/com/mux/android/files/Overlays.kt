package com.mux.android.files

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Which dialog / viewer is open right now. */
object Ov {
    var image by mutableStateOf<Pair<List<File>, Int>?>(null)
    var text by mutableStateOf<File?>(null)
    var zip by mutableStateOf<File?>(null)
    var apk by mutableStateOf<File?>(null)
    var props by mutableStateOf<File?>(null)
    var rename by mutableStateOf<File?>(null)
    var deleting by mutableStateOf<List<File>?>(null)
    var sort by mutableStateOf(false)
    var newMenu by mutableStateOf(false)
    var newKind by mutableStateOf<String?>(null)
    var search by mutableStateOf(false)
    var moreDir by mutableStateOf(false)
    var moreSel by mutableStateOf(false)
    fun any() = image != null || text != null
}

@Composable
fun PromptDialog(title: String, initial: String, hint: String, ok: String, onDone: (String) -> Unit, onCancel: () -> Unit) {
    var v by remember { mutableStateOf(initial) }
    HudDialog(title, onCancel) {
        HudField(v, hint, Modifier.fillMaxWidth().height(46.dp), onDone = { onDone(v) }) { v = it }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            HudButton("CANCEL", Modifier.weight(1f).height(44.dp), KeyKind.FN, fontSize = 12.sp) { onCancel() }
            HudButton(ok, Modifier.weight(1f).height(44.dp), KeyKind.ACCENT, fontSize = 12.sp) { onDone(v) }
        }
    }
}

@Composable
fun Overlays() {
    val ctx = LocalContext.current
    val c = Hud.colors

    // ----- delete confirm -----
    Ov.deleting?.let { fl ->
        HudDialog("DELETE ${fl.size} ITEM(S)?", { Ov.deleting = null }) {
            HudText(if (Prefs.useTrash) "Items go to the recycle bin (TOOLS > TRASH) and can be restored." else "Items are deleted for good.", color = c.text, size = 12.sp)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                HudButton("CANCEL", Modifier.weight(1f).height(44.dp), KeyKind.FN, fontSize = 12.sp) { Ov.deleting = null }
                HudButton("DELETE", Modifier.weight(1f).height(44.dp), KeyKind.DANGER, fontSize = 12.sp) { Ov.deleting = null; doDelete(ctx, fl) }
            }
        }
    }

    // ----- sort -----
    if (Ov.sort) {
        HudDialog("SORT BY", { Ov.sort = false }) {
            val names = listOf("NAME", "DATE", "SIZE", "TYPE")
            names.forEachIndexed { i, n ->
                HudButton(n, Modifier.fillMaxWidth().height(40.dp), KeyKind.OP, selected = Prefs.sortBy == i, fontSize = 12.sp) {
                    Prefs.sortBy = i; Prefs.commit(); Br.reload(); Ov.sort = false
                }
            }
            HudButton(if (Prefs.asc) "ORDER: A → Z / OLD → NEW / SMALL → BIG" else "ORDER: Z → A / NEW → OLD / BIG → SMALL", Modifier.fillMaxWidth().height(40.dp), KeyKind.ACCENT, fontSize = 9.sp) {
                Prefs.asc = !Prefs.asc; Prefs.commit(); Br.reload(); Ov.sort = false
            }
        }
    }

    // ----- new -----
    if (Ov.newMenu) {
        HudDialog("CREATE", { Ov.newMenu = false }) {
            HudButton("NEW FOLDER", Modifier.fillMaxWidth().height(44.dp), KeyKind.GOOD, fontSize = 12.sp) { Ov.newMenu = false; Ov.newKind = "folder" }
            HudButton("NEW FILE", Modifier.fillMaxWidth().height(44.dp), KeyKind.GOOD, fontSize = 12.sp) { Ov.newMenu = false; Ov.newKind = "file" }
        }
    }
    Ov.newKind?.let { k ->
        PromptDialog(if (k == "folder") "NEW FOLDER" else "NEW FILE", "", "name", "CREATE", onDone = { v ->
            val n = v.trim()
            if (n.isEmpty() || n.contains('/')) Util.toast(ctx, "BAD NAME")
            else {
                val f = File(Br.path, n)
                if (f.exists()) Util.toast(ctx, "ALREADY EXISTS")
                else {
                    val ok = try { if (k == "folder") f.mkdirs() else f.createNewFile() } catch (e: Exception) { false }
                    Util.toast(ctx, if (ok) "CREATED" else "FAILED")
                    Br.reload()
                }
            }
            Ov.newKind = null
        }, onCancel = { Ov.newKind = null })
    }

    // ----- search -----
    if (Ov.search) {
        PromptDialog("FIND IN ${Br.path.name.ifEmpty { "/" }.uppercase()} AND SUBFOLDERS", "", "part of the file name", "FIND", onDone = { v ->
            Ov.search = false
            if (v.isNotBlank()) Br.search(v.trim())
        }, onCancel = { Ov.search = false })
    }

    // ----- more (folder) -----
    if (Ov.moreDir) {
        HudDialog("OPTIONS", { Ov.moreDir = false }) {
            HudButton(if (Prefs.hidden) "HIDDEN FILES: SHOWN" else "HIDDEN FILES: HIDDEN", Modifier.fillMaxWidth().height(42.dp), KeyKind.OP, fontSize = 11.sp) {
                Prefs.hidden = !Prefs.hidden; Prefs.commit(); Br.reload(); Ov.moreDir = false
            }
            HudButton("SELECT ALL", Modifier.fillMaxWidth().height(42.dp), KeyKind.OP, fontSize = 11.sp) { Br.sel = Br.items.map { it.path }.toSet(); Ov.moreDir = false }
            HudButton("REFRESH", Modifier.fillMaxWidth().height(42.dp), KeyKind.OP, fontSize = 11.sp) { Br.reload(); Ov.moreDir = false }
            if (Br.title == null) {
                val pinned = Br.path.absolutePath in Prefs.bookmarks
                HudButton(if (pinned) "UNPIN THIS FOLDER" else "PIN THIS FOLDER TO HOME", Modifier.fillMaxWidth().height(42.dp), KeyKind.ACCENT, fontSize = 11.sp) {
                    val p = Br.path.absolutePath
                    Prefs.bookmarks = if (pinned) Prefs.bookmarks - p else Prefs.bookmarks + p
                    Prefs.commit(); Ov.moreDir = false
                }
                HudButton("PROPERTIES OF THIS FOLDER", Modifier.fillMaxWidth().height(42.dp), KeyKind.OP, fontSize = 11.sp) { Ov.props = Br.path; Ov.moreDir = false }
            }
        }
    }

    // ----- more (selection) -----
    if (Ov.moreSel) {
        val fl = Br.selectedFiles()
        HudDialog("${fl.size} SELECTED", { Ov.moreSel = false }) {
            if (fl.size == 1) {
                val f = fl[0]
                HudButton("RENAME", Modifier.fillMaxWidth().height(42.dp), KeyKind.OP, fontSize = 11.sp) { Ov.rename = f; Ov.moreSel = false }
                HudButton("PROPERTIES", Modifier.fillMaxWidth().height(42.dp), KeyKind.OP, fontSize = 11.sp) { Ov.props = f; Ov.moreSel = false }
                if (!f.isDirectory) HudButton("OPEN WITH ANOTHER APP", Modifier.fillMaxWidth().height(42.dp), KeyKind.OP, fontSize = 11.sp) { Fs.openExternal(ctx, f); Ov.moreSel = false }
                if (Br.title != null) HudButton("SHOW IN FOLDER", Modifier.fillMaxWidth().height(42.dp), KeyKind.ACCENT, fontSize = 11.sp) {
                    Ov.moreSel = false
                    f.parentFile?.let { Br.open(it, false) }
                }
                if (f.isDirectory) {
                    val pinned = f.absolutePath in Prefs.bookmarks
                    HudButton(if (pinned) "UNPIN FROM HOME" else "PIN TO HOME", Modifier.fillMaxWidth().height(42.dp), KeyKind.ACCENT, fontSize = 11.sp) {
                        Prefs.bookmarks = if (pinned) Prefs.bookmarks - f.absolutePath else Prefs.bookmarks + f.absolutePath
                        Prefs.commit(); Br.sel = emptySet(); Ov.moreSel = false
                    }
                }
            }
            HudButton("SELECT ALL", Modifier.fillMaxWidth().height(42.dp), KeyKind.OP, fontSize = 11.sp) { Br.sel = Br.items.map { it.path }.toSet(); Ov.moreSel = false }
            HudButton("INVERT SELECTION", Modifier.fillMaxWidth().height(42.dp), KeyKind.OP, fontSize = 11.sp) {
                Br.sel = Br.items.map { it.path }.toSet() - Br.sel; Ov.moreSel = false
            }
        }
    }

    Ov.rename?.let { f ->
        PromptDialog("RENAME", f.name, "new name", "RENAME", onDone = { v ->
            val n = v.trim()
            if (n.isEmpty() || n.contains('/')) Util.toast(ctx, "BAD NAME")
            else {
                val t = File(f.parentFile, n)
                if (t.exists()) Util.toast(ctx, "ALREADY EXISTS")
                else Util.toast(ctx, if (f.renameTo(t)) "RENAMED" else "RENAME FAILED")
                Br.sel = emptySet()
                Br.reload()
            }
            Ov.rename = null
        }, onCancel = { Ov.rename = null })
    }

    Ov.props?.let { PropsDialog(it) }
    Ov.zip?.let { ZipDialog(it) }
    Ov.apk?.let { ApkDialog(it) }
    Ov.image?.let { ImageViewer(it.first, it.second) }
    Ov.text?.let { TextViewer(it) }
}

@Composable
private fun PropsDialog(f: File) {
    val c = Hud.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var sums by remember { mutableStateOf(listOf<String>()) }
    var busy by remember { mutableStateOf(false) }
    val st by produceState<Stat?>(null, f.absolutePath) {
        value = if (f.isDirectory) withContext(Dispatchers.IO) { Fs.stat(f) } else null
    }
    HudDialog("PROPERTIES", { Ov.props = null }) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            HudText(f.name.ifEmpty { "/" }, color = c.primary, size = 13.sp, bold = true)
            HudText(f.absolutePath, color = c.dim, size = 10.sp)
            Spacer(Modifier.height(6.dp))
            val kind = Fs.kind(f)
            HudText("TYPE: ${if (f.isDirectory) "folder" else Fs.mime(f)}", color = c.text, size = 11.sp)
            if (f.isDirectory) HudText(st?.let { "CONTENT: ${it.files} files, ${it.dirs} folders\nSIZE: ${Fs.human(it.bytes)}" } ?: "SIZE: counting...", color = c.text, size = 11.sp)
            else HudText("SIZE: ${Fs.human(f.length())} (${f.length()} bytes)", color = c.text, size = 11.sp)
            HudText("MODIFIED: ${Fs.date(f.lastModified())}", color = c.text, size = 11.sp)
            HudText("ACCESS: ${if (f.canRead()) "read " else ""}${if (f.canWrite()) "write" else ""}", color = c.text, size = 11.sp)
            if (kind != Kind.DIR) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    for (a in listOf("MD5", "SHA-1", "SHA-256")) {
                        HudButton(a, Modifier.weight(1f).height(38.dp), KeyKind.OP, enabled = !busy, fontSize = 10.sp) {
                            scope.launch {
                                busy = true
                                val r = withContext(Dispatchers.IO) { try { Fs.checksum(f, a) } catch (e: Exception) { "error" } }
                                sums = sums + "$a: $r"
                                busy = false
                            }
                        }
                    }
                }
                for (s in sums) {
                    HudText(s, color = c.accent, size = 9.sp, modifier = Modifier.padding(top = 4.dp).combinedClickable { Util.copy(ctx, s.substringAfter(": "), "COPIED") })
                }
                if (sums.isNotEmpty()) HudText("TAP A VALUE TO COPY", color = c.dim, size = 8.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        HudButton("CLOSE", Modifier.fillMaxWidth().height(42.dp), KeyKind.FN, fontSize = 12.sp) { Ov.props = null }
    }
}

@Composable
private fun ZipDialog(f: File) {
    val c = Hud.colors
    val ctx = LocalContext.current
    val info by produceState<Pair<List<Pair<String, Long>>, Int>?>(null, f.absolutePath) {
        value = withContext(Dispatchers.IO) { try { Fs.zipEntries(f) } catch (e: Exception) { null } }
    }
    HudDialog(f.name, { Ov.zip = null }) {
        val i = info
        HudText(if (i == null) "reading..." else "${i.second} entries · ${Fs.human(f.length())}", color = c.dim, size = 10.sp)
        Column(Modifier.height(220.dp).verticalScroll(rememberScrollState())) {
            i?.first?.forEach { (n, s) -> HudText("$n  ${if (s >= 0 && !n.endsWith("/")) Fs.human(s) else ""}", color = c.text, size = 10.sp, maxLines = 1) }
            if (i != null && i.second > i.first.size) HudText("... ${i.second - i.first.size} more", color = c.dim, size = 10.sp)
        }
        Spacer(Modifier.height(8.dp))
        HudButton("EXTRACT TO NEW FOLDER HERE", Modifier.fillMaxWidth().height(42.dp), KeyKind.GOOD, fontSize = 11.sp) {
            val dest = Fs.uniqueTarget(f.parentFile ?: Fs.ROOT, f.name.substringBeforeLast('.', f.name))
            Ov.zip = null
            doUnzip(ctx, f, dest)
        }
        HudButton("EXTRACT INTO THIS FOLDER", Modifier.fillMaxWidth().height(42.dp), KeyKind.OP, fontSize = 11.sp) {
            val dest = f.parentFile ?: Fs.ROOT
            Ov.zip = null
            doUnzip(ctx, f, dest)
        }
        HudButton("CLOSE", Modifier.fillMaxWidth().height(40.dp), KeyKind.FN, fontSize = 11.sp) { Ov.zip = null }
    }
}

@Composable
private fun ApkDialog(f: File) {
    val c = Hud.colors
    val ctx = LocalContext.current
    val info = remember(f.absolutePath) {
        try {
            val pi = ctx.packageManager.getPackageArchiveInfo(f.absolutePath, 0)
            if (pi == null) "not a valid APK" else {
                val ai = pi.applicationInfo
                ai?.sourceDir = f.absolutePath
                ai?.publicSourceDir = f.absolutePath
                "${ai?.loadLabel(ctx.packageManager) ?: "?"}\n${pi.packageName}\nversion ${pi.versionName}"
            }
        } catch (e: Throwable) {
            "cannot read APK"
        }
    }
    HudDialog(f.name, { Ov.apk = null }) {
        HudText(info, color = c.text, size = 12.sp)
        HudText(Fs.human(f.length()), color = c.dim, size = 10.sp)
        Spacer(Modifier.height(8.dp))
        HudButton("INSTALL", Modifier.fillMaxWidth().height(44.dp), KeyKind.GOOD, enabled = f.extension.equals("apk", true), fontSize = 12.sp) {
            if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
                Util.toast(ctx, "ALLOW 'INSTALL UNKNOWN APPS' FOR THIS APP, THEN TRY AGAIN")
                try {
                    ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                }
            } else {
                Fs.openExternal(ctx, f)
                Ov.apk = null
            }
        }
        HudButton("CLOSE", Modifier.fillMaxWidth().height(40.dp), KeyKind.FN, fontSize = 11.sp) { Ov.apk = null }
    }
}

@Composable
private fun ImageViewer(files: List<File>, start: Int) {
    val c = Hud.colors
    val ctx = LocalContext.current
    var idx by remember { mutableIntStateOf(start.coerceIn(0, maxOf(files.size - 1, 0))) }
    var scale by remember { mutableFloatStateOf(1f) }
    var off by remember { mutableStateOf(Offset.Zero) }
    val f = files.getOrNull(idx)
    Dialog(onDismissRequest = { Ov.image = null }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black).safeDrawingPadding()) {
            if (f != null) {
                AsyncImage(
                    model = f, imageLoader = Thumbs.loader(ctx), contentDescription = null, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                        .pointerInput(idx) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 8f)
                                off = if (scale > 1f) off + pan else Offset.Zero
                            }
                        }
                        .graphicsLayer { scaleX = scale; scaleY = scale; translationX = off.x; translationY = off.y }
                )
                Column(Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Color(0xAA000000)).padding(8.dp)) {
                    HudText(f.name, color = c.primary, size = 12.sp, bold = true, maxLines = 1)
                    HudText("${idx + 1} / ${files.size} · ${Fs.human(f.length())}", color = c.dim, size = 10.sp)
                }
                Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(androidx.compose.ui.graphics.Color(0xAA000000)).padding(8.dp)) {
                    HudButton("◄", Modifier.weight(1f).height(44.dp), KeyKind.OP, enabled = idx > 0, fontSize = 14.sp) { idx--; scale = 1f; off = Offset.Zero }
                    HudButton("►", Modifier.weight(1f).height(44.dp), KeyKind.OP, enabled = idx < files.size - 1, fontSize = 14.sp) { idx++; scale = 1f; off = Offset.Zero }
                    HudButton("SHARE", Modifier.weight(1f).height(44.dp), KeyKind.GOOD, fontSize = 10.sp) { Fs.share(ctx, listOf(f)) }
                    HudButton("INFO", Modifier.weight(1f).height(44.dp), KeyKind.OP, fontSize = 10.sp) { Ov.props = f }
                    HudButton("CLOSE", Modifier.weight(1f).height(44.dp), KeyKind.DANGER, fontSize = 10.sp) { Ov.image = null }
                }
            }
        }
    }
}

@Composable
private fun TextViewer(f: File) {
    val c = Hud.colors
    val ctx = LocalContext.current
    var orig by remember(f.absolutePath) { mutableStateOf<String?>(null) }
    var txt by remember(f.absolutePath) { mutableStateOf("") }
    var warnClose by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(f.absolutePath) {
        if (f.length() > 1024 * 1024) { Util.toast(ctx, "FILE IS BIGGER THAN 1 MB: OPENING WITH ANOTHER APP"); Fs.openExternal(ctx, f); Ov.text = null; return@LaunchedEffect }
        val t = withContext(Dispatchers.IO) { try { f.readText() } catch (e: Exception) { null } }
        if (t == null) { Util.toast(ctx, "CANNOT READ FILE"); Ov.text = null } else { orig = t; txt = t }
    }
    val dirty = orig != null && txt != orig
    Dialog(onDismissRequest = { }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(c.bg).safeDrawingPadding().padding(8.dp)) {
            HudText(f.name + if (dirty) " ●" else "", color = c.primary, size = 13.sp, bold = true, maxLines = 1)
            HudText("${f.parent} · ${Fs.human(f.length())}", color = c.dim, size = 9.sp, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Box(Modifier.weight(1f).fillMaxWidth().border(1.dp, c.primary.copy(alpha = 0.45f), HudShape(8.dp)).padding(8.dp)) {
                BasicTextField(
                    value = txt, onValueChange = { txt = it; warnClose = false },
                    textStyle = TextStyle(color = c.text, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                    cursorBrush = SolidColor(c.primary),
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth()) {
                HudButton("SAVE", Modifier.weight(1f).height(44.dp), KeyKind.GOOD, enabled = dirty, fontSize = 12.sp) {
                    try { f.writeText(txt); orig = txt; Util.toast(ctx, "SAVED"); Br.reload() } catch (e: Exception) { Util.toast(ctx, "SAVE FAILED: ${e.message}") }
                }
                HudButton("SHARE", Modifier.weight(1f).height(44.dp), KeyKind.OP, fontSize = 12.sp) { Fs.share(ctx, listOf(f)) }
                HudButton(if (dirty && warnClose) "DISCARD?" else "CLOSE", Modifier.weight(1f).height(44.dp), KeyKind.DANGER, fontSize = 12.sp) {
                    if (dirty && !warnClose) { warnClose = true; Util.toast(ctx, "UNSAVED CHANGES: PRESS AGAIN TO DISCARD") } else Ov.text = null
                }
            }
        }
    }
}
