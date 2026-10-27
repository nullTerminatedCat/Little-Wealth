package com.lincx.noise

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 前台采集麦克风，大约每 0.2 秒交出一窗 16-bit 单声道 PCM。
 *
 * 小米等机型对普通 MIC 常做自动增益和降噪。这里先试 UNPROCESSED，
 * 再试 VOICE_RECOGNITION，最后才用 MIC，并尝试关掉这三项效果。
 * 系统若不提供未处理通路，读数仍会带着手机自己的加工。
 */
fun microphonePcm(): Flow<PcmWindow> = callbackFlow {
    val output = this
    val job = launch(Dispatchers.IO) {
        var started: OpenRecording? = null
        try {
            val opened = openRecording()
            started = opened
            val recorder = opened.recorder
            val windowSize = (recorder.sampleRate / 5).coerceAtLeast(1)
            val window = ShortArray(windowSize)
            val chunk = ShortArray(minOf(windowSize, 4096).coerceAtLeast(1))
            var filled = 0
            while (isActive) {
                val want = minOf(chunk.size, windowSize - filled)
                val n = recorder.read(chunk, 0, want)
                if (n < 0) throw IllegalStateException("麦克风读取失败（$n）")
                if (n == 0) continue
                chunk.copyInto(window, destinationOffset = filled, endIndex = n)
                filled += n
                if (filled >= windowSize) {
                    val copy = ShortArray(filled)
                    window.copyInto(copy, endIndex = filled)
                    output.send(PcmWindow(copy, filled, recorder.sampleRate))
                    filled = 0
                }
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            output.close(t)
        } finally {
            started?.close()
        }
    }
    awaitClose { job.cancel() }
}

class PcmWindow(
    val samples: ShortArray,
    val count: Int,
    val sampleRate: Int,
)

private class OpenRecording(
    val recorder: AudioRecord,
    private val effects: List<AudioEffect>,
) {
    fun close() {
        try {
            if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                recorder.stop()
            }
        } catch (_: IllegalStateException) {
        }
        for (effect in effects) {
            try {
                effect.release()
            } catch (_: RuntimeException) {
            }
        }
        recorder.release()
    }
}

private fun openRecording(): OpenRecording {
    val sources = intArrayOf(
        MediaRecorder.AudioSource.UNPROCESSED,
        MediaRecorder.AudioSource.VOICE_RECOGNITION,
        MediaRecorder.AudioSource.MIC,
    )
    val rates = intArrayOf(48_000, 44_100, 16_000)
    for (source in sources) {
        for (rate in rates) {
            val opened = tryOpen(source, rate) ?: continue
            return opened
        }
    }
    throw IllegalStateException("无法打开麦克风")
}

private fun tryOpen(source: Int, rate: Int): OpenRecording? {
    val minBytes = AudioRecord.getMinBufferSize(
        rate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
    )
    if (minBytes <= 0) return null
    val windowBytes = rate * 2 / 5
    val bufferBytes = maxOf(minBytes * 2, windowBytes * 2)
    val recorder = try {
        AudioRecord.Builder()
            .setAudioSource(source)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(bufferBytes)
            .build()
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: UnsupportedOperationException) {
        null
    } ?: return null
    if (recorder.state != AudioRecord.STATE_INITIALIZED) {
        recorder.release()
        return null
    }
    try {
        recorder.startRecording()
    } catch (_: IllegalStateException) {
        recorder.release()
        return null
    }
    if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
        recorder.release()
        return null
    }
    return OpenRecording(recorder, disableDeviceProcessing(recorder.audioSessionId))
}

private fun disableDeviceProcessing(sessionId: Int): List<AudioEffect> {
    val effects = ArrayList<AudioEffect>(3)
    fun turnOff(create: () -> AudioEffect?) {
        try {
            val effect = create() ?: return
            effect.enabled = false
            effects += effect
        } catch (_: RuntimeException) {
        }
    }
    if (effectAvailable { AutomaticGainControl.isAvailable() }) {
        turnOff { AutomaticGainControl.create(sessionId) }
    }
    if (effectAvailable { NoiseSuppressor.isAvailable() }) {
        turnOff { NoiseSuppressor.create(sessionId) }
    }
    if (effectAvailable { AcousticEchoCanceler.isAvailable() }) {
        turnOff { AcousticEchoCanceler.create(sessionId) }
    }
    return effects
}

private fun effectAvailable(query: () -> Boolean): Boolean = try {
    query()
} catch (_: RuntimeException) {
    false
}
