package com.mux.android.files

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/** Rectangle with two cut corners (top-left and bottom-right) - the HUD frame look. */
class HudShape(private val cut: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val c = with(density) { cut.toPx() }.coerceAtMost(minOf(size.width, size.height) / 2f)
        val p = Path().apply {
            moveTo(c, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height - c)
            lineTo(size.width - c, size.height)
            lineTo(0f, size.height)
            lineTo(0f, c)
            close()
        }
        return Outline.Generic(p)
    }
}

enum class KeyKind { NUM, OP, FN, ACCENT, DANGER, GOOD }

@Composable
fun HudText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Hud.colors.text,
    size: TextUnit = 14.sp,
    bold: Boolean = false,
    align: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = size,
        fontFamily = FontFamily.Monospace,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        textAlign = align,
        maxLines = maxLines
    )
}

@Composable
fun HudPanel(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = Hud.colors
    val shape = HudShape(12.dp)
    Column(
        modifier
            .background(c.panel.copy(alpha = 0.82f), shape)
            .drawBehind {
                val t = 10.dp.toPx()
                val w = 2.dp.toPx()
                drawLine(c.primary, Offset(size.width - t, 0f), Offset(size.width, 0f), w)
                drawLine(c.primary, Offset(size.width, 0f), Offset(size.width, t), w)
                drawLine(c.primary, Offset(0f, size.height - t), Offset(0f, size.height), w)
                drawLine(c.primary, Offset(0f, size.height), Offset(t, size.height), w)
            }
            .border(1.dp, c.primary.copy(alpha = 0.5f), shape)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        if (title != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Box(Modifier.size(width = 4.dp, height = 10.dp).background(c.accent))
                Spacer(Modifier.width(6.dp))
                HudText(title, color = c.primary, size = 10.sp, maxLines = 1)
            }
        }
        content()
    }
}

/** HUD key. [sub] is a small second line (letters on a dial pad). [onLongClick] is optional. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HudButton(
    label: String,
    modifier: Modifier = Modifier,
    kind: KeyKind = KeyKind.NUM,
    enabled: Boolean = true,
    selected: Boolean = false,
    fontSize: TextUnit = 18.sp,
    sub: String? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val c = Hud.colors
    val shape = HudShape(6.dp)
    val base = when (kind) {
        KeyKind.NUM -> c.text
        KeyKind.OP -> c.primary
        KeyKind.FN -> c.primary.copy(alpha = 0.75f)
        KeyKind.ACCENT -> c.accent
        KeyKind.DANGER -> c.danger
        KeyKind.GOOD -> c.good
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fill = if (pressed || selected) base.copy(alpha = 0.28f) else base.copy(alpha = 0.06f)
    Box(
        modifier
            .padding(2.dp)
            .background(fill, shape)
            .border(1.dp, base.copy(alpha = if (enabled) 0.6f else 0.2f), shape)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onLongClick = onLongClick,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            HudText(
                text = label,
                modifier = Modifier.padding(horizontal = 6.dp),
                color = if (enabled) base else base.copy(alpha = 0.3f),
                size = fontSize,
                bold = true,
                maxLines = 1
            )
            if (sub != null) {
                HudText(sub, color = (if (enabled) base else base).copy(alpha = 0.55f), size = 9.sp, maxLines = 1)
            }
        }
    }
}

/** A list row with the HUD frame. Content is free-form (use Row / Column inside). */
@Composable
fun HudItem(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = Hud.colors
    val shape = HudShape(8.dp)
    Column(
        modifier
            .padding(vertical = 2.dp)
            .background(c.primary.copy(alpha = if (selected) 0.14f else 0.04f), shape)
            .border(1.dp, c.primary.copy(alpha = if (selected) 0.6f else 0.25f), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        content = content
    )
}

@Composable
fun HudBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = Hud.colors
    val transition = rememberInfiniteTransition(label = "hud-bg")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(40000, easing = LinearEasing)),
        label = "angle"
    )
    Box(
        modifier
            .background(c.bg)
            .drawBehind {
                val step = 28.dp.toPx()
                var x = 0f
                while (x < size.width) {
                    drawLine(c.primary.copy(alpha = 0.05f), Offset(x, 0f), Offset(x, size.height), 1f)
                    x += step
                }
                var y = 0f
                while (y < size.height) {
                    drawLine(c.primary.copy(alpha = 0.05f), Offset(0f, y), Offset(size.width, y), 1f)
                    y += step
                }
                val center = Offset(size.width * 0.82f, size.height * 0.18f)
                val r = minOf(size.width, size.height) * 0.35f
                drawCircle(c.primary.copy(alpha = 0.07f), r * 0.8f, center, style = Stroke(1.dp.toPx()))
                rotate(degrees = angle, pivot = center) {
                    drawArc(
                        color = c.primary.copy(alpha = 0.14f),
                        startAngle = 0f,
                        sweepAngle = 80f,
                        useCenter = false,
                        topLeft = Offset(center.x - r, center.y - r),
                        size = Size(2 * r, 2 * r),
                        style = Stroke(3.dp.toPx())
                    )
                    drawArc(
                        color = c.accent.copy(alpha = 0.12f),
                        startAngle = 140f,
                        sweepAngle = 50f,
                        useCenter = false,
                        topLeft = Offset(center.x - r, center.y - r),
                        size = Size(2 * r, 2 * r),
                        style = Stroke(3.dp.toPx())
                    )
                }
            },
        content = content
    )
}

/** Pop-up window in the HUD frame. */
@Composable
fun HudDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = Hud.colors
    Dialog(onDismissRequest = onDismiss) {
        Box(Modifier.fillMaxWidth().background(c.bg, HudShape(12.dp))) {
            HudPanel(Modifier.fillMaxWidth(), title, content)
        }
    }
}
