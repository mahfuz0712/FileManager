package com.mux.android.files

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shown by a screen when it cannot work without a runtime permission. */
@Composable
fun PermissionPanel(text: String, onGrant: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    HudPanel(modifier.fillMaxWidth(), "PERMISSION REQUIRED") {
        HudText(text, color = Hud.colors.text, size = 13.sp)
        Spacer(Modifier.height(8.dp))
        HudButton("GRANT ACCESS", Modifier.fillMaxWidth().height(46.dp), KeyKind.ACCENT, fontSize = 13.sp) { onGrant() }
        HudButton("APP SETTINGS", Modifier.fillMaxWidth().height(40.dp), KeyKind.FN, fontSize = 12.sp) {
            Util.openAppSettings(ctx)
        }
    }
}

@Composable
fun HudField(
    value: String,
    hint: String,
    modifier: Modifier = Modifier,
    focus: FocusRequester? = null,
    onDone: (() -> Unit)? = null,
    onChange: (String) -> Unit
) {
    val c = Hud.colors
    val shape = HudShape(8.dp)
    Box(
        modifier
            .background(c.primary.copy(alpha = 0.06f), shape)
            .border(1.dp, c.primary.copy(alpha = 0.5f), shape)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) HudText(hint, color = c.dim, size = 13.sp, maxLines = 1)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = c.text, fontSize = 15.sp, fontFamily = FontFamily.Monospace),
            cursorBrush = SolidColor(c.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onDone?.invoke() }),
            modifier = if (focus != null) Modifier.fillMaxWidth().focusRequester(focus) else Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun EmptyNote(text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        HudText(text, color = Hud.colors.dim, size = 12.sp)
    }
}

@Composable
fun SettingRow(label: String, hint: String?, value: String, kind: KeyKind = KeyKind.OP, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            HudText(label, color = Hud.colors.text, size = 12.sp)
            if (hint != null) HudText(hint, color = Hud.colors.dim, size = 9.sp)
        }
        HudButton(value, Modifier.width(118.dp).height(38.dp), kind, fontSize = 11.sp, onClick = onClick)
    }
}

@Composable
fun LinkRow(label: String, value: String, link: String?) {
    val c = Hud.colors
    val ctx = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (link != null) Modifier.clickable { Util.open(ctx, link) } else Modifier)
            .padding(vertical = 6.dp)
    ) {
        HudText(label, color = c.dim, size = 10.sp)
        HudText(value, color = if (link != null) c.primary else c.text, size = 16.sp)
    }
}
