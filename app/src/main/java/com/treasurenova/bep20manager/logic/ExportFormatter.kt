package com.treasurenova.bep20manager.logic

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ExportFormatter {
    val headers = listOf("Username", "BEP20 Address", "Status", "Error")

    init {
        check(headers.none { it.contains("password", ignoreCase = true) })
    }

    fun csv(rows: List<AddressRow>): String {
        val lines = mutableListOf(headers.joinToString(",") { csvCell(it) })
        rows.forEach { row ->
            lines += listOf(row.username, row.bep20Address, row.status, row.error)
                .joinToString(",") { csvCell(it) }
        }
        return lines.joinToString("\n")
    }

    fun txt(rows: List<AddressRow>): String {
        val lines = mutableListOf(headers.joinToString(" | "))
        rows.forEach { row ->
            lines += listOf(row.username, row.bep20Address, row.status, row.error)
                .joinToString(" | ")
        }
        return lines.joinToString("\n")
    }

    fun xlsx(rows: List<AddressRow>): ByteArray {
        val sheet = buildSheet(rows)
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun put(name: String, body: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(body.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put("[Content_Types].xml", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                </Types>
            """.trimIndent())
            put("_rels/.rels", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                </Relationships>
            """.trimIndent())
            put("xl/workbook.xml", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets><sheet name="Results" sheetId="1" r:id="rId1"/></sheets>
                </workbook>
            """.trimIndent())
            put("xl/_rels/workbook.xml.rels", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                </Relationships>
            """.trimIndent())
            put("xl/worksheets/sheet1.xml", sheet)
        }
        return out.toByteArray()
    }

    private fun buildSheet(rows: List<AddressRow>): String {
        val all = listOf(headers) + rows.map { listOf(it.username, it.bep20Address, it.status, it.error) }
        val body = all.mapIndexed { index, cols ->
            val cells = cols.mapIndexed { col, value ->
                val ref = "${('A' + col)}${index + 1}"
                "<c r=\"$ref\" t=\"inlineStr\"><is><t>${xml(value)}</t></is></c>"
            }.joinToString("")
            "<row r=\"${index + 1}\">$cells</row>"
        }.joinToString("")
        return """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <sheetData>$body</sheetData>
            </worksheet>
        """.trimIndent()
    }

    private fun csvCell(value: String): String {
        val needs = value.contains(',') || value.contains('"') || value.contains('\n')
        val escaped = value.replace("\"", "\"\"")
        return if (needs) "\"$escaped\"" else escaped
    }

    private fun xml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}

object BatchEngine {
    class Halted(message: String) : RuntimeException(message)

    suspend fun run(
        accounts: List<AccountInput>,
        confirmed: Boolean,
        shouldStop: () -> Boolean,
        onAccount: suspend (index: Int, account: AccountInput) -> AddressRow,
    ): List<AddressRow> {
        if (!confirmed) throw Halted("Batch was not confirmed")
        val rows = mutableListOf<AddressRow>()
        for ((index, account) in accounts.withIndex()) {
            if (shouldStop()) {
                rows += AddressRow(account.username, "", "Stopped", "Stopped by user")
                break
            }
            val row = onAccount(index, account)
            rows += row
            if (row.status == "Blocked" || row.haltBatch) break
        }
        return rows
    }
}
