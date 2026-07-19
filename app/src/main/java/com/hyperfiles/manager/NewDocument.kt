package com.hyperfiles.manager

import android.app.Activity
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import java.io.File
import java.io.FileOutputStream

/** Creates a new blank document (text formats or a blank A4/Letter PDF). */
object NewDocument {

    private val formats = listOf("txt", "md", "html", "csv", "json", "xml", "rtf", "pdf")

    fun dialog(activity: Activity, onCreated: (folderPath: String) -> Unit) {
        val labels = formats.map { ".$it" }.toTypedArray()
        AlertDialog.Builder(activity)
            .setTitle("New document")
            .setItems(labels) { _, i ->
                val ext = formats[i]
                if (ext == "pdf") pageSizeThenName(activity, onCreated)
                else nameThenCreate(activity, ext, null, onCreated)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pageSizeThenName(activity: Activity, onCreated: (String) -> Unit) {
        val sizes = arrayOf("A4 (210 × 297 mm)", "Letter (8.5 × 11 in)")
        AlertDialog.Builder(activity)
            .setTitle("Page size")
            .setItems(sizes) { _, which -> nameThenCreate(activity, "pdf", if (which == 0) "A4" else "Letter", onCreated) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun nameThenCreate(activity: Activity, ext: String, pageSize: String?, onCreated: (String) -> Unit) {
        val input = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText("document")
        }
        AlertDialog.Builder(activity)
            .setTitle("File name")
            .setMessage("Creates a .$ext file in Documents")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val base = input.text.toString().trim().ifEmpty { "document" }
                create(activity, base, ext, pageSize, onCreated)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun create(activity: Activity, base: String, ext: String, pageSize: String?, onCreated: (String) -> Unit) {
        val folder = File(StorageUtil.primaryStorage(), "Documents").apply { mkdirs() }
        val file = unique(folder, "$base.$ext")
        try {
            if (ext == "pdf") writeBlankPdf(file, pageSize == "Letter")
            else file.writeText(starter(ext))
            FileScanner.invalidate()
            Toast.makeText(activity, "Created ${file.name} in Documents", Toast.LENGTH_LONG).show()
            onCreated(folder.absolutePath)
        } catch (e: Exception) {
            Toast.makeText(activity, "Could not create: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun starter(ext: String): String = when (ext) {
        "html" -> "<!DOCTYPE html>\n<html>\n<head>\n<meta charset=\"utf-8\">\n<title>Document</title>\n</head>\n<body>\n\n</body>\n</html>\n"
        "json" -> "{\n  \n}\n"
        "xml" -> "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<root>\n</root>\n"
        "rtf" -> "{\\rtf1\\ansi\\deff0\n\n}\n"
        "csv" -> "column1,column2,column3\n"
        "md" -> "# Document\n\n"
        else -> ""
    }

    private fun writeBlankPdf(file: File, letter: Boolean) {
        // Points at 72 dpi: A4 = 595x842, Letter = 612x792
        val w = if (letter) 612 else 595
        val h = if (letter) 792 else 842
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, 1).create())
        page.canvas.drawColor(android.graphics.Color.WHITE)
        val p = Paint().apply { color = android.graphics.Color.LTGRAY; textSize = 10f }
        page.canvas.drawText(if (letter) "Letter" else "A4", 24f, 24f, p)
        doc.finishPage(page)
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
    }

    private fun unique(dir: File, name: String): File {
        var f = File(dir, name); var i = 1
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        while (f.exists()) { f = File(dir, "$stem ($i)$ext"); i++ }
        return f
    }
}
