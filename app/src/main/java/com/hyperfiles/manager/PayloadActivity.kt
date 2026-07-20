package com.hyperfiles.manager

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hyperfiles.manager.databinding.ActivityArchiveBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PayloadActivity : AppCompatActivity() {

    private lateinit var binding: ActivityArchiveBinding
    private lateinit var payload: File       // original (may be in a restricted path)
    private var readablePayload: File? = null
    private var info: PayloadDumper.Info? = null
    private val adapter = PartAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theming.applyActivityTheme(this)
        binding = ActivityArchiveBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val resolved = IntentInput.resolveToFile(this, intent)
        if (resolved == null) { finish(); return }
        payload = resolved
        title = "ROM payload"
        supportActionBar?.subtitle = payload.name

        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter
        binding.btnExtractAll.text = "Extract all"
        binding.btnExtractAll.setOnClickListener { extractAll() }

        parse()
    }

    private suspend fun readable(): File? {
        readablePayload?.let { return it }
        return withContext(Dispatchers.IO) { Readable.resolve(payload) }.also { readablePayload = it }
    }

    /** One writable output dir for the whole session (next to the payload if possible, else Download/FilesDev). */
    private val outputDir: File by lazy {
        val parent = payload.parentFile
        val base = if (parent != null && parent.isDirectory && parent.canWrite() && !SafHelper.isRestricted(parent))
            parent else File(StorageUtil.primaryStorage(), "Download/FilesDev")
        File(base, "payload_extracted").apply { mkdirs() }
    }

    private fun parse() {
        binding.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val src = readable()
            if (src == null) {
                binding.progress.visibility = View.GONE
                binding.empty.text = Readable.reason()
                binding.empty.visibility = View.VISIBLE
                return@launch
            }
            val result = withContext(Dispatchers.IO) { runCatching { PayloadDumper.parse(src) } }
            binding.progress.visibility = View.GONE
            result.onSuccess {
                info = it
                adapter.submit(it.partitions)
                binding.summary.text = "${it.partitions.size} partitions · block ${it.blockSize}"
                binding.empty.text = "No partitions found"
                binding.empty.visibility = if (it.partitions.isEmpty()) View.VISIBLE else View.GONE
            }.onFailure { e ->
                binding.empty.text = "Not a valid payload.bin\n${e.message ?: e.javaClass.simpleName}"
                binding.empty.visibility = View.VISIBLE
            }
        }
    }

    private fun extractOne(p: PayloadDumper.Partition) {
        val i = info ?: return
        val src = readablePayload ?: return
        if (!p.supported) {
            Toast.makeText(this, "${p.name}: incremental payload (needs base image)", Toast.LENGTH_LONG).show()
            return
        }
        binding.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { PayloadDumper.extract(src, i, p, File(outputDir, "${p.name}.img")) {} }
            }
            binding.progress.visibility = View.GONE
            result.onSuccess {
                Toast.makeText(this@PayloadActivity, "Extracted ${p.name}.img to ${outputDir.absolutePath}", Toast.LENGTH_LONG).show()
                FileScanner.invalidate()
            }.onFailure { e ->
                Toast.makeText(this@PayloadActivity, "Failed ${p.name}: ${e.message ?: e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun extractAll() {
        val i = info ?: return
        val src = readablePayload ?: return
        val supported = i.partitions.filter { it.supported }
        if (supported.isEmpty()) {
            Toast.makeText(this, "No full-OTA partitions to extract (incremental payload)", Toast.LENGTH_LONG).show()
            return
        }
        binding.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val fails = withContext(Dispatchers.IO) {
                var f = 0
                for (p in supported) {
                    runCatching { PayloadDumper.extract(src, i, p, File(outputDir, "${p.name}.img")) {} }
                        .onFailure { f++ }
                }
                f
            }
            binding.progress.visibility = View.GONE
            FileScanner.invalidate()
            Toast.makeText(this@PayloadActivity,
                if (fails == 0) "Extracted ${supported.size} partitions to ${outputDir.absolutePath}" else "$fails failed",
                Toast.LENGTH_LONG).show()
        }
    }

    inner class PartAdapter : RecyclerView.Adapter<PartAdapter.VH>() {
        private val items = ArrayList<PayloadDumper.Partition>()
        fun submit(list: List<PayloadDumper.Partition>) { items.clear(); items.addAll(list); notifyDataSetChanged() }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_archive, parent, false)
            return VH(v)
        }
        override fun getItemCount() = items.size
        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            private val icon: ImageView = v.findViewById(R.id.icon)
            private val name: TextView = v.findViewById(R.id.name)
            private val size: TextView = v.findViewById(R.id.size)
            fun bind(p: PayloadDumper.Partition) {
                icon.setImageResource(R.drawable.ic_bin)
                name.text = if (p.supported) p.name else "${p.name}  (incremental)"
                size.text = if (p.size > 0) StorageUtil.formatSize(p.size) else ""
                itemView.setOnClickListener { extractOne(p) }
            }
        }
    }
}
