package com.sparkynox.lumix.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.sparkynox.lumix.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) { "1.0.0" }
        binding.tvVersion.text = "Lumi X v$version"

        binding.btnGithub.setOnClickListener { openUrl("https://github.com/SparkyNox") }
        binding.btnSourceCode.setOnClickListener { openUrl("https://github.com/SparkyNox/LumiX") }
        binding.btnModrinth.setOnClickListener { openUrl("https://modrinth.com/user/sparkynox") }
    }

    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
