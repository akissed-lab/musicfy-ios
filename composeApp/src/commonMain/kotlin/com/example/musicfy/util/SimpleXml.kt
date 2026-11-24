package com.example.musicfy.util

/**
 * Minimal dependency-free XML DOM used by the TTML lyrics parser.
 *
 * Android used `javax.xml.parsers.DocumentBuilderFactory`; that API does not
 * exist in Kotlin/Native. This implementation covers the subset TTML uses:
 * elements, attributes (including prefixed names), ordered text nodes,
 * entities, comments, CDATA, processing instructions and DOCTYPE declarations.
 */
class XmlNode(
    val name: String,
    val attributes: Map<String, String>,
) {
    /** Element children and `#text` children, in document order. */
    val children: MutableList<XmlNode> = mutableListOf()

    /** Raw value of a `#text` node. */
    var textValue: String = ""

    /** Direct text of an element, or the value of a `#text` node. */
    val text: String
        get() = if (name == "#text") {
            textValue
        } else {
            children.filter { it.name == "#text" }.joinToString("") { it.textValue }
        }

    /** Returns an attribute by local name, ignoring any namespace prefix. */
    fun attr(localName: String): String {
        attributes[localName]?.let { return it }
        for ((key, value) in attributes) {
            if (key.substringAfterLast(':') == localName) return value
        }
        return ""
    }

    fun descendants(name: String): List<XmlNode> {
        val result = mutableListOf<XmlNode>()
        fun visit(node: XmlNode) {
            node.children.forEach { child ->
                if (child.name.equals(name, ignoreCase = true)) result.add(child)
                visit(child)
            }
        }
        visit(this)
        return result
    }

    /** Text of this element and its children, skipping [excluded] element names. */
    fun textContent(excluded: Set<String> = emptySet()): String = buildString {
        children.forEach { child ->
            if (child.name == "#text") {
                append(child.textValue)
            } else if (child.name.lowercase() !in excluded) {
                append(child.textContent(excluded))
            }
        }
    }
}

object SimpleXml {
    fun parse(input: String): XmlNode? = Parser(input).parseDocument()

    private class Parser(private val s: String) {
        private var pos = 0

        fun parseDocument(): XmlNode? {
            var root: XmlNode? = null
            while (pos < s.length) {
                skipWhitespace()
                if (pos >= s.length) break
                if (s[pos] != '<') {
                    skipUntil('<')
                    continue
                }
                if (s.startsWith("<?", pos)) {
                    skipUntil("?>")
                    continue
                }
                if (s.startsWith("<!--", pos)) {
                    skipUntil("-->")
                    continue
                }
                if (s.startsWith("<!", pos)) {
                    skipUntil('>')
                    continue
                }
                val node = parseElement() ?: break
                if (root == null) root = node
            }
            return root
        }

        private fun parseElement(): XmlNode? {
            if (pos >= s.length || s[pos] != '<') return null
            pos++ // '<'
            val name = readName()
            if (name.isEmpty()) return null
            val attributes = mutableMapOf<String, String>()
            while (pos < s.length) {
                skipWhitespace()
                if (s.startsWith("/>", pos)) {
                    pos += 2
                    return XmlNode(name, attributes)
                }
                if (s[pos] == '>') {
                    pos++
                    break
                }
                val attrName = readName()
                if (attrName.isEmpty()) {
                    pos++
                    continue
                }
                skipWhitespace()
                val value = if (pos < s.length && s[pos] == '=') {
                    pos++
                    skipWhitespace()
                    readAttributeValue()
                } else ""
                attributes[attrName] = value
            }

            val node = XmlNode(name, attributes)
            val textBuffer = StringBuilder()
            while (pos < s.length) {
                if (s.startsWith("</", pos)) {
                    pos += 2
                    readName()
                    skipUntil('>')
                    break
                }
                if (s.startsWith("<!--", pos)) {
                    skipUntil("-->")
                    continue
                }
                if (s.startsWith("<![CDATA[", pos)) {
                    pos += 9
                    val end = s.indexOf("]]>", pos)
                    if (end < 0) break
                    appendText(node, s.substring(pos, end))
                    pos = end + 3
                    continue
                }
                if (s.startsWith("<?", pos)) {
                    skipUntil("?>")
                    continue
                }
                if (s[pos] == '<') {
                    appendText(node, textBuffer.toString())
                    textBuffer.clear()
                    val child = parseElement() ?: break
                    node.children.add(child)
                    continue
                }
                val next = s.indexOf('<', pos)
                if (next < 0) {
                    textBuffer.append(s, pos, s.length)
                    pos = s.length
                } else {
                    textBuffer.append(s, pos, next)
                    pos = next
                }
            }
            appendText(node, textBuffer.toString())
            return node
        }

        private fun appendText(node: XmlNode, raw: String) {
            if (raw.isEmpty()) return
            val decoded = decodeEntities(raw)
            val last = node.children.lastOrNull()
            if (last?.name == "#text") last.textValue += decoded
            else node.children.add(XmlNode("#text", emptyMap()).also { it.textValue = decoded })
        }

        private fun readName(): String {
            val start = pos
            while (pos < s.length && !s[pos].isWhitespace() && s[pos] !in "<>/=\"'") pos++
            return s.substring(start, pos)
        }

        private fun readAttributeValue(): String {
            if (pos >= s.length) return ""
            val quote = s[pos]
            if (quote != '"' && quote != '\'') {
                val start = pos
                while (pos < s.length && !s[pos].isWhitespace() && s[pos] != '>') pos++
                return decodeEntities(s.substring(start, pos))
            }
            pos++
            val start = pos
            while (pos < s.length && s[pos] != quote) pos++
            val value = s.substring(start, pos)
            if (pos < s.length) pos++
            return decodeEntities(value)
        }

        private fun skipWhitespace() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }

        private fun skipUntil(marker: String) {
            val end = s.indexOf(marker, pos)
            pos = if (end < 0) s.length else end + marker.length
        }

        private fun skipUntil(char: Char) {
            val end = s.indexOf(char, pos)
            pos = if (end < 0) s.length else end + 1
        }

        private fun decodeEntities(value: String): String = when {
            "&amp;" in value || "&lt;" in value || "&gt;" in value ||
                "&quot;" in value || "&apos;" in value -> value
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&amp;", "&")
            else -> value
        }
    }
}
