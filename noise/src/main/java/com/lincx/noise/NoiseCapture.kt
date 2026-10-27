package com.lincx.noise

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 前台采集麦克风。约每 200ms 产出一窗：短时 RMS（瞬时）和该窗样本峰值。
 * 调用方离开前台时应取消收集，以便释放麦克风。
 */
fun noiseReadings(): Flow<DbLevel.Window> = callbackFlow {
    val job = launch(Dispatchers.IO) {
        val recorder = openRecorder()
        try {
            recorder.startRecording()
            if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw IllegalStateException("麦克风未能开始录音")
            }
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
                    trySend(DbLevel.measure(window, filled))
                    filled = 0
                }
            }
        } finally {
            try {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    recorder.stop()
                }
            } catch (_: IllegalStateException) {
            }
            recorder.release()
        }
    }
    awaitClose { job.cancel() }
}

private fun openRecorder(): AudioRecord {
    val sources = intArrayOf(
        MediaRecorder.AudioSource.UNPROCESSED,
        MediaRecorder.AudioSource.MIC,
    )
    val rates = intArrayOf(48_000, 44_100, 16_000)
    for (source in sources) {
        for (rate in rates) {
            val minBytes = AudioRecord.getMinBufferSize(
                rate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (minBytes <= 0) continue
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
                    .setBufferSizeInBytes(minBytes * 2)
                    .build()
            } catch (_: IllegalArgumentException) {
                null
            } catch (_: UnsupportedOperationException) {
                null
            }
            if (recorder == null) continue
            if (recorder.state == AudioRecord.STATE_INITIALIZED) return recorder
            recorder.release()
        }
    }
    throw IllegalStateException("无法打开麦克风")
}
