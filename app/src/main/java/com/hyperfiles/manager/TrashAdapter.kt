package com.hyperfiles.manager

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class TrashAdapter(
    private val onMenu: (TrashBin.Entry, View) -> Unit
) : RecyclerView.Adapter<TrashAdapter.VH>() {

    private val items = ArrayList<TrashBin.Entry>()

    fun submit(list: List<TrashBin.Entry>) {
        items.clear(); items.addAll(list); notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_file_list, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size
    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        private val icon: ImageView = v.findViewById(R.id.icon)
        private val name: TextView = v.findViewById(R.id.name)
        private val info: TextView = v.findViewById(R.id.info)
        private val more: ImageView = v.findViewById(R.id.more)
        fun bind(e: TrashBin.Entry) {
            name.text = e.originalName
            icon.setImageResource(if (e.file.isDirectory) R.drawable.ic_folder else FileTypes.iconFor(File(e.originalName)))
            info.text = "from ${e.originalParent}  ·  ${StorageUtil.formatDate(e.deletedAt)}"
            more.setOnClickListener { onMenu(e, it) }
            itemView.setOnClickListener { onMenu(e, more) }
        }
    }
}
