package com.hyperfiles.manager

import java.io.File
import java.util.zip.ZipFile

/**
 * Extracts readable text from Office / OpenDocument / RTF files without heavy
 * libraries, by reading the packaged XML. Produces a best-effort text preview;
 * full-fidelity rendering is offered via "Open with app".
 */
object DocExtractor {

    fun extract(file: File): String {
        return try {
            when (FileTypes.ext(file.name)) {
                "docx" -> stripXml(readEntry(file, "word/document.xml"), "</w:p>", "</w:tab>")
                "odt", "odp", "ods" -> stripXml(readEntry(file, "content.xml"), "</text:p>", "</text:h>")
                "xlsx" -> xlsx(file)
                "pptx" -> pptx(file)
                "rtf" -> stripRtf(readText(file))
                else -> readText(file)
            }.ifBlank { "(No extractable text — use \"Open with app\" for full formatting.)" }
        } catch (e: Exception) {
            "Could not read document: ${e.message}\n\nTry \"Open with app\"."
        }
    }

    private fun readText(file: File): String {
        val out = java.io.ByteArrayOutputStream()
        file.inputStream().use { ins ->
            val buf = ByteArray(8192); var total = 0; val max = 3 * 1024 * 1024
            while (total < max) { val r = ins.read(buf); if (r < 0) break; out.write(buf, 0, r); total += r }
        }
        return String(out.toByteArray())
    }

    private fun readEntry(file: File, entry: String): String {
        ZipFile(file).use { zf ->
            val e = zf.getEntry(entry) ?: return ""
            zf.getInputStream(e).use { return it.readBytes().toString(Charsets.UTF_8) }
        }
    }

    /** Insert newlines at paragraph boundaries, then strip all tags + unescape. */
    private fun stripXml(xml: String, vararg breakTags: String): String {
        if (xml.isEmpty()) return ""
        var s = xml
        for (t in breakTags) s = s.replace(t, "$t\n")
        s = s.replace(Regex("<[^>]+>"), "")
        return unescape(s).lines().joinToString("\n") { it.trim() }.replace(Regex("\n{3,}"), "\n\n").trim()
    }

    private fun xlsx(file: File): String {
        val shared = ArrayList<String>()
        try {
            val ss = readEntry(file, "xl/sharedStrings.xml")
            Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL).findAll(ss).forEach {
                shared.add(unescape(it.groupValues[1]))
            }
        } catch (_: Exception) {}
        val sb = StringBuilder()
        var sheetIdx = 1
        while (sheetIdx <= 12) {
            val sheet = try { readEntry(file, "xl/worksheets/sheet$sheetIdx.xml") } catch (e: Exception) { "" }
            if (sheet.isEmpty()) { if (sheetIdx == 1) {} ; sheetIdx++; if (sheetIdx > 3 && sb.isNotEmpty()) break else continue }
            sb.append("── Sheet $sheetIdx ──\n")
            Regex("<row[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL).findAll(sheet).forEach { row ->
                val cells = ArrayList<String>()
                Regex("<c[^>]*?(t=\"([^\"]*)\")?[^>]*>(.*?)</c>", RegexOption.DOT_MATCHES_ALL)
                    .findAll(row.groupValues[1]).forEach { c ->
                        val type = c.groupValues[2]
                        val vRaw = Regex("<v[^>]*>(.*?)</v>", RegexOption.DOT_MATCHES_ALL)
                            .find(c.groupValues[3])?.groupValues?.get(1) ?: ""
                        val inline = Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL)
                            .find(c.groupValues[3])?.groupValues?.get(1)
                        val value = when {
                            inline != null -> unescape(inline)
                            type == "s" -> shared.getOrNull(vRaw.toIntOrNull() ?: -1) ?: ""
                            else -> vRaw
                        }
                        cells.add(value)
                    }
                sb.append(cells.joinToString("\t")).append("\n")
            }
            sb.append("\n")
            sheetIdx++
        }
        return sb.toString().trim()
    }

    private fun pptx(file: File): String {
        val sb = StringBuilder()
        var i = 1
        while (i <= 200) {
            val slide = try { readEntry(file, "ppt/slides/slide$i.xml") } catch (e: Exception) { "" }
            if (slide.isEmpty()) break
            sb.append("── Slide $i ──\n")
            Regex("<a:t>(.*?)</a:t>", RegexOption.DOT_MATCHES_ALL).findAll(slide).forEach {
                sb.append(unescape(it.groupValues[1])).append("\n")
            }
            sb.append("\n")
            i++
        }
        return sb.toString().trim()
    }

    private fun stripRtf(rtf: String): String {
        var s = rtf
        s = s.replace(Regex("\\\\[a-zA-Z]+-?\\d* ?"), "")
        s = s.replace("{", "").replace("}", "")
        s = s.replace(Regex("\\\\'[0-9a-fA-F]{2}"), "")
        return s.trim()
    }

    private fun unescape(s: String): String = s
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'").replace("&#10;", "\n").replace("&#9;", "\t")
}
