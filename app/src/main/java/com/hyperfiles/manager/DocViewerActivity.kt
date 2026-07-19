package com.hyperfiles.manager

import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.hyperfiles.manager.databinding.ActivityDocViewerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class DocViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDocViewerBinding
    private lateinit var file: File
    private var pdfRenderer: PdfRenderer? = null
    private var pfd: ParcelFileDescriptor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theming.applyActivityTheme(this)
        binding = ActivityDocViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val resolved = IntentInput.resolveToFile(this, intent)
        if (resolved == null) { finish(); return }
        file = resolved
        title = file.name

        when {
            FileTypes.isPdf(file.name) -> openPdf()
            FileTypes.isWeb(file.name) -> openWeb()
            else -> openText()
        }
    }

    private fun openPdf() {
        binding.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val readFile = if (file.canRead()) file
                    else if (RootShell.isRootAvailable()) RootShell.rootCopyToCache(file, cacheDir) ?: file else file
                    pfd = ParcelFileDescriptor.open(readFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    pdfRenderer = PdfRenderer(pfd!!)
                    true
                } catch (e: Exception) { false }
            }
            binding.progress.visibility = View.GONE
            val renderer = pdfRenderer
            if (ok && renderer != null) {
                val width = resources.displayMetrics.widthPixels - dp(20)
                binding.pdfList.visibility = View.VISIBLE
                binding.pdfList.layoutManager = LinearLayoutManager(this@DocViewerActivity)
                binding.pdfList.adapter = PdfPageAdapter(renderer, width)
                supportActionBar?.subtitle = "${renderer.pageCount} pages"
            } else {
                showText("Could not open PDF (it may be encrypted or damaged).\n\nUse \"Open with app\".")
            }
        }
    }

    private fun openWeb() {
        binding.webView.visibility = View.VISIBLE
        binding.webView.settings.apply {
            javaScriptEnabled = false
            allowFileAccess = true
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
        }
        binding.webView.loadUrl("file://${file.absolutePath}")
    }

    private fun openText() {
        binding.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val text = withContext(Dispatchers.IO) {
                val readFile = if (file.canRead()) file
                else if (RootShell.isRootAvailable()) RootShell.rootCopyToCache(file, cacheDir) ?: file else file
                DocExtractor.extract(readFile)
            }
            binding.progress.visibility = View.GONE
            showText(text)
            if (FileTypes.isOffice(file.name)) supportActionBar?.subtitle = "Text preview"
        }
    }

    private fun showText(text: String) {
        binding.docScroll.visibility = View.VISIBLE
        binding.docText.text = text
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.doc_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_open_with) OpenHelper.systemView(this, file)
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        super.onDestroy()
        try { pdfRenderer?.close() } catch (_: Exception) {}
        try { pfd?.close() } catch (_: Exception) {}
    }
}
