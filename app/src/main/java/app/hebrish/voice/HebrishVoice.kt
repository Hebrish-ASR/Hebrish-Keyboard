// SPDX-License-Identifier: GPL-3.0-only
package app.hebrish.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import app.hebrish.core.ModelStore
import app.hebrish.core.Recorder
import app.hebrish.core.Transcriber
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R

/**
 * Dictation inside the keyboard: the mic key starts a take, the mic key again ends it, and the
 * transcript is typed through LatinIME's own text input path. HeliBoard would instead switch to
 * a separate voice IME; the hooks that route here are listed in docs/upstream.md.
 *
 * A status pill sits at the top of the keyboard while a take is open or being transcribed.
 * Tapping the pill discards the take.
 */
object HebrishVoice {
    private enum class State { IDLE, RECORDING, TRANSCRIBING }

    private val main = Handler(Looper.getMainLooper())
    private var state = State.IDLE
    private var recorder: Recorder? = null
    private var pill: TextView? = null
    private var level = 0f
    /** Bumped when the field closes, so a transcript that finishes later is not typed elsewhere. */
    private var session = 0

    /** Called from LatinIME.setInputView: adds the status pill to the keyboard's root view. */
    fun attach(inputView: View) {
        val root = inputView as? FrameLayout ?: return
        val ctx = root.context
        val dp = { v: Int -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics).toInt() }
        pill = TextView(ctx).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(dp(16), dp(8), dp(16), dp(8))
            background = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(0xE6202A36.toInt()) }
            visibility = View.GONE
            setOnClickListener { cancel() }
        }
        root.addView(pill, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(4) })
        render()
    }

    /** Called from LatinIME.onEvent for KeyCode.VOICE_INPUT (mic key on the keyboard or toolbar). */
    fun onMicKey(ime: LatinIME) {
        when (state) {
            State.IDLE -> start(ime)
            State.RECORDING -> finish(ime)
            State.TRANSCRIBING -> Unit
        }
    }

    /** Called from LatinIME.onFinishInputViewInternal: the field closed, so drop any open take. */
    fun onFinishInput() {
        session++
        if (state == State.RECORDING) cancel()
    }

    private fun start(ime: LatinIME) {
        val needsMic = ime.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        if (needsMic || !ModelStore.isReady(ime)) {
            openSetup(ime)
            return
        }
        val rec = Recorder(onLevel = { level = it }, onLimit = { main.post { if (state == State.RECORDING) finish(ime) } })
        recorder = rec
        state = State.RECORDING
        try {
            rec.start()
        } catch (e: Exception) {
            recorder = null
            state = State.IDLE
            flash(ime.getString(R.string.hebrish_voice_mic_failed, e.message ?: e.javaClass.simpleName))
            return
        }
        // Load the model while the user talks; the first load takes a few seconds.
        Thread { runCatching { Transcriber.load(ime) } }.start()
        tick()
    }

    private fun finish(ime: LatinIME) {
        val rec = recorder ?: return
        recorder = null
        state = State.TRANSCRIBING
        render()
        val audio = rec.stop()
        val mine = session
        Thread {
            val result = runCatching { Transcriber.transcribe(ime, audio) }
            main.post {
                state = State.IDLE
                result.onSuccess { text ->
                    if (text.isNotEmpty() && mine == session) insert(ime, text)
                    render()
                }.onFailure { flash(ime.getString(R.string.hebrish_voice_failed, it.message ?: it.javaClass.simpleName)) }
            }
        }.start()
    }

    private fun cancel() {
        if (state != State.RECORDING) return
        recorder?.cancel()
        recorder = null
        state = State.IDLE
        render()
    }

    /** Types [text] after a space unless the cursor already follows whitespace or starts the field. */
    private fun insert(ime: LatinIME, text: String) {
        val before = ime.currentInputConnection?.getTextBeforeCursor(1, 0)
        val space = if (before.isNullOrEmpty() || before.last().isWhitespace()) "" else " "
        ime.onTextInput(space + text)
    }

    private fun tick() {
        if (state != State.RECORDING) return
        render()
        main.postDelayed(::tick, 250)
    }

    private fun render() {
        val p = pill ?: return
        val ctx = p.context
        when (state) {
            State.IDLE -> if (p.tag == null) p.visibility = View.GONE
            State.RECORDING -> {
                val s = recorder?.seconds ?: 0
                val dot = if (level > 0.02f) "●" else "○"
                show(p, ctx.getString(R.string.hebrish_voice_listening, dot, s / 60, s % 60))
            }
            State.TRANSCRIBING -> show(p, ctx.getString(
                if (Transcriber.isLoaded(ctx)) R.string.hebrish_voice_transcribing else R.string.hebrish_voice_loading))
        }
    }

    private fun show(p: TextView, text: String) {
        p.tag = null
        p.text = text
        p.visibility = View.VISIBLE
        p.bringToFront()
    }

    /** Shows [text] for a few seconds, e.g. an error. */
    private fun flash(text: String) {
        val p = pill ?: return
        show(p, text)
        p.tag = "flash"
        main.postDelayed({ if (p.tag == "flash") { p.tag = null; render() } }, 4000)
    }

    private fun openSetup(ctx: Context) {
        ctx.startActivity(Intent(ctx, VoiceSetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
