package com.abyss.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abyss.app.ui.theme.AbyssFonts
import com.abyss.app.ui.theme.AbyssPalette

private sealed class MdBlock {
    data class Para(val text: String) : MdBlock()
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class Bullet(val text: String, val marker: String) : MdBlock()
    data class Quote(val text: String) : MdBlock()
    data class Code(val lang: String, val code: String) : MdBlock()
    object Rule : MdBlock()
}

private fun parseBlocks(src: String): List<MdBlock> {
    val out = mutableListOf<MdBlock>()
    val lines = src.replace("\r", "").split('\n')
    val para = StringBuilder()
    fun flush() { if (para.isNotBlank()) out += MdBlock.Para(para.toString().trim()); para.clear() }

    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trimStart()
        when {
            trimmed.startsWith("```") -> {
                flush()
                val lang = trimmed.removePrefix("```").trim()
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    if (code.isNotEmpty()) code.append('\n')
                    code.append(lines[i]); i++
                }
                out += MdBlock.Code(lang, code.toString())
            }
            trimmed.startsWith("#") -> {
                flush()
                val level = trimmed.takeWhile { it == '#' }.length.coerceIn(1, 4)
                out += MdBlock.Heading(level, trimmed.drop(level).trim())
            }
            trimmed == "---" || trimmed == "***" -> { flush(); out += MdBlock.Rule }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ") -> {
                flush(); out += MdBlock.Bullet(trimmed.drop(2), "•")
            }
            Regex("^\\d+[.)] ").containsMatchIn(trimmed) -> {
                flush()
                val m = Regex("^(\\d+)[.)] ").find(trimmed)!!
                out += MdBlock.Bullet(trimmed.substring(m.range.last + 1), m.groupValues[1] + ".")
            }
            trimmed.startsWith("> ") -> { flush(); out += MdBlock.Quote(trimmed.drop(2)) }
            trimmed.isBlank() -> flush()
            else -> { if (para.isNotEmpty()) para.append(' '); para.append(trimmed) }
        }
        i++
    }
    flush()
    return out
}

private val INLINE = Regex(
    "(\\*\\*[^*]+\\*\\*)|(`[^`]+`)|(\\*[^*\\n]+\\*)|(_[^_\\n]+_)|(\\[[^\\]]+\\]\\([^)]+\\))"
)

private fun inline(text: String, palette: AbyssPalette): AnnotatedString = buildAnnotatedString {
    var last = 0
    INLINE.findAll(text).forEach { m ->
        append(text.substring(last, m.range.first))
        val t = m.value
        when {
            t.startsWith("**") -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(t.removeSurrounding("**")) }
            t.startsWith("`") -> withStyle(SpanStyle(
                fontFamily = AbyssFonts.Code, fontSize = 13.5.sp,
                background = palette.surfaceVariant, color = palette.secondary,
            )) { append(" " + t.removeSurrounding("`") + " ") }
            t.startsWith("[") -> {
                val label = t.substringAfter('[').substringBefore(']')
                withStyle(SpanStyle(color = palette.primary, textDecoration = TextDecoration.Underline)) { append(label) }
            }
            else -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(t.substring(1, t.length - 1)) }
        }
        last = m.range.last + 1
    }
    if (last < text.length) append(text.substring(last))
}

/** Renders assistant markdown in the editorial reading style. */
@Composable
fun MarkdownText(text: String, palette: AbyssPalette, modifier: Modifier = Modifier) {
    val blocks = remember(text) { parseBlocks(text) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEach { b ->
            when (b) {
                is MdBlock.Para -> Text(inline(b.text, palette), color = palette.onSurface,
                    fontFamily = AbyssFonts.Reading, fontSize = 16.5.sp, lineHeight = 26.sp)
                is MdBlock.Heading -> Text(inline(b.text, palette), color = palette.onBackground,
                    fontFamily = AbyssFonts.Display, fontWeight = FontWeight.SemiBold,
                    fontSize = when (b.level) { 1 -> 23.sp; 2 -> 20.sp; else -> 18.sp },
                    modifier = Modifier.padding(top = 4.dp))
                is MdBlock.Bullet -> Row {
                    Text(b.marker, color = palette.primary, fontFamily = AbyssFonts.Reading,
                        fontSize = 16.5.sp, modifier = Modifier.width(24.dp))
                    Text(inline(b.text, palette), color = palette.onSurface,
                        fontFamily = AbyssFonts.Reading, fontSize = 16.5.sp, lineHeight = 26.sp)
                }
                is MdBlock.Quote -> Row(Modifier.fillMaxWidth()) {
                    Box(Modifier.width(3.dp).height(24.dp).background(palette.primary.copy(alpha = 0.6f)))
                    Spacer(Modifier.width(12.dp))
                    Text(inline(b.text, palette), color = palette.onSurface.copy(alpha = 0.8f),
                        fontFamily = AbyssFonts.Reading, fontStyle = FontStyle.Italic, fontSize = 16.sp)
                }
                is MdBlock.Code -> CodeBlock(b.lang, b.code, palette)
                MdBlock.Rule -> Box(Modifier.fillMaxWidth().height(1.dp).background(palette.outline))
            }
        }
    }
}

