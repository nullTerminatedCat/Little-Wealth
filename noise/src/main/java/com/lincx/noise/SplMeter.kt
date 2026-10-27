package com.lincx.noise

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * 一次测量会话：A 计权、快档平滑、会话峰值、会话 Leq。
 *
 * 快档按 IEC 61672 的时间计权，对 A 计权后的平方做指数平均，时间常数 0.125 秒。
 * Leq 是会话开始后的能量平均。峰值是 A 计权采样绝对值的最大，不是快档的最大。
 * 偏移只在换成 dB 时加上，线性量始终不带偏移。
 */
internal class SplMeter {
    private val lock = Any()
    private var filter: AWeightingFilter? = null
    private var filterRate = 0
    private var generation = 0
    private var offsetDb = 0.0
    private var fastMs = 0.0
    private var fastReady = false
    private var energySum = 0.0
    private var energyCount = 0L
    private var sessionPeak = 0.0
    private val recent = ArrayDeque<Bucket>()
    private var recentSeconds = 0.0

    fun process(samples: ShortArray, count: Int, sampleRate: Int): SplFrame = synchronized(lock) {
        val n = minOf(count, samples.size).coerceAtLeast(0)
        val frameGeneration = generation
        val offset = offsetDb
        if (n == 0 || sampleRate <= 0) {
            return@synchronized snapshot(frameGeneration, offset, 0.0, rawSilent = true, clipped = false, sampleRate)
        }
        val weighting = filterFor(sampleRate)
        val alpha = exp(-1.0 / (sampleRate * FAST_SECONDS))
        val blend = 1.0 - alpha
        var sumSquares = 0.0
        var windowPeak = 0.0
        var rawPeak = 0
        var sawSample = false
        for (i in 0 until n) {
            val raw = samples[i].toInt()
            val absRaw = abs(raw)
            if (absRaw > rawPeak) rawPeak = absRaw
            if (absRaw != 0) sawSample = true
            val weighted = weighting.process(raw.toDouble())
            val magnitude = abs(weighted)
            if (magnitude > windowPeak) windowPeak = magnitude
            if (magnitude > sessionPeak) sessionPeak = magnitude
            val square = weighted * weighted
            sumSquares += square
            fastMs = alpha * fastMs + blend * square
        }
        fastReady = true
        energySum += sumSquares
        energyCount += n
        pushRecent(sumSquares, n, n.toDouble() / sampleRate)
        snapshot(
            generation = frameGeneration,
            offsetDb = offset,
            windowPeak = windowPeak,
            rawSilent = !sawSample,
            clipped = rawPeak >= PCM_CLIP,
            sampleRate = sampleRate,
        )
    }

    fun resetSession() = synchronized(lock) {
        generation += 1
        fastMs = 0.0
        fastReady = false
        energySum = 0.0
        energyCount = 0L
        sessionPeak = 0.0
        recent.clear()
        recentSeconds = 0.0
        filter?.reset()
    }

    /** 把峰值保持改到当前快档，方便用户丢掉前面的突发。 */
    fun rebasePeakToFast() = synchronized(lock) {
        generation += 1
        sessionPeak = if (fastReady) sqrt(fastMs.coerceAtLeast(0.0)) else 0.0
    }

    fun setOffsetDb(offset: Double) = synchronized(lock) {
        offsetDb = offset.coerceIn(-DbLevel.MAX_OFFSET_DB, DbLevel.MAX_OFFSET_DB)
    }

    fun offsetDb(): Double = synchronized(lock) { offsetDb }

    fun generation(): Int = synchronized(lock) { generation }

    fun recentSeconds(): Double = synchronized(lock) { recentSeconds }

    fun recentUncalibratedLeqDb(): Double? = synchronized(lock) { recentLeqUnlocked() }

    private fun filterFor(sampleRate: Int): AWeightingFilter {
        val current = filter
        if (current != null && filterRate == sampleRate) return current
        val created = AWeightingFilter(sampleRate)
        filter = created
        filterRate = sampleRate
        return created
    }

    private fun pushRecent(sumSquares: Double, count: Int, seconds: Double) {
        recent.addLast(Bucket(seconds, sumSquares, count))
        recentSeconds += seconds
        while (recent.size > 1 && recentSeconds - recent.first().seconds >= RECENT_SECONDS) {
            val dropped = recent.removeFirst()
            recentSeconds -= dropped.seconds
        }
    }

    private fun recentLeqUnlocked(): Double? {
        if (recentSeconds < DbLevel.QUIET_ROOM_MIN_SECONDS) return null
        var sum = 0.0
        var count = 0
        for (bucket in recent) {
            sum += bucket.sumSquares
            count += bucket.count
        }
        if (count <= 0) return null
        return DbLevel.unclampedMeanSquareDb(sum / count)
    }

    private fun snapshot(
        generation: Int,
        offsetDb: Double,
        windowPeak: Double,
        rawSilent: Boolean,
        clipped: Boolean,
        sampleRate: Int,
    ): SplFrame {
        val fast = if (fastReady) DbLevel.fromMeanSquare(fastMs, offsetDb) else 0.0
        val leq = if (energyCount > 0L) {
            DbLevel.fromMeanSquare(energySum / energyCount.toDouble(), offsetDb)
        } else {
            0.0
        }
        return SplFrame(
            generation = generation,
            fastDb = fast,
            windowPeakDb = DbLevel.fromAmplitude(windowPeak, offsetDb),
            sessionPeakDb = DbLevel.fromAmplitude(sessionPeak, offsetDb),
            leqDb = leq,
            offsetDb = offsetDb,
            rawSilent = rawSilent,
            clipped = clipped,
            sampleRate = sampleRate,
            recentSeconds = recentSeconds,
        )
    }

    private data class Bucket(val seconds: Double, val sumSquares: Double, val count: Int)

    private companion object {
        const val FAST_SECONDS = 0.125
        const val RECENT_SECONDS = 2.0
        const val PCM_CLIP = 32_767
    }
}

internal data class SplFrame(
    val generation: Int,
    val fastDb: Double,
    val windowPeakDb: Double,
    val sessionPeakDb: Double,
    val leqDb: Double,
    val offsetDb: Double,
    val rawSilent: Boolean,
    val clipped: Boolean,
    val sampleRate: Int,
    val recentSeconds: Double,
)
