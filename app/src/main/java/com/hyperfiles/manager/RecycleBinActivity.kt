package com.hyperfiles.manager

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.hyperfiles.manager.databinding.ActivityRecycleBinBinding

class RecycleBinActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecycleBinBinding
    private lateinit var adapter: TrashAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theming.applyActivityTheme(this)
        binding = ActivityRecycleBinBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = TrashAdapter { e, v -> menu(e, v) }
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter
        load()
    }

    private fun load() {
        val entries = TrashBin.list(this)
        adapter.submit(entries)
        binding.empty.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        supportActionBar?.subtitle = "${entries.size} items"
    }

    private fun menu(entry: TrashBin.Entry, anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add("Restore")
        popup.menu.add("Open")
        popup.menu.add("Delete forever")
        popup.setOnMenuItemClickListener {
            when (it.title) {
                "Restore" -> {
                    if (TrashBin.restore(this, entry)) Toast.makeText(this, "Restored", Toast.LENGTH_SHORT).show()
                    else Toast.makeText(this, "Restore failed", Toast.LENGTH_SHORT).show()
                    load()
                }
                "Open" -> OpenHelper.open(this, entry.file)
                "Delete forever" -> {
                    TrashBin.deleteForever(this, entry); load()
                }
            }
            true
        }
        popup.show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.recycle_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_empty_bin) {
            AlertDialog.Builder(this)
                .setTitle("Empty recycle bin?")
                .setMessage("All items will be permanently deleted.")
                .setPositiveButton("Empty") { _, _ -> TrashBin.empty(this); load() }
                .setNegativeButton("Cancel", null)
                .show()
        }
        return super.onOptionsItemSelected(item)
    }
}