@Composable
private fun CodeBlock(lang: String, code: String, palette: AbyssPalette) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (palette.dark) palette.background.copy(alpha = 0.85f) else palette.surfaceVariant)
            .border(1.dp, palette.outline, RoundedCornerShape(12.dp)),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(lang.ifBlank { com.abyss.app.ui.i18n.LocalStrings.current.codeLabel }, color = palette.onSurface.copy(alpha = 0.55f),
                fontFamily = AbyssFonts.Code, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(if (copied) com.abyss.app.ui.i18n.LocalStrings.current.copied else com.abyss.app.ui.i18n.LocalStrings.current.copy, color = palette.primary, fontSize = 12.sp,
                modifier = Modifier.clickable {
                    clipboard.setText(AnnotatedString(code)); copied = true
                })
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.outline))
        Text(
            remember(code, lang, palette.id) { highlight(code, lang, palette) },
            color = palette.onSurface, fontFamily = AbyssFonts.Code,
            fontSize = 13.sp, lineHeight = 19.sp, softWrap = false,
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(14.dp),
        )
    }
}

// ==================================================================== syntax highlighting

private val KEYWORDS = setOf(
    // shared C-family / Kotlin / Java / JS / TS / Swift / Go / Rust / C#
    "fun", "val", "var", "class", "object", "interface", "data", "sealed", "enum", "when", "if", "else",
    "for", "while", "do", "return", "break", "continue", "in", "is", "as", "null", "true", "false",
    "this", "super", "import", "package", "private", "public", "protected", "internal", "override",
    "open", "abstract", "final", "static", "const", "let", "function", "new", "try", "catch", "finally",
    "throw", "throws", "async", "await", "suspend", "void", "int", "long", "float", "double", "boolean",
    "char", "string", "struct", "impl", "fn", "mut", "pub", "use", "mod", "match", "func", "go", "defer",
    "type", "typeof", "extends", "implements", "export", "default", "switch", "case", "yield", "lambda",
    // python / shell
    "def", "self", "None", "True", "False", "and", "or", "not", "from", "with", "pass", "elif", "except",
    "raise", "global", "nonlocal", "assert", "del", "print", "echo", "then", "fi", "done", "esac",
)

private val HASH_COMMENT_LANGS = setOf("py", "python", "sh", "bash", "shell", "zsh", "yaml", "yml", "toml", "rb", "ruby", "r")

private val CODE_TOKEN_C = Regex(
    """(//[^\n]*|/\*[\s\S]*?\*/)|("(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|`[^`]*`)|(\b\d+(?:\.\d+)?\b)|(\b[A-Za-z_]\w*\b)"""
)
private val CODE_TOKEN_HASH = Regex(
    """(#[^\n]*)|("(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|`[^`]*`)|(\b\d+(?:\.\d+)?\b)|(\b[A-Za-z_]\w*\b)"""
)

/** Lightweight, dependency-free highlighter: comments, strings, numbers, keywords, Types. */
private fun highlight(code: String, lang: String, palette: AbyssPalette): AnnotatedString {
    val dark = palette.dark
    val cKeyword = palette.primary
    val cString = if (dark) androidx.compose.ui.graphics.Color(0xFFA5C88F) else androidx.compose.ui.graphics.Color(0xFF4E7A35)
    val cNumber = if (dark) androidx.compose.ui.graphics.Color(0xFFE0B36A) else androidx.compose.ui.graphics.Color(0xFF9A6216)
    val cType = palette.secondary
    val cComment = palette.onSurface.copy(alpha = 0.45f)
    val re = if (lang.lowercase() in HASH_COMMENT_LANGS) CODE_TOKEN_HASH else CODE_TOKEN_C

    return buildAnnotatedString {
        var last = 0
        re.findAll(code).forEach { m ->
            if (m.range.first > last) append(code.substring(last, m.range.first))
            val g = m.groups
            when {
                g[1] != null -> withStyle(SpanStyle(color = cComment, fontStyle = FontStyle.Italic)) { append(m.value) }
                g[2] != null -> withStyle(SpanStyle(color = cString)) { append(m.value) }
                g[3] != null -> withStyle(SpanStyle(color = cNumber)) { append(m.value) }
                g[4] != null -> {
                    val w = m.value
                    when {
                        w in KEYWORDS -> withStyle(SpanStyle(color = cKeyword, fontWeight = FontWeight.Medium)) { append(w) }
                        w[0].isUpperCase() -> withStyle(SpanStyle(color = cType)) { append(w) }
                        else -> append(w)
                    }
                }
                else -> append(m.value)
            }
            last = m.range.last + 1
        }
        if (last < code.length) append(code.substring(last))
    }
}

