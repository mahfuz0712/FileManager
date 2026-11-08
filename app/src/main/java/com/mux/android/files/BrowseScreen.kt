package com.mux.android.files

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object Thumbs {
    private var loader: ImageLoader? = null
    fun loader(ctx: Context): ImageLoader = loader ?: ImageLoader.Builder(ctx.applicationContext)
        .components { add(VideoFrameDecoder.Factory()) }
        .build().also { loader = it }
}

@Composable
fun kindColor(k: Kind): Color {
    val c = Hud.colors
    return when (k) {
        Kind.DIR -> c.accent
        Kind.IMAGE, Kind.VIDEO -> c.good
        Kind.AUDIO -> Color(0xFFD66BFF)
        Kind.DOC -> Color(0xFFFF8A80)
        Kind.ARCHIVE -> Color(0xFFFFD54F)
        Kind.APK -> c.good
        Kind.CODE -> c.primary
        else -> c.dim
    }
}

@Composable
fun FileThumb(f: File, dir: Boolean, size: Dp) {
    val ctx = LocalContext.current
    val c = Hud.colors
    val kind = Fs.kind(f, dir)
    val shape = HudShape(6.dp)
    val col = kindColor(kind)
    when (kind) {
        Kind.IMAGE, Kind.VIDEO -> {
            val req = remember(f.absolutePath) {
                ImageRequest.Builder(ctx).data(f).size(220).apply { if (kind == Kind.VIDEO) videoFrameMillis(1000) }.build()
            }
            AsyncImage(
                model = req, imageLoader = Thumbs.loader(ctx), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(shape).border(1.dp, col.copy(alpha = 0.6f), shape)
            )
        }
        Kind.APK -> {
            val icon by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, f.absolutePath) {
                value = withContext(Dispatchers.IO) {
                    try {
                        val pm = ctx.packageManager
                        val info = pm.getPackageArchiveInfo(f.absolutePath, 0)
                        val ai = info?.applicationInfo
                        if (ai != null) {
                            ai.sourceDir = f.absolutePath
                            ai.publicSourceDir = f.absolutePath
                            ai.loadIcon(pm).toBitmap(96, 96).asImageBitmap()
                        } else null
                    } catch (e: Throwable) {
                        null
                    }
                }
            }
            val ic = icon
            if (ic != null) Image(ic, null, Modifier.size(size).clip(shape))
            else Box(Modifier.size(size).border(1.dp, col, shape), contentAlignment = Alignment.Center) { HudText("APK", color = col, size = 11.sp, bold = true) }
        }
        Kind.DIR -> Box(
            Modifier.size(size).background(col.copy(alpha = 0.12f), shape).border(1.dp, col.copy(alpha = 0.8f), shape),
            contentAlignment = Alignment.Center
        ) { HudText("▣", color = col, size = (size.value * 0.45f).sp) }
        else -> Box(
            Modifier.size(size).background(col.copy(alpha = 0.08f), shape).border(1.dp, col.copy(alpha = 0.6f), shape),
            contentAlignment = Alignment.Center
        ) { HudText(Fs.ext(f).take(4).uppercase().ifEmpty { "FILE" }, color = col, size = (size.value * 0.24f).coerceAtLeast(9f).sp, bold = true, maxLines = 1) }
    }
}

fun openItem(ctx: Context, it: Item) {
    when (Fs.kind(it.f, it.dir)) {
        Kind.IMAGE -> {
            val imgs = Br.items.filter { x -> !x.dir && Fs.kind(x.f) == Kind.IMAGE }.map { x -> x.f }
            Ov.image = imgs to imgs.indexOf(it.f).coerceAtLeast(0)
        }
        Kind.CODE, Kind.TEXT -> Ov.text = it.f
        Kind.ARCHIVE -> if (Fs.ext(it.f) == "zip" || Fs.ext(it.f) == "jar") Ov.zip = it.f else Fs.openExternal(ctx, it.f)
        Kind.APK -> Ov.apk = it.f
        else -> Fs.openExternal(ctx, it.f)
    }
}

// ---------- actions ----------

fun doPaste(ctx: Context) {
    val files = Br.clip
    val cut = Br.clipCut
    val dest = Br.path
    if (files.isEmpty()) return
    if (cut) Br.clip = emptyList()
    Tasks.run(ctx, if (cut) "MOVING" else "COPYING", Fs.totalSize(files)) { prog, c ->
        var n = 0
        for (f in files) {
            if (c.on) throw InterruptedException()
            if (cut) Fs.moveTree(f, dest, prog, c) else Fs.copyTree(f, dest, prog, c)
            n++
        }
        "${if (cut) "MOVED" else "COPIED"} $n ITEM(S)"
    }
}

