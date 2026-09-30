package com.treasurenova.bep20manager.logic

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

object AccountImport {
    data class Draft(val username: String, val password: String)

    fun previewUsernames(accounts: List<Draft>): List<String> = accounts.map { it.username }

    fun parse(fileName: String, bytes: ByteArray): List<Draft> {
        val lower = fileName.lowercase()
        val rows = when {
            lower.endsWith(".xlsx") -> parseXlsx(bytes)
            lower.endsWith(".txt") -> parseTxt(bytes.toString(Charsets.UTF_8))
            else -> parseCsv(bytes.toString(Charsets.UTF_8))
        }
        return rows.mapNotNull { cols ->
            val user = cols.getOrNull(0)?.trim().orEmpty()
            val pass = cols.getOrNull(1)?.trim().orEmpty()
            if (user.isEmpty() || pass.isEmpty()) null
            else if (user.equals("username", true)) null
            else Draft(user, pass)
        }
    }

    private fun parseTxt(text: String): List<List<String>> =
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { line ->
                when {
                    line.contains(",") -> splitCsvLine(line)
                    line.contains(":") -> line.split(":", limit = 2)
                    else -> line.split("\\s+".toRegex(), limit = 2)
                }
            }
            .toList()

    fun parseCsv(text: String): List<List<String>> =
        text.lineSequence()
            .map { it.trimEnd() }
            .filter { it.isNotBlank() }
            .map { splitCsvLine(it) }
            .toList()

    private fun splitCsvLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> {
                    cur.append('"')
                    i++
                }
                ch == '"' -> quoted = !quoted
                ch == ',' && !quoted -> {
                    out += cur.toString()
                    cur.clear()
                }
                else -> cur.append(ch)
            }
            i++
        }
        out += cur.toString()
        return out
    }

    fun parseXlsx(bytes: ByteArray): List<List<String>> {
        val files = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) files[entry.name] = zip.readBytes()
                entry = zip.nextEntry
            }
        }
        val shared = readSharedStrings(files["xl/sharedStrings.xml"])
        val sheetName = files.keys.firstOrNull { it.startsWith("xl/worksheets/sheet") }
            ?: return emptyList()
        return readSheet(files.getValue(sheetName), shared)
    }

    private fun readSharedStrings(bytes: ByteArray?): List<String> {
        if (bytes == null) return emptyList()
        val doc = xml(bytes)
        val out = mutableListOf<String>()
        val items = doc.getElementsByTagName("si")
        for (i in 0 until items.length) {
            val item = items.item(i) as Element
            val texts = item.getElementsByTagName("t")
            val sb = StringBuilder()
            for (t in 0 until texts.length) sb.append(texts.item(t).textContent)
            out += sb.toString()
        }
        return out
    }

    private fun readSheet(bytes: ByteArray, shared: List<String>): List<List<String>> {
        val doc = xml(bytes)
        val rows = doc.getElementsByTagName("row")
        val table = mutableListOf<List<String>>()
        for (r in 0 until rows.length) {
            val row = rows.item(r) as Element
            val cells = mutableMapOf<Int, String>()
            var child = row.firstChild
            while (child != null) {
                if (child is Element && child.tagName.substringAfter(':') == "c") {
                    val ref = child.getAttribute("r")
                    val col = columnIndex(ref)
                    cells[col] = cellText(child, shared)
                }
                child = child.nextSibling
            }
            if (cells.isEmpty()) continue
            val width = (cells.keys.maxOrNull() ?: 0) + 1
            table += List(width) { cells[it].orEmpty() }
        }
        return table
    }

    private fun cellText(cell: Element, shared: List<String>): String {
        val type = cell.getAttribute("t")
        if (type == "inlineStr") {
            val texts = cell.getElementsByTagName("t")
            val sb = StringBuilder()
            for (i in 0 until texts.length) sb.append(texts.item(i).textContent)
            return sb.toString()
        }
        val value = directChildText(cell, "v")
        if (type == "s") {
            val index = value.toIntOrNull() ?: return ""
            return shared.getOrElse(index) { "" }
        }
        return value
    }

    private fun directChildText(cell: Element, name: String): String {
        var child = cell.firstChild
        while (child != null) {
            if (child is Element && child.tagName.substringAfter(':') == name) {
                return child.textContent ?: ""
            }
            child = child.nextSibling
        }
        return ""
    }

    private fun columnIndex(ref: String): Int {
        var n = 0
        for (ch in ref) {
            if (!ch.isLetter()) break
            n = n * 26 + (ch.uppercaseChar() - 'A' + 1)
        }
        return (n - 1).coerceAtLeast(0)
    }

    private fun xml(bytes: ByteArray) = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        .parse(ByteArrayInputStream(bytes))
}
