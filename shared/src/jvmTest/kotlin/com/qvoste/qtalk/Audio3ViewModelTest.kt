package com.qvoste.qtalk

import com.qvoste.qtalk.domain.calls.ConnectSipUseCase
import com.qvoste.qtalk.ui.calls.CallsViewModel
import com.qvoste.qtalk.ui.calls.model.*
import com.qvoste.qtalk.voip.*
import kotlinx.coroutines.*
import kotlin.test.*

class Audio3ViewModelTest {
    @Test fun finalGainValuesAreDeliveredWhileAnotherCommandHoldsMutexAndPreviewStopsSynchronously() = runBlocking {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val microphone = CompletableDeferred<Float>(); val speaker = CompletableDeferred<Float>()
        var preview = false
        val engine = object : VoipEngine by FakeVoipEngine() {
            override suspend fun refreshAudioDevices() { entered.complete(Unit); release.await() }
            override suspend fun setMicrophoneSensitivity(value: Float) { microphone.complete(value) }
            override suspend fun setSpeakerVolume(value: Float) { speaker.complete(value) }
            override fun previewRingtone(ringtoneId: String) { preview = true }
            override fun stopRingtonePreview() { preview = false }
        }
        val vm = CallsViewModel(engine, ConnectSipUseCase(engine), InMemorySipAccountStore(),
            InMemoryContactStore(), DisabledContactDatabaseService, InMemoryContactNoteStore(), InMemoryCallHistoryStore())
        try {
            vm.refreshAudioDevices()
            withTimeout(5_000) { entered.await() }
            vm.setMicrophoneSensitivity(.2f); vm.setSpeakerVolume(.7f)
            assertEquals(.2f, withTimeout(5_000) { microphone.await() })
            assertEquals(.7f, withTimeout(5_000) { speaker.await() })
            vm.previewRingtone("warm"); assertTrue(preview)
            vm.stopRingtonePreview(); assertFalse(preview)
        } finally { release.complete(Unit); vm.close() }
    }
}
