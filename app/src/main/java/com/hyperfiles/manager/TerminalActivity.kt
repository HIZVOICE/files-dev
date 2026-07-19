package com.hyperfiles.manager

import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import com.hyperfiles.manager.databinding.ActivityTerminalBinding
import java.io.File

class TerminalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTerminalBinding
    private var cwd: File = File("/sdcard").let { if (it.isDirectory) it else StorageUtil.primaryStorage() }
    private var running = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theming.applyActivityTheme(this)
        binding = ActivityTerminalBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }
        title = "Terminal"

        binding.switchRoot.isEnabled = RootShell.isRootAvailable()
        if (!binding.switchRoot.isEnabled) binding.switchRoot.text = "root (unavailable)"

        updateCwd()
        append("Files Dev shell — type commands below. 'cd' and 'clear' are supported.\n")

        binding.btnRun.setOnClickListener { submit() }
        binding.input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEND) {
                submit(); true
            } else false
        }
    }

    private fun submit() {
        if (running) return
        val cmd = binding.input.text.toString().trim()
        if (cmd.isEmpty()) return
        binding.input.setText("")
        val root = binding.switchRoot.isChecked
        append("\n${cwd.absolutePath} ${if (root) "#" else "$"} $cmd\n")

        when {
            cmd == "clear" -> { binding.output.text = ""; return }
            cmd == "cd" || cmd.startsWith("cd ") -> { changeDir(cmd.removePrefix("cd").trim()); return }
        }
        runCommand(cmd, root)
    }

    private fun changeDir(arg: String) {
        val target = when {
            arg.isEmpty() -> File("/sdcard")
            arg == ".." -> cwd.parentFile ?: cwd
            arg.startsWith("/") -> File(arg)
            else -> File(cwd, arg)
        }
        if (target.isDirectory) {
            cwd = try { target.canonicalFile } catch (e: Exception) { target }
            updateCwd()
        } else {
            append("cd: not a directory: $arg\n")
        }
    }

    private fun runCommand(cmd: String, root: Boolean) {
        running = true
        binding.btnRun.isEnabled = false
        Thread {
            try {
                val proc = RootShell.start(cmd, root, cwd)
                val reader = proc.inputStream.bufferedReader()
                val buf = CharArray(2048)
                while (true) {
                    val n = reader.read(buf)
                    if (n < 0) break
                    val chunk = String(buf, 0, n)
                    runOnUiThread { append(chunk) }
                }
                val code = proc.waitFor()
                runOnUiThread { append("\n[exit $code]\n") }
            } catch (e: Exception) {
                runOnUiThread { append("\n[error: ${e.message}]\n") }
            } finally {
                runOnUiThread { running = false; binding.btnRun.isEnabled = true }
            }
        }.start()
    }

    private fun append(s: String) {
        binding.output.append(s)
        binding.outputScroll.post { binding.outputScroll.fullScroll(android.view.View.FOCUS_DOWN) }
    }

    private fun updateCwd() {
        binding.cwd.text = cwd.absolutePath
    }
}
