package com.example.asmrplayer.core.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

/**
 * The platform edge of the output indicator: reads the connected sinks and turns
 * them into the app's own vocabulary.
 *
 * Kept in its own file so [pickActiveOutput] stays plain Kotlin. Everything Android
 * lives here, and nothing here is worth testing -- it is a switch over constants.
 *
 * Returns an empty list rather than throwing when the service is unavailable: an
 * indicator that cannot tell where the sound is going should draw nothing, not take
 * the player page down with it.
 */
internal fun androidAudioOutputs(context: Context): List<AudioOutputCandidate> {
    val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return emptyList()
    val devices = try {
        manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
    } catch (_: Exception) {
        return emptyList()
    }
    return devices.mapNotNull { device ->
        if (!device.isSink) return@mapNotNull null
        AudioOutputCandidate(
            kind = device.audioOutputKind(),
            name = device.productName?.toString()?.takeIf { it.isNotBlank() },
        )
    }
}

@Suppress("InlinedApi")
private fun AudioDeviceInfo.audioOutputKind(): AudioOutputKind = when (type) {
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_USB_DEVICE,
    AudioDeviceInfo.TYPE_USB_ACCESSORY,
    AudioDeviceInfo.TYPE_USB_HEADSET,
    AudioDeviceInfo.TYPE_LINE_ANALOG,
    AudioDeviceInfo.TYPE_LINE_DIGITAL,
    AudioDeviceInfo.TYPE_AUX_LINE,
    AudioDeviceInfo.TYPE_HDMI,
    AudioDeviceInfo.TYPE_HDMI_ARC,
    AudioDeviceInfo.TYPE_HDMI_EARC,
    -> AudioOutputKind.Wired

    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
    AudioDeviceInfo.TYPE_BLE_HEADSET,
    AudioDeviceInfo.TYPE_BLE_SPEAKER,
    AudioDeviceInfo.TYPE_HEARING_AID,
    -> AudioOutputKind.Bluetooth

    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
    -> AudioOutputKind.BuiltIn

    else -> AudioOutputKind.Other
}
