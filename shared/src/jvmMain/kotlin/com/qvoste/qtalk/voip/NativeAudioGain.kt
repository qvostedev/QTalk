package com.qvoste.qtalk.voip

import org.linphone.core.*

/** One native policy for refresh, new streams, mute/hold and LAN handoff restoration. */
internal fun applyAudioGain(core: Core, conference: Conference?, levels: GainLevels,
    microphoneMuted: Boolean, speakerMuted: Boolean, held: Boolean, staged: Set<Call>,
    zeroMicrophone: Boolean = false, zeroSpeaker: Boolean = false) {
    core.micGainDb = AudioGain.db(levels.microphone) + levels.boostDb.coerceIn(0f, 6f)
    core.playbackGainDb = AudioGain.db(levels.speaker)
    val mic = AudioGain.muted(microphoneMuted || zeroMicrophone, held, levels.microphone)
    core.isMicEnabled = !mic
    conference?.microphoneMuted = mic
    core.calls.forEach { call ->
        call.setMicrophoneMuted(AudioGain.muted(microphoneMuted || zeroMicrophone, held, levels.microphone, call in staged))
        call.setSpeakerMuted(AudioGain.muted(speakerMuted || zeroSpeaker, held, levels.speaker, call in staged))
    }
}
