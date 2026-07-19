package com.hyperfiles.manager

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class PdfPageAdapter(
    private val renderer: PdfRenderer,
    private val targetWidth: Int
) : RecyclerView.Adapter<PdfPageAdapter.VH>() {

    private val lock = Any()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_pdf_page, parent, false)
        return VH(v)
    }

    override fun getItemCount() = renderer.pageCount

    override fun onBindViewHolder(holder: VH, position: Int) {
        val bmp = renderPage(position)
        if (bmp != null) holder.image.setImageBitmap(bmp)
    }

    private fun renderPage(index: Int): Bitmap? {
        return try {
            synchronized(lock) {
                renderer.openPage(index).use { page ->
                    val w = targetWidth.coerceAtLeast(320)
                    val h = (w.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    inner class VH(v: android.view.View) : RecyclerView.ViewHolder(v) {
        val image: ImageView = v.findViewById(R.id.pageImage)
    }
}
