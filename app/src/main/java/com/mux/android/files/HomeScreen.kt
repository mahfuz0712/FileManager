package com.mux.android.files

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Storage permission state. [tick] is bumped when the app resumes so the UI re-checks. */
object Perm {
    var tick by mutableIntStateOf(0)

    fun has(ctx: android.content.Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else ctx.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
}

fun openAny(ctx: android.content.Context, f: File) {
    when (Fs.kind(f)) {
        Kind.IMAGE -> Ov.image = listOf(f) to 0
        Kind.CODE, Kind.TEXT -> Ov.text = f
        Kind.ARCHIVE -> if (Fs.ext(f) == "zip" || Fs.ext(f) == "jar") Ov.zip = f else Fs.openExternal(ctx, f)
        Kind.APK -> Ov.apk = f
        else -> Fs.openExternal(ctx, f)
    }
}

private val CATS = listOf("IMAGES", "VIDEOS", "AUDIO", "DOCUMENTS", "APKS", "ARCHIVES", "DOWNLOADS")
private val SHORTS = listOf("Download", "DCIM", "Documents", "Pictures", "Music", "Movies")

@Composable
fun HomeScreen() {
    val c = Hud.colors
    val ctx = LocalContext.current
    val tick = Perm.tick
    val granted = remember(tick) { Perm.has(ctx) }

    val legacy = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { Perm.tick++ }
    val grant: () -> Unit = {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + ctx.packageName)))
            } catch (e: Exception) {
                ctx.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            legacy.launch(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE))
        }
    }

    if (!granted) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            PermissionPanel("Files needs 'All files access' to show and manage everything on your storage. Turn it on for Files, then come back.", grant)
        }
        return
    }

    var volumes by remember { mutableStateOf<List<StorageInfo>>(emptyList()) }
    var recent by remember { mutableStateOf<List<File>>(emptyList()) }
    LaunchedEffect(tick, Br.tick) {
        volumes = withContext(Dispatchers.IO) { Fs.volumes(ctx) }
        recent = withContext(Dispatchers.IO) { Fs.recent(ctx, 20) }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        for (v in volumes) {
            val used = (v.total - v.free).coerceAtLeast(0)
            val frac = if (v.total > 0) used.toFloat() / v.total else 0f
            HudPanel(Modifier.fillMaxWidth().padding(bottom = 6.dp), "STORAGE") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        HudText(v.label.uppercase(), color = c.text, size = 13.sp, bold = true, maxLines = 1)
                        HudText("${Fs.human(used)} used of ${Fs.human(v.total)} · ${Fs.human(v.free)} free", color = c.dim, size = 10.sp)
                    }
                    HudButton("OPEN", Modifier.width(72.dp).height(36.dp), KeyKind.ACCENT, fontSize = 11.sp) {
                        Br.open(v.dir, push = false); Nav.tab = Nav.FILES
                    }
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(8.dp).background(c.primary.copy(alpha = 0.15f))) {
                    Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).height(8.dp).background(if (frac > 0.9f) c.danger else c.primary))
                }
            }
        }

        HudPanel(Modifier.fillMaxWidth().padding(bottom = 6.dp), "CATEGORIES") {
            for (row in CATS.chunked(4)) {
                Row(Modifier.fillMaxWidth()) {
                    for (k in row) {
                        HudButton(k, Modifier.weight(1f).height(46.dp), KeyKind.OP, fontSize = 9.sp) {
                            Br.showList(k, Fs.category(ctx, k))
                            Nav.tab = Nav.FILES
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        HudPanel(Modifier.fillMaxWidth().padding(bottom = 6.dp), "SHORTCUTS") {
            for (row in SHORTS.chunked(3)) {
                Row(Modifier.fillMaxWidth()) {
                    for (s in row) {
                        HudButton(s.uppercase(), Modifier.weight(1f).height(42.dp), KeyKind.FN, fontSize = 10.sp) {
                            val d = File(Fs.ROOT, s)
                            if (!d.exists()) d.mkdirs()
                            Br.open(d, push = false); Nav.tab = Nav.FILES
                        }
                    }
                }
            }
        }

        if (Prefs.bookmarks.isNotEmpty()) {
            HudPanel(Modifier.fillMaxWidth().padding(bottom = 6.dp), "PINNED") {
                for (p in Prefs.bookmarks) {
                    val f = File(p)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        HudButton("▸ " + f.name, Modifier.weight(1f).height(38.dp), KeyKind.ACCENT, fontSize = 11.sp) {
                            if (f.isDirectory) { Br.open(f, push = false); Nav.tab = Nav.FILES } else openAny(ctx, f)
                        }
                        HudButton("✕", Modifier.width(44.dp).height(38.dp), KeyKind.FN, fontSize = 11.sp) {
                            Prefs.bookmarks = Prefs.bookmarks - p; Prefs.commit()
                        }
                    }
                }
            }
        }

        HudPanel(Modifier.fillMaxWidth().padding(bottom = 6.dp), "RECENT") {
            if (recent.isEmpty()) HudText("Nothing yet.", color = c.dim, size = 11.sp)
            for (f in recent) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FileThumb(f, false, 40.dp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        HudText(f.name, color = c.text, size = 12.sp, maxLines = 1)
                        HudText("${Fs.human(f.length())} · ${Fs.dateShort(f.lastModified())}", color = c.dim, size = 9.sp)
                    }
                    HudButton("OPEN", Modifier.width(60.dp).height(34.dp), KeyKind.OP, fontSize = 9.sp) { openAny(ctx, f) }
                }
            }
        }
    }
}
