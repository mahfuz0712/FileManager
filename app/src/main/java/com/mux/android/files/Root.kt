package com.mux.android.files

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
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

object Nav {
    const val HOME = 0
    const val FILES = 1
    const val TOOLS = 2
    const val SET = 3
    const val SYS = 4
    var tab by mutableIntStateOf(HOME)
}

private val TABS = listOf("HOME", "FILES", "TOOLS", "SET", "SYS")

@Composable
fun FilesRoot() {
    val ctx = LocalContext.current
    MuxTheme(if (Prefs.amber) AmberPalette else CyanPalette) {
        val c = Hud.colors
        var update by remember { mutableStateOf<UpdateInfo?>(null) }

        LaunchedEffect(Unit) {
            if (isOnline(ctx)) update = checkUpdate(ctx).second
        }

        update?.let { u ->
            UpdateDialog(u, appVersion(ctx), onUpdate = { Util.open(ctx, u.apkUrl); update = null }, onCancel = { update = null })
        }

        // Back: close viewers/dialogs first, then selection, then folders, then go home.
        BackHandler(enabled = true) {
            when {
                Ov.image != null -> Ov.image = null
                Ov.text != null -> Ov.text = null
                Ov.zip != null -> Ov.zip = null
                Ov.apk != null -> Ov.apk = null
                Ov.props != null -> Ov.props = null
                Ov.rename != null -> Ov.rename = null
                Ov.deleting != null -> Ov.deleting = null
                Br.sel.isNotEmpty() -> Br.sel = emptySet()
                Nav.tab == Nav.FILES -> { if (!Br.goBack()) Nav.tab = Nav.HOME }
                Nav.tab != Nav.HOME -> Nav.tab = Nav.HOME
                else -> (ctx as? android.app.Activity)?.finish()
            }
        }

        HudBackground(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 8.dp, vertical = 6.dp)) {
                Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically) {
                    HudText("◈ FILE MANAGER", color = c.primary, size = 16.sp, bold = true)
                    Spacer(Modifier.width(8.dp))
                    HudText(TABS[Nav.tab], color = c.dim, size = 10.sp)
                }
                Spacer(Modifier.height(4.dp))
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (Nav.tab) {
                        Nav.HOME -> HomeScreen()
                        Nav.FILES -> if (Perm.has(ctx)) BrowseScreen() else HomeScreen()
                        Nav.TOOLS -> ToolsScreen()
                        Nav.SET -> SettingsScreen()
                        else -> SystemScreen(false) { update = it }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth().height(44.dp)) {
                    TABS.forEachIndexed { i, t ->
                        HudButton(t, Modifier.weight(1f).fillMaxHeight(), KeyKind.OP, selected = i == Nav.tab, fontSize = 11.sp) {
                            if (i == Nav.FILES && Br.title != null && Nav.tab == Nav.FILES) Br.goBack()
                            Nav.tab = i
                        }
                    }
                }
            }
        }
        Overlays()
    }
}