fun doDelete(ctx: Context, files: List<File>) {
    val bin = Trash.dir.absolutePath
    Tasks.run(ctx, "DELETING", 0) { _, c ->
        var n = 0
        var failed = 0
        for (f in files) {
            if (c.on) throw InterruptedException()
            val inBin = f.absolutePath.startsWith(bin)
            val ok = if (Prefs.useTrash && !inBin) Trash.put(f, c) else Fs.deleteTree(f)
            if (ok) n++ else failed++
        }
        "DELETED $n" + if (failed > 0) ", $failed FAILED" else ""
    }
}

fun doZip(ctx: Context, files: List<File>) {
    val dir = Br.path
    val name = if (files.size == 1) files[0].name.substringBeforeLast('.', files[0].name) + ".zip" else "archive.zip"
    val out = Fs.uniqueTarget(dir, name)
    Tasks.run(ctx, "COMPRESSING", Fs.totalSize(files)) { prog, c ->
        try {
            Fs.zip(files, out, prog, c)
        } catch (e: Exception) {
            out.delete(); throw e
        }
        "CREATED ${out.name}"
    }
}

fun doUnzip(ctx: Context, zip: File, dest: File) {
    Tasks.run(ctx, "EXTRACTING", zip.length() * 2) { prog, c ->
        Fs.unzip(zip, dest, prog, c)
        "EXTRACTED TO ${dest.name}"
    }
}

// ---------- screen ----------

