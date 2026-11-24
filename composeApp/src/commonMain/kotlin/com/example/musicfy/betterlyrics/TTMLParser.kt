package com.example.musicfy.betterlyrics

import com.example.musicfy.util.SimpleXml
import com.example.musicfy.util.XmlNode

/**
 * Port of upstream `TTMLParser.kt`. JVM's `org.w3c.dom` is replaced by the
 * dependency-free [SimpleXml] DOM; parsing logic and output format are intact.
 */
object TTMLParser {

    data class ParsedLine(
        val text: String,
        val startTime: Double,
        val words: List<ParsedWord>,
        val agent: String? = null,
        val isBackground: Boolean = false,
        val backgroundLines: List<ParsedLine> = emptyList(),
    )

    data class ParsedWord(
        val text: String,
        val startTime: Double,
        val endTime: Double,
    )

    private data class SpanInfo(
        val text: String,
        val startTime: Double,
        val endTime: Double,
        val hasTrailingSpace: Boolean,
    )

    fun parseTTML(ttml: String): List<ParsedLine> {
        val lines = mutableListOf<ParsedLine>()
        try {
            val doc = SimpleXml.parse(ttml) ?: return emptyList()
            doc.descendants("p").forEach { pElement ->
                val begin = pElement.attr("begin")
                if (begin.isEmpty()) return@forEach

                val startTime = parseTime(begin)
                val spanInfos = mutableListOf<SpanInfo>()
                val backgroundLines = mutableListOf<ParsedLine>()
                val agent = pElement.attr("agent").ifEmpty { null }

                pElement.children.forEachIndexed { index, node ->
                    if (node.name == "#text") return@forEachIndexed
                    if (node.name.lowercase() != "span") return@forEachIndexed

                    when (node.attr("role")) {
                        "x-bg" -> {
                            parseBackgroundSpan(node, startTime)?.let(backgroundLines::add)
                        }
                        "x-translation", "x-roman" -> Unit
                        else -> {
                            val wordBegin = node.attr("begin")
                            val wordEnd = node.attr("end")
                            val wordText = node.textContent().trim()
                            if (wordText.isNotEmpty() && wordBegin.isNotEmpty() && wordEnd.isNotEmpty()) {
                                val nextSibling = pElement.children.getOrNull(index + 1)
                                spanInfos.add(
                                    SpanInfo(
                                        text = wordText,
                                        startTime = parseTime(wordBegin),
                                        endTime = parseTime(wordEnd),
                                        hasTrailingSpace = isWordBoundary(nextSibling),
                                    ),
                                )
                            }
                        }
                    }
                }

                val words = mergeSpansIntoWords(spanInfos)
                val lineText = words.joinToString(" ") { it.text }
                val finalText = if (lineText.isEmpty()) getDirectTextContent(pElement).trim() else lineText

                if (finalText.isNotEmpty()) {
                    lines.add(
                        ParsedLine(
                            text = finalText,
                            startTime = startTime,
                            words = words,
                            agent = agent,
                            isBackground = false,
                            backgroundLines = backgroundLines,
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            return emptyList()
        }
        return lines
    }

    private fun parseBackgroundSpan(span: XmlNode, parentStartTime: Double): ParsedLine? {
        val bgBegin = span.attr("begin")
        val bgStartTime = if (bgBegin.isNotEmpty()) parseTime(bgBegin) else parentStartTime

        val spanInfos = mutableListOf<SpanInfo>()
        span.children.forEachIndexed { index, node ->
            if (node.name == "#text" || node.name.lowercase() != "span") return@forEachIndexed

            when (node.attr("role")) {
                "x-translation", "x-roman" -> return@forEachIndexed
            }

            val wordBegin = node.attr("begin")
            val wordEnd = node.attr("end")
            val wordText = node.textContent().trim()
            if (wordText.isNotEmpty() && wordBegin.isNotEmpty() && wordEnd.isNotEmpty()) {
                val nextSibling = span.children.getOrNull(index + 1)
                val hasTrailingSpace = nextSibling?.name == "#text" &&
                    nextSibling.textValue.contains(Regex("\\s"))
                spanInfos.add(
                    SpanInfo(
                        text = wordText,
                        startTime = parseTime(wordBegin),
                        endTime = parseTime(wordEnd),
                        hasTrailingSpace = hasTrailingSpace,
                    ),
                )
            }
        }

        val words = mergeSpansIntoWords(spanInfos)
        val lineText = words.joinToString(" ") { it.text }
        val finalText = if (lineText.isEmpty()) getDirectTextContent(span).trim() else lineText

        return if (finalText.isNotEmpty()) {
            ParsedLine(
                text = finalText,
                startTime = bgStartTime,
                words = words,
                agent = null,
                isBackground = true,
                backgroundLines = emptyList(),
            )
        } else null
    }

    private fun getDirectTextContent(element: XmlNode): String = buildString {
        element.children.forEach { node ->
            if (node.name == "#text") {
                append(node.textValue)
            } else {
                val role = node.attr("role")
                if (role != "x-bg" && role != "x-translation" && role != "x-roman" &&
                    node.name.lowercase() == "span"
                ) {
                    append(node.textContent())
                }
            }
        }
    }

    private fun isWordBoundary(node: XmlNode?): Boolean {
        if (node == null || node.name != "#text") return false
        val between = node.textValue
        if (between.isEmpty()) return false
        if (between.contains('\n') || between.contains('\r')) return false
        return between.any { it == ' ' || it == '\t' || it == '\u00a0' }
    }

    private fun mergeSpansIntoWords(spanInfos: List<SpanInfo>): List<ParsedWord> {
        if (spanInfos.isEmpty()) return emptyList()

        val words = mutableListOf<ParsedWord>()
        var currentText = StringBuilder()
        var currentStartTime = spanInfos[0].startTime
        var currentEndTime = spanInfos[0].endTime

        spanInfos.forEachIndexed { index, span ->
            if (index == 0) {
                currentText.append(span.text)
                currentStartTime = span.startTime
                currentEndTime = span.endTime
            } else {
                val prevSpan = spanInfos[index - 1]
                if (prevSpan.hasTrailingSpace) {
                    if (currentText.isNotEmpty()) {
                        words.add(
                            ParsedWord(
                                text = currentText.toString().trim(),
                                startTime = currentStartTime,
                                endTime = currentEndTime,
                            ),
                        )
                    }
                    currentText = StringBuilder(span.text)
                    currentStartTime = span.startTime
                    currentEndTime = span.endTime
                } else {
                    currentText.append(span.text)
                    currentEndTime = span.endTime
                }
            }
        }

        if (currentText.isNotEmpty()) {
            words.add(
                ParsedWord(
                    text = currentText.toString().trim(),
                    startTime = currentStartTime,
                    endTime = currentEndTime,
                ),
            )
        }

        return words
    }

    fun toLRC(lines: List<ParsedLine>): String = buildString {
        lines.forEach { line ->
            val timeMs = (line.startTime * 1000).toLong()
            val minutes = timeMs / 60000
            val seconds = (timeMs % 60000) / 1000
            val centiseconds = (timeMs % 1000) / 10

            val agentPrefix = if (!line.agent.isNullOrEmpty()) "{agent:${line.agent}}" else ""
            appendLine("[${pad2(minutes)}:${pad2(seconds)}.${pad2(centiseconds)}]$agentPrefix${line.text}")

            if (line.words.isNotEmpty()) {
                val wordsData = line.words.joinToString("|") { word ->
                    "${word.text}:${word.startTime}:${word.endTime}"
                }
                appendLine("<$wordsData>")
            }

            line.backgroundLines.forEach { bgLine ->
                val bgTimeMs = (bgLine.startTime * 1000).toLong()
                val bgMinutes = bgTimeMs / 60000
                val bgSeconds = (bgTimeMs % 60000) / 1000
                val bgCentiseconds = (bgTimeMs % 1000) / 10

                appendLine("[${pad2(bgMinutes)}:${pad2(bgSeconds)}.${pad2(bgCentiseconds)}]{bg}${bgLine.text}")

                if (bgLine.words.isNotEmpty()) {
                    val bgWordsData = bgLine.words.joinToString("|") { word ->
                        "${word.text}:${word.startTime}:${word.endTime}"
                    }
                    appendLine("<$bgWordsData>")
                }
            }
        }
    }

    private fun pad2(value: Long): String = value.toString().padStart(2, '0')

    private fun parseTime(timeStr: String): Double = try {
        when {
            timeStr.contains(":") -> {
                val parts = timeStr.split(":")
                when (parts.size) {
                    2 -> parts[0].toDouble() * 60 + parts[1].toDouble()
                    3 -> parts[0].toDouble() * 3600 + parts[1].toDouble() * 60 + parts[2].toDouble()
                    else -> timeStr.toDoubleOrNull() ?: 0.0
                }
            }
            else -> timeStr.toDoubleOrNull() ?: 0.0
        }
    } catch (e: Exception) {
        0.0
    }
}
