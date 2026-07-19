package com.hyperfiles.manager

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hyperfiles.manager.databinding.ActivityDuplicatesBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class DuplicatesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDuplicatesBinding
    private val rows = ArrayList<Row>()
    private lateinit var adapter: DupAdapter

    class Row(val isHeader: Boolean, val text: String?, val file: File?, var checked: Boolean)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theming.applyActivityTheme(this)
        binding = ActivityDuplicatesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = DupAdapter()
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter
        binding.deleteBtn.setOnClickListener { deleteSelected() }

        scan()
    }

    private fun scan() {
        binding.progress.visibility = View.VISIBLE
        binding.empty.visibility = View.GONE
        rows.clear(); adapter.notifyDataSetChanged(); refreshDeleteButton()
        lifecycleScope.launch {
            val (built, wasted) = withContext(Dispatchers.IO) { findDuplicates() }
            rows.clear(); rows.addAll(built)
            adapter.notifyDataSetChanged()
            binding.progress.visibility = View.GONE
            supportActionBar?.subtitle = "Reclaimable: ${StorageUtil.formatSize(wasted)}"
            binding.empty.text = "No duplicates found"
            binding.empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
            refreshDeleteButton()
        }
    }

    private fun findDuplicates(): Pair<List<Row>, Long> {
        val files = ArrayList<File>()
        val stack = ArrayDeque<File>()
        stack.addLast(StorageUtil.primaryStorage())
        val cachePath = cacheDir.absolutePath
        while (stack.isNotEmpty()) {
            val dir = stack.removeLast()
            val children = dir.listFiles() ?: continue
            for (c in children) {
                try {
                    if (c.isDirectory) {
                        if (c.name == ".thumbnails" || c.name == ".FilesDevTrash" || c.absolutePath.startsWith(cachePath)) continue
                        stack.addLast(c)
                    } else if (c.length() > 0) files.add(c)
                } catch (_: Exception) {}
            }
        }
        val bySize = files.groupBy { it.length() }
        val dupSets = ArrayList<Pair<Long, List<File>>>()
        for ((sz, group) in bySize) {
            if (group.size < 2) continue
            val byHash = HashMap<String, MutableList<File>>()
            for (f in group) {
                val h = try { md5(f) } catch (e: Exception) { continue }
                byHash.getOrPut(h) { mutableListOf() }.add(f)
            }
            for ((_, dups) in byHash) if (dups.size > 1) dupSets.add(sz to dups)
        }
        dupSets.sortByDescending { it.first * (it.second.size - 1) }
        val out = ArrayList<Row>()
        var wasted = 0L
        for ((sz, dups) in dupSets) {
            wasted += sz * (dups.size - 1)
            out.add(Row(true, "${dups.size} copies · ${StorageUtil.formatSize(sz)} each", null, false))
            dups.sortedBy { it.absolutePath }.forEachIndexed { i, f -> out.add(Row(false, null, f, i > 0)) }
        }
        return out to wasted
    }

    private fun md5(f: File): String {
        val d = MessageDigest.getInstance("MD5")
        f.inputStream().use { ins ->
            val buf = ByteArray(1 shl 16)
            while (true) { val r = ins.read(buf); if (r < 0) break; d.update(buf, 0, r) }
        }
        return d.digest().joinToString("") { "%02x".format(it) }
    }

    private fun refreshDeleteButton() {
        val n = rows.count { !it.isHeader && it.checked }
        binding.deleteBtn.isEnabled = n > 0
        binding.deleteBtn.text = if (n > 0) "Delete selected ($n)" else "Delete selected"
    }

    private fun deleteSelected() {
        val targets = rows.filter { !it.isHeader && it.checked }.mapNotNull { it.file }
        if (targets.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("Delete ${targets.size} file(s)?")
            .setMessage("This permanently removes the selected duplicates.")
            .setPositiveButton("Delete") { _, _ ->
                binding.progress.visibility = View.VISIBLE
                lifecycleScope.launch {
                    val fails = withContext(Dispatchers.IO) {
                        var f = 0; targets.forEach { if (!it.delete()) f++ }; f
                    }
                    FileScanner.invalidate()
                    Toast.makeText(this@DuplicatesActivity,
                        if (fails == 0) "Deleted ${targets.size}" else "$fails failed", Toast.LENGTH_SHORT).show()
                    scan()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    inner class DupAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemViewType(position: Int) = if (rows[position].isHeader) 0 else 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inf = LayoutInflater.from(parent.context)
            return if (viewType == 0)
                HeaderVH(inf.inflate(R.layout.item_dup_header, parent, false))
            else FileVH(inf.inflate(R.layout.item_dup, parent, false))
        }

        override fun getItemCount() = rows.size

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val row = rows[position]
            if (holder is HeaderVH) holder.header.text = row.text
            else if (holder is FileVH) holder.bind(row)
        }

        inner class HeaderVH(v: View) : RecyclerView.ViewHolder(v) {
            val header: TextView = v.findViewById(R.id.header)
        }

        inner class FileVH(v: View) : RecyclerView.ViewHolder(v) {
            private val check: CheckBox = v.findViewById(R.id.check)
            private val name: TextView = v.findViewById(R.id.name)
            private val path: TextView = v.findViewById(R.id.path)
            fun bind(row: Row) {
                val f = row.file ?: return
                name.text = f.name
                path.text = f.parent
                check.setOnCheckedChangeListener(null)
                check.isChecked = row.checked
                check.setOnCheckedChangeListener { _, isChecked ->
                    row.checked = isChecked
                    refreshDeleteButton()
                }
                itemView.setOnClickListener { OpenHelper.open(this@DuplicatesActivity, f) }
            }
        }
    }
}
