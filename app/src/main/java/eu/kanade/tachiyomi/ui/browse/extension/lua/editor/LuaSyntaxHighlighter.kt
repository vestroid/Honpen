package eu.kanade.tachiyomi.ui.browse.extension.lua.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle

class LuaSyntaxHighlighter : VisualTransformation {

    // GitHub-ish colors
    private val keywordColor = Color(0xFFD73A49)
    private val stringColor = Color(0xFF032F62)
    private val commentColor = Color(0xFF6A737D)
    private val functionColor = Color(0xFF6F42C1)
    private val numericColor = Color(0xFF005CC5)

    private val keywords = setOf(
        "and", "break", "do", "else", "elseif", "end", "false", "for", "function",
        "if", "in", "local", "nil", "not", "or", "repeat", "return", "then",
        "true", "until", "while"
    )

    override fun filter(text: AnnotatedString): TransformedText {
        return TransformedText(highlightLua(text.text), OffsetMapping.Identity)
    }

    private fun highlightLua(code: String): AnnotatedString {
        return buildAnnotatedString {
            var i = 0
            while (i < code.length) {
                val c = code[i]
                
                when {
                    // Comments
                    code.startsWith("--", i) -> {
                        val end = code.indexOf('\n', i).takeIf { it != -1 } ?: code.length
                        withStyle(SpanStyle(color = commentColor)) {
                            append(code.substring(i, end))
                        }
                        i = end
                    }
                    // Strings
                    c == '"' || c == '\'' -> {
                        val quote = c
                        var end = i + 1
                        while (end < code.length && code[end] != quote) {
                            if (code[end] == '\\') end++
                            end++
                        }
                        if (end < code.length) end++
                        withStyle(SpanStyle(color = stringColor)) {
                            append(code.substring(i, end))
                        }
                        i = end
                    }
                    // Multi-line strings [[ ]]
                    code.startsWith("[[", i) -> {
                        val end = code.indexOf("]]", i + 2).takeIf { it != -1 }?.plus(2) ?: code.length
                        withStyle(SpanStyle(color = stringColor)) {
                            append(code.substring(i, end))
                        }
                        i = end
                    }
                    // Numbers
                    c.isDigit() -> {
                        var end = i
                        while (end < code.length && (code[end].isDigit() || code[end] == '.')) end++
                        withStyle(SpanStyle(color = numericColor)) {
                            append(code.substring(i, end))
                        }
                        i = end
                    }
                    // Identifiers (Keywords and Functions)
                    c.isLetter() || c == '_' -> {
                        var end = i
                        while (end < code.length && (code[end].isLetterOrDigit() || code[end] == '_')) end++
                        val word = code.substring(i, end)
                        
                        val style = when {
                            keywords.contains(word) -> SpanStyle(color = keywordColor, fontWeight = FontWeight.Bold)
                            // Basic heuristic for function calls: followed by (
                            end < code.length && code[end] == '(' -> SpanStyle(color = functionColor)
                            else -> SpanStyle()
                        }
                        
                        withStyle(style) {
                            append(word)
                        }
                        i = end
                    }
                    else -> {
                        append(c)
                        i++
                    }
                }
            }
        }
    }
}
