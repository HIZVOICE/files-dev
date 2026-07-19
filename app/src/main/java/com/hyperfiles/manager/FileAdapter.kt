package com.hyperfiles.manager

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.io.File

class FileAdapter(
    private val onClick: (File) -> Unit,
    private val onMore: (File, View) -> Unit,
    private val selectable: Boolean = false
) : RecyclerView.Adapter<FileAdapter.VH>() {

    private val items = ArrayList<File>()
    var grid = false
    var media = false

    var selectionMode = false
        private set
    val selected = LinkedHashSet<File>()
    /** Lets the browser mark root-only paths as directories (File.isDirectory can't stat them). */
    var isDirOverride: ((File) -> Boolean)? = null
    /** Lets the browser supply sizes for files it listed via an elevated shell (File.length()==0). */
    var sizeOverride: ((File) -> Long?)? = null
    var onSelectionChanged: ((Int) -> Unit)? = null
    var onEnterSelectionMode: (() -> Unit)? = null

    fun submit(list: List<File>) {
        items.clear(); items.addAll(list); notifyDataSetChanged()
    }

    fun enterSelectionMode() {
        if (!selectionMode) { selectionMode = true; onEnterSelectionMode?.invoke() }
    }

    fun exitSelection() {
        selectionMode = false; selected.clear(); notifyDataSetChanged()
    }

    fun toggle(f: File) {
        if (!selected.remove(f)) selected.add(f)
        onSelectionChanged?.invoke(selected.size)
        notifyDataSetChanged()
    }

    fun selectAllVisible() {
        selected.addAll(items)
        onSelectionChanged?.invoke(selected.size)
        notifyDataSetChanged()
    }

    /** Enter selection mode and select exactly [list] (used by "Select by date"). */
    fun selectOnly(list: List<File>) {
        enterSelectionMode()
        selected.clear(); selected.addAll(list)
        onSelectionChanged?.invoke(selected.size)
        notifyDataSetChanged()
    }

    fun selectedList(): List<File> = selected.toList()

    override fun getItemViewType(position: Int) = if (grid) 1 else if (media) 2 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val layout = when (viewType) {
            1 -> R.layout.item_file_grid
            2 -> R.layout.item_media
            else -> R.layout.item_file_list
        }
        val v = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        private val icon: ImageView = v.findViewById(R.id.icon)
        private val name: TextView = v.findViewById(R.id.name)
        private val info: TextView? = v.findViewById(R.id.info)
        private val more: ImageView? = v.findViewById(R.id.more)
        private val chevron: ImageView? = v.findViewById(R.id.chevron)
        private val playBadge: ImageView? = v.findViewById(R.id.playBadge)

        fun bind(f: File) {
            itemView.scaleX = 1f; itemView.scaleY = 1f
            PressScale.attach(itemView)
            name.text = f.name
            val dir = f.isDirectory || isDirOverride?.invoke(f) == true
            val isImg = !dir && FileTypes.isImage(f.name)
            val isVid = !dir && FileTypes.isVideo(f.name)
            val isAud = !dir && FileTypes.isAudio(f.name)
            val iconRes = if (dir) R.drawable.ic_folder else FileTypes.iconFor(f)
            if (media && !dir) {
                // Rounded-thumbnail media rows: real thumbnails for photos/videos,
                // embedded album art for audio, centered file-type glyph otherwise.
                when {
                    isImg || isVid -> {
                        icon.setPadding(0, 0, 0, 0)
                        icon.scaleType = ImageView.ScaleType.CENTER_CROP
                        Glide.with(icon).load(f).centerCrop().placeholder(iconRes).into(icon)
                    }
                    isAud -> {
                        Glide.with(icon).clear(icon)
                        Thumbs.loadAudioArt(icon, f, iconRes)
                    }
                    else -> {
                        Glide.with(icon).clear(icon)
                        val p = (icon.resources.displayMetrics.density * 15).toInt()
                        icon.setPadding(p, p, p, p)
                        icon.scaleType = ImageView.ScaleType.FIT_CENTER
                        icon.setImageResource(iconRes)
                    }
                }
                playBadge?.visibility = if (isVid) View.VISIBLE else View.GONE
            } else {
                icon.setPadding(0, 0, 0, 0)
                if (isImg || isVid) {
                    icon.scaleType = ImageView.ScaleType.CENTER_CROP
                    Glide.with(icon).load(f).centerCrop().placeholder(iconRes).into(icon)
                } else {
                    Glide.with(icon).clear(icon)
                    icon.scaleType = ImageView.ScaleType.FIT_CENTER
                    icon.setImageResource(iconRes)
                }
                playBadge?.visibility = View.GONE
            }
            info?.let {
                val len = sizeOverride?.invoke(f) ?: f.length()
                it.text = when {
                    dir -> {
                        val n = f.listFiles()?.size ?: 0
                        "${StorageUtil.shortStamp(f.lastModified())}  |  $n ${if (n == 1) "item" else "items"}"
                    }
                    media -> "${StorageUtil.formatSize(len)}  •  ${StorageUtil.mediumDate(f.lastModified())}"
                    else -> "${StorageUtil.shortStamp(f.lastModified())}  |  ${StorageUtil.formatSize(len)}"
                }
            }

            val isSel = selected.contains(f)
            itemView.isActivated = selectionMode && isSel
            // Folders in non-selectable lists show a navigation chevron (HyperOS style);
            // everything else keeps the overflow / selection control.
            val useChevron = !selectable && dir && !selectionMode
            chevron?.visibility = if (useChevron) View.VISIBLE else View.GONE
            more?.visibility = if (useChevron) View.GONE else View.VISIBLE
            more?.setImageResource(
                when {
                    !selectionMode -> R.drawable.ic_more
                    isSel -> R.drawable.ic_check_circle
                    else -> R.drawable.ic_circle
                }
            )

            itemView.setOnClickListener {
                if (selectionMode) {
                    Anim.bounce(icon); toggle(f)
                } else {
                    Anim.bounce(icon)
                    icon.postDelayed({ onClick(f) }, 90)
                }
            }
            more?.setOnClickListener {
                if (selectionMode) { Anim.bounce(icon); toggle(f) }
                else { Anim.bounce(it); onMore(f, it) }
            }
            itemView.setOnLongClickListener {
                when {
                    selectable && !selectionMode -> { enterSelectionMode(); toggle(f); true }
                    selectionMode -> { toggle(f); true }
                    else -> { onMore(f, itemView); true }
                }
            }
        }
    }
}
