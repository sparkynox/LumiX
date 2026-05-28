package com.sparkynox.lumix.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.google.firebase.auth.FirebaseAuth
import com.sparkynox.lumix.R
import com.sparkynox.lumix.databinding.ActivityMainBinding
import com.sparkynox.lumix.ui.fragments.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Keep fragment instances so state isn't lost on tab switch
    private val homeFragment = HomeFragment()
    private val subscriptionsFragment = SubscriptionsFragment()
    private val searchFragment = SearchFragment()
    private val likedFragment = LikedVideosFragment()
    private val historyFragment = HistoryFragment()

    private var activeFragment: Fragment = homeFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupAvatar()
        setupFragments()
        setupBottomNav()
        setupTopButtons()
    }

    private fun setupAvatar() {
        val user = FirebaseAuth.getInstance().currentUser
        user?.photoUrl?.let {
            Glide.with(this).load(it).transform(CircleCrop()).into(binding.imgAvatar)
        }
    }

    private fun setupFragments() {
        val fm = supportFragmentManager
        fm.beginTransaction().apply {
            add(R.id.fragmentContainer, homeFragment, "home")
            add(R.id.fragmentContainer, subscriptionsFragment, "subs").hide(subscriptionsFragment)
            add(R.id.fragmentContainer, searchFragment, "search").hide(searchFragment)
            add(R.id.fragmentContainer, likedFragment, "liked").hide(likedFragment)
            add(R.id.fragmentContainer, historyFragment, "history").hide(historyFragment)
        }.commit()
    }

    private fun switchFragment(fragment: Fragment) {
        if (fragment == activeFragment) return
        supportFragmentManager.beginTransaction()
            .hide(activeFragment)
            .show(fragment)
            .commit()
        activeFragment = fragment
    }

    private fun setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { switchFragment(homeFragment); true }
                R.id.nav_subscriptions -> { switchFragment(subscriptionsFragment); true }
                R.id.nav_search -> { switchFragment(searchFragment); true }
                R.id.nav_liked -> { switchFragment(likedFragment); true }
                R.id.nav_history -> { switchFragment(historyFragment); true }
                else -> false
            }
        }
    }

    private fun setupTopButtons() {
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.imgAvatar.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }
}
