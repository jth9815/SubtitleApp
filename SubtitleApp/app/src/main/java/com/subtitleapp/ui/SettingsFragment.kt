package com.subtitleapp.ui

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import com.subtitleapp.R
import com.subtitleapp.stt.SttCoordinator
import com.subtitleapp.stt.SttMode

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        // STT 모드 선택
        findPreference<ListPreference>(SttCoordinator.PREF_STT_MODE)?.apply {
            entries = SttMode.values().map { "${it.displayName} — ${it.description}" }.toTypedArray()
            entryValues = SttMode.values().map { it.name }.toTypedArray()
            if (value == null) value = SttMode.FAST.name

            setOnPreferenceChangeListener { _, newValue ->
                val mode = SttMode.valueOf(newValue as String)
                summary = "${mode.displayName} — ${mode.description}"
                true
            }
            // 현재 값 요약 표시
            value?.let { v ->
                val mode = runCatching { SttMode.valueOf(v) }.getOrDefault(SttMode.FAST)
                summary = "${mode.displayName} — ${mode.description}"
            }
        }
    }
}