@Composable
private fun TaskBanner() {
    val c = Hud.colors
    HudPanel(Modifier.fillMaxWidth(), Tasks.label) {
        val frac = if (Tasks.total > 0) (Tasks.done.toFloat() / Tasks.total).coerceIn(0f, 1f) else 0f
        Box(Modifier.fillMaxWidth().height(8.dp).background(c.primary.copy(alpha = 0.15f))) {
            Box(Modifier.fillMaxWidth(if (Tasks.total > 0) frac else 0.3f).height(8.dp).background(c.accent))
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            HudText(if (Tasks.total > 0) "${Fs.human(Tasks.done)} / ${Fs.human(Tasks.total)}" else "WORKING...", color = c.dim, size = 10.sp, modifier = Modifier.weight(1f))
            HudButton("CANCEL", Modifier.width(80.dp).height(32.dp), KeyKind.DANGER, fontSize = 10.sp) { Tasks.cancel() }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BrowseScreen() {
    val c = Hud.colors
    val ctx = LocalContext.current
    val selected = Br.sel
    val selecting = selected.isNotEmpty()
    val parts = remember(Br.path) {
        val l = ArrayList<Pair<String, File>>()
        var cur = File("/")
        for (p in Br.path.absolutePath.split('/').filter { it.isNotEmpty() }) {
            cur = File(cur, p)
            l.add(p to cur)
        }
        l
    }

    Column(Modifier.fillMaxSize()) {
        if (Tasks.busy) { TaskBanner(); Spacer(Modifier.height(4.dp)) }

        if (selecting) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                HudText("${selected.size} SELECTED", color = c.accent, size = 12.sp, bold = true, modifier = Modifier.weight(1f))
                HudButton("✕", Modifier.width(44.dp).height(34.dp), KeyKind.FN, fontSize = 13.sp) { Br.sel = emptySet() }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                val w = Modifier.width(72.dp).height(38.dp)
                HudButton("COPY", w, KeyKind.OP, fontSize = 10.sp) { Br.clip = Br.selectedFiles(); Br.clipCut = false; Br.sel = emptySet(); Util.toast(ctx, "COPIED: PASTE IN ANOTHER FOLDER") }
                HudButton("CUT", w, KeyKind.OP, fontSize = 10.sp) { Br.clip = Br.selectedFiles(); Br.clipCut = true; Br.sel = emptySet(); Util.toast(ctx, "CUT: PASTE IN ANOTHER FOLDER") }
                HudButton("DELETE", w, KeyKind.DANGER, fontSize = 10.sp) {
                    val fl = Br.selectedFiles()
                    if (Prefs.confirmDelete) Ov.deleting = fl else doDelete(ctx, fl)
                }
                HudButton("ZIP", w, KeyKind.ACCENT, fontSize = 10.sp) { doZip(ctx, Br.selectedFiles()) }
                HudButton("SHARE", w, KeyKind.GOOD, fontSize = 10.sp) { Fs.share(ctx, Br.selectedFiles()) }
                HudButton("MORE", w, KeyKind.FN, fontSize = 10.sp) { Ov.moreSel = true }
            }
        } else {
            val t = Br.title
            if (t != null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    HudText(t, color = c.primary, size = 12.sp, bold = true, maxLines = 1, modifier = Modifier.weight(1f))
                    HudButton("CLOSE", Modifier.width(72.dp).height(34.dp), KeyKind.FN, fontSize = 10.sp) { Br.goBack() }
                }
            } else {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    HudText("/", color = c.dim, size = 12.sp, modifier = Modifier.combinedClickable { Br.open(File("/")) })
                    for ((n, f) in parts) {
                        HudText(" ▸ ", color = c.dim, size = 11.sp)
                        HudText(n, color = if (f == Br.path) c.primary else c.text, size = 12.sp, bold = f == Br.path, maxLines = 1, modifier = Modifier.combinedClickable { Br.open(f) })
                    }
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                val w = Modifier.width(64.dp).height(36.dp)
                HudButton("↑ UP", w, KeyKind.OP, fontSize = 10.sp) { if (!Br.goBack()) Util.toast(ctx, "TOP FOLDER") }
                HudButton(if (Prefs.grid) "LIST" else "GRID", w, KeyKind.OP, fontSize = 10.sp) { Prefs.grid = !Prefs.grid; Prefs.commit() }
                HudButton("SORT", w, KeyKind.OP, fontSize = 10.sp) { Ov.sort = true }
                HudButton("+ NEW", w, KeyKind.GOOD, fontSize = 10.sp) { Ov.newMenu = true }
                HudButton("FIND", w, KeyKind.ACCENT, fontSize = 10.sp) { Ov.search = true }
                HudButton("MORE", w, KeyKind.FN, fontSize = 10.sp) { Ov.moreDir = true }
            }
        }

        if (Br.clip.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                HudText("${Br.clip.size} ITEM(S) ${if (Br.clipCut) "CUT" else "COPIED"}", color = c.accent, size = 10.sp, modifier = Modifier.weight(1f))
                HudButton("PASTE HERE", Modifier.width(110.dp).height(36.dp), KeyKind.ACCENT, enabled = !Tasks.busy && Br.title == null, fontSize = 10.sp) { doPaste(ctx) }
                HudButton("✕", Modifier.width(40.dp).height(36.dp), KeyKind.FN, fontSize = 12.sp) { Br.clip = emptyList() }
            }
        }
        Spacer(Modifier.height(4.dp))

        if (Br.loading && Br.items.isEmpty()) HudText("LOADING...", color = c.dim, size = 11.sp)
        if (!Br.loading && Br.items.isEmpty()) EmptyNote(if (Br.title != null) "NOTHING FOUND" else if (!Br.path.canRead()) "CANNOT READ THIS FOLDER (SYSTEM RULE OR NO PERMISSION)" else "EMPTY FOLDER")

        if (Prefs.grid) {
            LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(Br.items, key = { it.path }) { it ->
                    val on = it.path in selected
                    Column(
                        Modifier.padding(3.dp)
                            .background(if (on) c.primary.copy(alpha = 0.2f) else Color.Transparent, HudShape(6.dp))
                            .combinedClickable(
                                onClick = { if (selecting) Br.toggle(it) else if (it.dir) Br.open(it.f) else openItem(ctx, it) },
                                onLongClick = { Br.toggle(it) }
                            )
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        FileThumb(it.f, it.dir, 76.dp)
                        HudText((if (on) "✔ " else "") + it.name, color = if (on) c.accent else c.text, size = 10.sp, maxLines = 2)
                    }
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                items(Br.items, key = { it.path }) { it ->
                    val on = it.path in selected
                    Row(
                        Modifier.fillMaxWidth().height(56.dp)
                            .background(if (on) c.primary.copy(alpha = 0.18f) else Color.Transparent, HudShape(6.dp))
                            .combinedClickable(
                                onClick = { if (selecting) Br.toggle(it) else if (it.dir) Br.open(it.f) else openItem(ctx, it) },
                                onLongClick = { Br.toggle(it) }
                            )
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FileThumb(it.f, it.dir, 42.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            HudText(it.name, color = if (on) c.accent else c.text, size = 13.sp, maxLines = 1)
                            HudText(
                                (if (it.dir) "FOLDER" else Fs.human(it.size)) + " · " + Fs.dateShort(it.mod) +
                                    if (Br.title != null) " · " + (it.f.parent ?: "") else "",
                                color = c.dim, size = 10.sp, maxLines = 1
                            )
                        }
                        if (on) HudText("✔", color = c.accent, size = 16.sp, bold = true)
                    }
                }
            }
        }
    }
}
