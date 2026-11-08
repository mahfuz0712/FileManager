package com.mux.android.files

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SORTS = listOf("NAME", "DATE", "SIZE", "TYPE")

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        HudPanel(Modifier.fillMaxWidth(), "APPEARANCE") {
            SettingRow("HUD colour", null, if (Prefs.amber) "AMBER" else "CYAN") { Prefs.amber = !Prefs.amber; Prefs.commit() }
            SettingRow("Default view", null, if (Prefs.grid) "GRID" else "LIST") { Prefs.grid = !Prefs.grid; Prefs.commit() }
        }
        Spacer(Modifier.height(6.dp))
        HudPanel(Modifier.fillMaxWidth(), "BROWSING") {
            SettingRow("Sort by", null, SORTS[Prefs.sortBy]) { Prefs.sortBy = (Prefs.sortBy + 1) % 4; Prefs.commit(); Br.reload() }
            SettingRow("Order", null, if (Prefs.asc) "A → Z" else "Z → A") { Prefs.asc = !Prefs.asc; Prefs.commit(); Br.reload() }
            SettingRow("Hidden files", "Names starting with a dot", if (Prefs.hidden) "SHOWN" else "HIDDEN") { Prefs.hidden = !Prefs.hidden; Prefs.commit(); Br.reload() }
        }
        Spacer(Modifier.height(6.dp))
        HudPanel(Modifier.fillMaxWidth(), "DELETING") {
            SettingRow("Recycle bin", "Deleted items go to a hidden folder first", if (Prefs.useTrash) "ON" else "OFF", if (Prefs.useTrash) KeyKind.GOOD else KeyKind.DANGER) { Prefs.useTrash = !Prefs.useTrash; Prefs.commit() }
            SettingRow("Confirm delete", null, if (Prefs.confirmDelete) "ON" else "OFF") { Prefs.confirmDelete = !Prefs.confirmDelete; Prefs.commit() }
        }
        Spacer(Modifier.height(6.dp))
        HudPanel(Modifier.fillMaxWidth(), "ACCESS") {
            HudText(if (Perm.has(ctx)) "All-files access: GRANTED" else "All-files access: NOT GRANTED", color = Hud.colors.text, size = 12.sp)
            HudButton("OPEN APP SETTINGS", Modifier.fillMaxWidth().height(42.dp), KeyKind.ACCENT, fontSize = 12.sp) { Util.openAppSettings(ctx) }
            HudText("Android/data and Android/obb are blocked by Android 11+ for every file manager except the system one.", color = Hud.colors.dim, size = 10.sp)
        }
    }
}
