package com.hyperfiles.manager

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class ArchiveEntryAdapter(
    private val onClick: (ArchiveEngine.Entry) -> Unit
) : RecyclerView.Adapter<ArchiveEntryAdapter.VH>() {

    private val items = ArrayList<ArchiveEngine.Entry>()

    fun submit(list: List<ArchiveEngine.Entry>) {
        items.clear(); items.addAll(list); notifyDataSetChanged()
    }

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
        fun bind(e: ArchiveEngine.Entry) {
            name.text = e.name
            if (e.isDirectory) {
                icon.setImageResource(R.drawable.ic_folder)
                size.text = ""
            } else {
                icon.setImageResource(FileTypes.iconFor(File(e.name)))
                size.text = if (e.size >= 0) StorageUtil.formatSize(e.size) else ""
            }
            itemView.setOnClickListener { onClick(e) }
        }
    }
}
