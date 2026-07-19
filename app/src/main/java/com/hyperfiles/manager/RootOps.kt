package com.hyperfiles.manager

import android.app.Activity
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import java.io.File

/** Root-powered operations for ROM development (via su). */
object RootOps {

    private fun run(activity: Activity, cmd: String, onDone: () -> Unit) {
        Thread {
            val r = RootShell.sh(cmd, root = true)
            activity.runOnUiThread {
                val msg = if (r.ok) "Done" else "Failed: ${r.stderr.ifBlank { "exit ${r.exitCode}" }.take(140)}"
                Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()
                if (r.ok) { FileScanner.invalidate(); onDone() }
            }
        }.start()
    }

    fun chmodDialog(activity: Activity, file: File, onDone: () -> Unit) {
        val input = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(StorageUtil.permOctal(file).ifEmpty { "644" })
        }
        AlertDialog.Builder(activity)
            .setTitle("chmod · ${file.name}")
            .setMessage("Enter octal mode (e.g. 644, 755, 777)")
            .setView(input)
            .setPositiveButton("Apply") { _, _ ->
                val mode = input.text.toString().trim()
                if (mode.isNotEmpty()) run(activity, "chmod $mode '${file.absolutePath}'", onDone)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun chownDialog(activity: Activity, file: File, onDone: () -> Unit) {
        val input = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText("root:root")
        }
        AlertDialog.Builder(activity)
            .setTitle("chown · ${file.name}")
            .setMessage("Enter owner[:group] (e.g. root:root, 1000:1000, system:system)")
            .setView(input)
            .setPositiveButton("Apply") { _, _ ->
                val owner = input.text.toString().trim()
                if (owner.isNotEmpty()) run(activity, "chown $owner '${file.absolutePath}'", onDone)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun symlinkDialog(activity: Activity, target: File, onDone: () -> Unit) {
        val input = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText("${target.name}.link")
        }
        AlertDialog.Builder(activity)
            .setTitle("Create symlink")
            .setMessage("Link name (created in the same folder, pointing to ${target.name})")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val link = File(target.parentFile, name).absolutePath
                    run(activity, "ln -s '${target.absolutePath}' '$link'", onDone)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun mountDialog(activity: Activity) {
        if (!RootShell.suBinaryPresent()) {
            Toast.makeText(activity, "Root (su) not available on this device", Toast.LENGTH_SHORT).show()
            return
        }
        val partitions = arrayOf("/", "/system", "/vendor", "/product", "/system_ext", "/odm")
        AlertDialog.Builder(activity)
            .setTitle("Remount read-write")
            .setItems(partitions) { _, i ->
                val mp = partitions[i]
                run(activity, "mount -o rw,remount $mp || mount -o remount,rw $mp", {})
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
