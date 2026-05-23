package com.subtitleapp.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.subtitleapp.R

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "설정"

        supportFragmentManager.beginTransaction()
            .replace(R.id.settings_container, SettingsFragment())
            .commit()
    }
    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
