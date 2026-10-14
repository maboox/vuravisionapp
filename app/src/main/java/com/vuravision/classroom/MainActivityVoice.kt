package com.vuravision.classroom
import android.app.*
import android.content.*
import android.graphics.*
import android.os.*
import android.view.*
import android.widget.*
import com.vuravision.classroom.ui.IconCatalog
import com.vuravision.classroom.ui.PRIMARY_CONTAINER
import com.vuravision.classroom.ui.SURFACE
import com.vuravision.classroom.ui.s
import com.vuravision.classroom.ui.tr
import com.vuravision.classroom.voice.RouterEndpoint
import com.vuravision.classroom.voice.RouterVoiceAssistant
import com.vuravision.classroom.voice.RouterVoiceHttp
import com.vuravision.classroom.voice.VoiceAssistant
import com.vuravision.classroom.voice.VoiceAudio
import com.vuravision.classroom.voice.VoiceConversation
import com.vuravision.classroom.voice.VoiceKnowledge
import com.vuravision.classroom.voice.VoiceScreen
import com.vuravision.classroom.voice.VoiceSettings
import com.vuravision.classroom.voice.VoiceSettingsUi
import kotlin.math.*

// The voice assistant session (split from MainActivity, item 11).

/** Opens even while the assistant is off: this page is where it is turned on (it used to silently do nothing). */
internal fun MainActivity.voiceSettings() {
    VoiceSettingsUi(this, menuReturn.also { menuReturn = null }).connection(voiceKeys, voiceWorker) { stopVoice() }
}
internal fun MainActivity.toggleVoice() {
    if (!VoiceSettings.enabled(this)) {
        stopVoice()
        return
    }
    if (voiceStarting || voiceAssistant?.active == true) {
        stopVoice()
        return
    }
    val settings = VoiceSettings.load(this)
    val keys = voiceKeys.forProvider(settings.provider)
    if (!keys.hasKey()) {
        voiceSettings()
        return
    }
    if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
        requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), voicePermissionRequest)
        return
    }
    val generation = ++voiceStartGeneration
    voiceStarting = true
    setVoiceState("voice_connecting")
    voiceWorker.execute {
        try {
            val key = keys.read()
            val knowledge = VoiceKnowledge.load(this)
            handler.post {
                if (destroyed || !VoiceSettings.enabled(this) || !voiceStarting || generation != voiceStartGeneration)return@post
                voiceStarting = false
                val ended: (String?) -> Unit = { reason ->
                    voiceScreen?.close()
                    voiceScreen = null
                    voiceAssistant = null
                    setVoiceState("voice_assistant")
                    if (reason != null)toast(s(reason))
                }
                val audio = VoiceAudio(this, if (settings.provider == "openai")24000 else 16000)
                val assistant: VoiceConversation = if (settings.provider in listOf("openrouter", "avalai")) {
                    RouterVoiceAssistant(settings, knowledge, audio, RouterVoiceHttp(key, RouterEndpoint.of(settings)), { setVoiceState(it) }, ended, connected = { voiceScreen?.refresh() }, commands = voiceCommand)
                } else {
                    VoiceAssistant(settings, key, knowledge, audio, { setVoiceState(it) }, ended, connected = { voiceScreen?.refresh() }, commands = voiceCommand)
                }
                voiceAssistant = assistant
                assistant.setMuted(voiceMuted)
                voiceScreen = VoiceScreen(
                    this,
                    workspaceHost,
                    settings.frameSeconds,
                    { assistant.active && assistant.ready },
                    { board.hasActiveInteraction || SystemClock.uptimeMillis() - lastUserTouch < 500 },
                    assistant::sendImage,
                    { if (assistant.active)assistant.stop("voice_vision_error") }
                )
                assistant.start()
                voiceScreen?.start()
            }
        } catch (_: Exception) {
            handler.post {
                if (!destroyed && voiceStarting && generation == voiceStartGeneration) {
                    voiceStarting = false
                    setVoiceState("voice_assistant")
                    toast(s("voice_key_storage_error"))
                }
            }
        }
    }
}
internal fun MainActivity.setVoiceState(value: String) {
    if (!voiceButtonReady)return
    voiceButton.visibility = if (VoiceSettings.enabled(this))View.VISIBLE else View.GONE
    voiceButton.text = s(value)
    refreshMute()
    val active = value != "voice_assistant"
    voiceButton.setIconResource(if (active)R.drawable.ph_stop else R.drawable.ph_mic)
    voiceButton.contentDescription = if (active)s(value) + ". " + s("voice_stop")else s("voice_assistant")
    voiceButton.backgroundTintList = android.content.res.ColorStateList.valueOf(if (active)PRIMARY_CONTAINER else SURFACE)
}
internal fun MainActivity.stopVoice() {
    voiceMuted = false
    ++voiceStartGeneration
    voiceStarting = false
    voiceScreen?.close()
    voiceScreen = null
    voiceAssistant?.stop()
    voiceAssistant = null
    setVoiceState("voice_assistant")
}

internal fun MainActivity.refreshMute() {
    if (!voiceMuteButtonReady)return
    voiceMuteButton.visibility = if (VoiceSettings.enabled(this) && (voiceStarting || voiceAssistant?.active == true))View.VISIBLE else View.GONE
    voiceMuteButton.isSelected = voiceMuted
    voiceMuteButton.alpha = if (voiceMuted)1f else .6f
    voiceMuteButton.setImageResource(IconCatalog.resource(if (voiceMuted)"mute"else "voice_assistant"))
    voiceMuteButton.contentDescription = if (voiceMuted)tr("Microphone muted · tap to unmute", "میکروفن بی‌صدا · برای فعال کردن بزنید")else tr("Microphone active · tap to mute", "میکروفن فعال · برای بی‌صدا کردن بزنید")
    voiceMuteButton.tooltipText = voiceMuteButton.contentDescription
}
