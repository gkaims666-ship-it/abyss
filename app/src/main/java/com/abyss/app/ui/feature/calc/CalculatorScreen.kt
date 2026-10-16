package com.abyss.app.ui.feature.calc

import com.abyss.app.ui.i18n.LocalStrings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abyss.app.core.tools.CalcEngine
import com.abyss.app.ui.theme.AbyssPalette

private sealed class Key(val label: String) {
    class Digit(val d: String) : Key(d)
    class Op(val t: String, label: String = t) : Key(label)
    class Fn(val t: String) : Key(t)
    object Clear : Key("C")
    object Back : Key("⌫")
    object Equals : Key("=")
    object Dot : Key(".")
}

@Composable
fun CalculatorScreen(palette: AbyssPalette) {
    val S = LocalStrings.current
    var expr by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    val history = remember { mutableStateListOf<Pair<String, String>>() }

    fun recompute() {
        result = if (expr.isBlank()) "" else
            runCatching { CalcEngine.format(CalcEngine.evaluate(expr)) }.getOrDefault("")
    }

    fun press(key: Key) {
        when (key) {
            is Key.Digit -> { expr += key.d; recompute() }
            is Key.Dot -> { expr += "."; recompute() }
            is Key.Op -> { expr += key.t; recompute() }
            is Key.Fn -> { expr += key.t + "("; recompute() }
            Key.Clear -> { expr = ""; result = "" }
            Key.Back -> { if (expr.isNotEmpty()) expr = expr.dropLast(1); recompute() }
            Key.Equals -> {
                val r = runCatching { CalcEngine.format(CalcEngine.evaluate(expr)) }
                r.onSuccess {
                    if (expr.isNotBlank()) { history.add(0, expr to it); expr = it; result = "" }
                }.onFailure { result = S.error }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(14.dp)) {

        // History
        LazyColumn(Modifier.fillMaxWidth().weight(0.9f), reverseLayout = true) {
            items(history) { (e, r) ->
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(e, color = palette.onSurface.copy(alpha = 0.45f), fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace, textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth())
                    Text("= $r", color = palette.onSurface.copy(alpha = 0.7f), fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace, textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth())
                }
            }
        }

        // Current expression + live result
        Text(expr.ifBlank { "0" }, color = palette.onBackground, fontSize = 34.sp,
            fontFamily = FontFamily.Monospace, textAlign = TextAlign.End,
            maxLines = 2, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
        Text(if (result.isNotBlank()) "= $result" else "", color = palette.primary,
            fontSize = 20.sp, fontFamily = FontFamily.Monospace, textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.size(10.dp))

        val rows = listOf(
            listOf(Key.Clear, Key.Op("(", "("), Key.Op(")", ")"), Key.Back),
            listOf(Key.Fn("sin"), Key.Fn("cos"), Key.Fn("tan"), Key.Op("^", "^")),
            listOf(Key.Fn("ln"), Key.Fn("log"), Key.Fn("sqrt"), Key.Op("%", "%")),
            listOf(Key.Digit("7"), Key.Digit("8"), Key.Digit("9"), Key.Op("/", "÷")),
            listOf(Key.Digit("4"), Key.Digit("5"), Key.Digit("6"), Key.Op("*", "×")),
            listOf(Key.Digit("1"), Key.Digit("2"), Key.Digit("3"), Key.Op("-", "−")),
            listOf(Key.Op("pi", "π"), Key.Digit("0"), Key.Dot, Key.Op("+", "+")),
            listOf(Key.Op("e", "e"), Key.Equals),
        )
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    CalcButton(
                        key = key, palette = palette,
                        modifier = Modifier.weight(if (key is Key.Equals) 3f else 1f),
                        onClick = { press(key) })
                }
            }
            Spacer(Modifier.size(8.dp))
        }
    }
}

@Composable
private fun CalcButton(
    key: Key, palette: AbyssPalette, modifier: Modifier, onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.4f), label = "press")

    val (bg, fg) = when (key) {
        is Key.Digit, Key.Dot -> palette.surfaceVariant to palette.onSurface
        Key.Equals -> palette.primary to palette.onPrimary
        Key.Clear, Key.Back -> palette.error.copy(alpha = 0.22f) to palette.error
        is Key.Op -> palette.surface to palette.primary
        is Key.Fn -> palette.surface to palette.secondary
    }

    Box(
        modifier
            .scale(scale)
            .aspectRatio(if (key is Key.Equals) 3f else 1.3f)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(key.label, color = fg, fontSize = 20.sp, fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium)
    }
}
