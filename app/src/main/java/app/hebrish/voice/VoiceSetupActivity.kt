// SPDX-License-Identifier: GPL-3.0-only
package app.hebrish.voice

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import app.hebrish.core.Catalogue
import app.hebrish.core.ModelDownload
import app.hebrish.core.ModelPrefs
import app.hebrish.core.ModelStore
import app.hebrish.core.Transcriber
import helium314.keyboard.latin.R

/**
 * Voice setup: pick and download a model, and grant the microphone. Opened by the mic key when
 * either is missing, and from its own launcher icon, "Hebrish voice". Built in code so the
 * fork adds no layouts that could collide with upstream ones.
 */
class VoiceSetupActivity : Activity() {
    private lateinit var picker: RadioGroup
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var download: Button
    private lateinit var micStatus: TextView
    private lateinit var micBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = dp(20)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }
        fun text(res: Int, sizeSp: Float = 16f) = TextView(this).apply {
            setText(res); setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp); setPadding(0, dp(8), 0, dp(4))
        }
        col.addView(text(R.string.hebrish_voice_setup_title, 22f))
        col.addView(text(R.string.hebrish_voice_setup_intro))

        col.addView(text(R.string.hebrish_voice_setup_model, 18f))
        picker = RadioGroup(this)
        col.addView(picker)
        status = TextView(this)
        col.addView(status)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 1000 }
        col.addView(progress)
        download = Button(this).apply {
            setOnClickListener {
                if (ModelDownload.status is ModelDownload.Status.Running) ModelDownload.cancel()
                else ModelDownload.start(this@VoiceSetupActivity)
            }
        }
        col.addView(download)

        col.addView(text(R.string.hebrish_voice_setup_mic, 18f))
        micStatus = TextView(this)
        col.addView(micStatus)
        micBtn = Button(this).apply {
            setText(R.string.hebrish_voice_setup_mic_grant)
            setOnClickListener { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC) }
        }
        col.addView(micBtn)

        col.addView(text(R.string.hebrish_voice_setup_use, 18f))
        col.addView(text(R.string.hebrish_voice_setup_use_body))
        col.addView(Button(this).apply {
            setText(R.string.hebrish_voice_setup_site)
            setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://hebrish.app"))) }
        })
        setContentView(ScrollView(this).apply { addView(col) })
    }

    override fun onResume() {
        super.onResume()
        ModelDownload.listener = { render() }
        ModelDownload.check(this)
        render()
    }

    override fun onPause() {
        super.onPause()
        ModelDownload.listener = null
    }

    @Deprecated("Activity API")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        render()
    }

    private fun render() {
        val s = ModelDownload.status
        val running = s is ModelDownload.Status.Running
        renderPicker(running)
        val model = Catalogue.selected(this)
        val installed = ModelStore.installed(this, model)
        val mb = (installed?.totalBytes ?: ModelStore.BUNDLED.values.first().totalBytes) / 1_000_000
        progress.visibility = if (running) View.VISIBLE else View.GONE
        when {
            s is ModelDownload.Status.Running -> {
                progress.progress = (s.done * 1000 / s.total.coerceAtLeast(1)).toInt()
                status.text = getString(R.string.hebrish_voice_setup_downloading, model.name, s.done / 1_000_000, s.total / 1_000_000)
                download.setText(android.R.string.cancel)
                download.visibility = View.VISIBLE
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            ModelStore.isReady(this) -> {
                status.text = getString(R.string.hebrish_voice_setup_ready, model.name, installed?.version ?: "", mb)
                download.visibility = View.GONE
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            else -> {
                status.text = if (s is ModelDownload.Status.Failed) getString(R.string.hebrish_voice_setup_failed, s.message)
                else getString(R.string.hebrish_voice_setup_missing, model.name, mb)
                download.setText(R.string.hebrish_voice_setup_download)
                download.visibility = View.VISIBLE
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        val mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        micStatus.setText(if (mic) R.string.hebrish_voice_setup_mic_ok else R.string.hebrish_voice_setup_mic_missing)
        micBtn.visibility = if (mic) View.GONE else View.VISIBLE
    }

    private fun renderPicker(running: Boolean) {
        val selected = Catalogue.selected(this)
        picker.setOnCheckedChangeListener(null)
        picker.removeAllViews()
        for (m in Catalogue.get(this).models) {
            val state = getString(when {
                !m.available -> R.string.hebrish_voice_setup_soon
                ModelStore.isReady(this, model = m) -> R.string.hebrish_voice_setup_downloaded
                else -> R.string.hebrish_voice_setup_not_downloaded
            })
            picker.addView(RadioButton(this).apply {
                id = View.generateViewId()
                tag = m.id
                text = getString(R.string.hebrish_voice_setup_option, m.name, state, m.summary)
                isEnabled = m.available && !running
                isChecked = m.id == selected.id
                setPadding(paddingLeft, dp(6), paddingRight, dp(6))
            })
        }
        picker.setOnCheckedChangeListener { group, checkedId ->
            val id = group.findViewById<RadioButton>(checkedId)?.tag as? String ?: return@setOnCheckedChangeListener
            if (id == Catalogue.selected(this).id) return@setOnCheckedChangeListener
            ModelPrefs.setSelectedModel(this, id)
            Transcriber.unload()
            ModelDownload.clearFailure()
            ModelDownload.check(this)
            render()
        }
    }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    companion object {
        private const val REQ_MIC = 1
    }
}
