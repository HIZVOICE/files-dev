package com.hyperfiles.manager

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.hyperfiles.manager.databinding.ActivityImageViewerBinding
import java.io.File

class ImageViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImageViewerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theming.applyActivityTheme(this)
        binding = ActivityImageViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val file = IntentInput.resolveToFile(this, intent)
        if (file == null) { finish(); return }
        title = file.name

        Glide.with(this).load(file).fitCenter().into(binding.image)

        binding.image.setOnClickListener {
            val bar = supportActionBar ?: return@setOnClickListener
            if (bar.isShowing) bar.hide() else bar.show()
        }
    }
}
