package com.sparkynox.lumix.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.sparkynox.lumix.databinding.ActivitySettingsBinding
import com.sparkynox.lumix.helper.FirestoreHelper
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"

        setupCredits()
        setupActions()
    }

    private fun setupCredits() {
        // Credits section — SparkyNox
        binding.tvCreditName.text = "Developed by SparkyNox"
        binding.tvCreditDesc.text = "Lumi X • Yori Ecosystem"

        binding.btnGithub.setOnClickListener {
            openUrl("https://github.com/SparkyNox")
        }

        binding.btnSourceCode.setOnClickListener {
            openUrl("https://github.com/SparkyNox/LumiX")
        }

        binding.btnModrinth.setOnClickListener {
            openUrl("https://modrinth.com/user/sparkynox")
        }
    }

    private fun setupActions() {
        binding.btnClearHistory.setOnClickListener {
            lifecycleScope.launch {
                FirestoreHelper.clearHistory()
                Toast.makeText(this@SettingsActivity, "History cleared", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finishAffinity()
        }

        // App version
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) { "1.0.0" }
        binding.tvVersion.text = "Lumi X v$version"
    }

    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
