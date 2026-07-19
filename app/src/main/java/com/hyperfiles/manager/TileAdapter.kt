package com.hyperfiles.manager

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class Tile(
    val id: String,
    val title: String,
    val iconRes: Int,
    var subtitle: String = "",
    val color: Int = 0
)

class TileAdapter(
    private val tiles: List<Tile>,
    private val layoutRes: Int = R.layout.item_category,
    private val onClick: (Tile) -> Unit
) : RecyclerView.Adapter<TileAdapter.VH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
        return VH(v)
    }

    override fun getItemCount() = tiles.size

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(tiles[position])

    inner class VH(v: android.view.View) : RecyclerView.ViewHolder(v) {
        private val iconBg: FrameLayout? = v.findViewById(R.id.tileIconBg)
        private val icon: ImageView = v.findViewById(R.id.tileIcon)
        private val title: TextView = v.findViewById(R.id.tileTitle)
        private val subtitle: TextView = v.findViewById(R.id.tileSubtitle)
        fun bind(t: Tile) {
            icon.setImageResource(t.iconRes)
            title.text = t.title
            subtitle.text = t.subtitle
            if (t.color != 0) {
                iconBg?.backgroundTintList = ColorStateList.valueOf(t.color)
                icon.imageTintList = ColorStateList.valueOf(Color.WHITE)
            } else {
                iconBg?.backgroundTintList = null
                icon.imageTintList = null
            }
            itemView.scaleX = 1f; itemView.scaleY = 1f
            PressScale.attach(itemView)
            itemView.setOnClickListener {
                Anim.bounce(icon)
                icon.postDelayed({ onClick(t) }, 90)
            }
        }
    }
}
