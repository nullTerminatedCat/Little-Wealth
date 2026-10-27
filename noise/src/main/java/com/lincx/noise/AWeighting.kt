package com.lincx.noise

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * IEC 61672-1 的 A 计权曲线，用双线性变换做成采样率上的数字滤波器，
 * 并强制 1 kHz 增益为 0 dB。
 *
 * 48 kHz 下这条曲线在常用频段能贴近标准设计目标。它只描述频率计权，
 * 不包含麦克风灵敏度，也不能把手机变成计量声级计。
 */
internal class AWeightingFilter(private val sampleRate: Int) {
    private val sections: List<Section>
    private val outputGain: Double

    init {
        require(sampleRate > 2_000) { "采样率过低，无法做 A 计权" }
        val designed = design(sampleRate.toDouble())
        sections = designed.sections
        outputGain = designed.gain
    }

    fun reset() {
        for (section in sections) section.reset()
    }

    fun process(sample: Double): Double {
        var y = sample
        for (section in sections) y = section.process(y)
        return y * outputGain
    }

    /** 数字滤波器相对 1 的幅度，换算成分贝。1 kHz 应约为 0。 */
    fun responseDb(frequencyHz: Double): Double {
        val mag = magnitude(sections, outputGain, frequencyHz, sampleRate.toDouble())
        if (mag <= 1e-20) return -200.0
        return 20.0 * log10(mag)
    }

    private class Section(
        val b0: Double,
        val b1: Double,
        val b2: Double,
        val a1: Double,
        val a2: Double,
    ) {
        private var s1 = 0.0
        private var s2 = 0.0

        fun reset() {
            s1 = 0.0
            s2 = 0.0
        }

        fun process(x: Double): Double {
            val y = b0 * x + s1
            s1 = b1 * x - a1 * y + s2
            s2 = b2 * x - a2 * y
            return y
        }
    }

    private data class Designed(val sections: List<Section>, val gain: Double)

    companion object {
        private const val F1 = 20.598997057568145
        private const val F2 = 107.65264864304628
        private const val F3 = 737.8622307362899
        private const val F4 = 12194.21714799801

        /**
         * 标准里的 A 计权设计目标（模拟曲线），相对 1 kHz。
         * 数字滤波器应在 48 kHz 上贴近它，但两者不会逐点重合。
         */
        fun designDb(frequencyHz: Double): Double {
            require(frequencyHz > 0.0)
            return 20.0 * log10(rawResponse(frequencyHz) / rawResponse(1_000.0))
        }

        private fun rawResponse(frequencyHz: Double): Double {
            val f2 = frequencyHz * frequencyHz
            val numerator = (F4 * F4) * (f2 * f2)
            val denominator = (f2 + F1 * F1) *
                sqrt((f2 + F2 * F2) * (f2 + F3 * F3)) *
                (f2 + F4 * F4)
            return numerator / denominator
        }

        private fun design(sampleRate: Double): Designed {
            val poles = doubleArrayOf(
                -2.0 * PI * F1,
                -2.0 * PI * F1,
                -2.0 * PI * F4,
                -2.0 * PI * F4,
                -2.0 * PI * F2,
                -2.0 * PI * F3,
            )
            val omega = 2.0 * PI * 1_000.0
            var analogMagnitude = omega * omega * omega * omega
            for (pole in poles) {
                analogMagnitude /= sqrt(omega * omega + pole * pole)
            }
            val analogGain = 1.0 / analogMagnitude

            val twiceRate = 2.0 * sampleRate
            var zeroProduct = 1.0
            repeat(4) { zeroProduct *= twiceRate }
            var poleProduct = 1.0
            for (pole in poles) poleProduct *= (twiceRate - pole)
            val digitalGain = analogGain * zeroProduct / poleProduct

            fun mapped(analogPoleHz: Double): Double {
                val analog = -2.0 * PI * analogPoleHz
                return (twiceRate + analog) / (twiceRate - analog)
            }

            val low = realSection(zeroAt = 1.0, mapped(F1), mapped(F1), gain = 1.0)
            val mid = realSection(zeroAt = 1.0, mapped(F2), mapped(F3), gain = 1.0)
            val high = realSection(zeroAt = -1.0, mapped(F4), mapped(F4), gain = digitalGain)
            val sections = listOf(low, mid, high)
            val atOneK = magnitude(sections, gain = 1.0, frequencyHz = 1_000.0, sampleRate = sampleRate)
            return Designed(sections, gain = 1.0 / atOneK)
        }

        private fun realSection(zeroAt: Double, poleA: Double, poleB: Double, gain: Double): Section {
            return Section(
                b0 = gain,
                b1 = gain * (-2.0 * zeroAt),
                b2 = gain * (zeroAt * zeroAt),
                a1 = -(poleA + poleB),
                a2 = poleA * poleB,
            )
        }

        private fun magnitude(
            sections: List<Section>,
            gain: Double,
            frequencyHz: Double,
            sampleRate: Double,
        ): Double {
            val angle = 2.0 * PI * frequencyHz / sampleRate
            val z1r = cos(angle)
            val z1i = -sin(angle)
            val z2r = z1r * z1r - z1i * z1i
            val z2i = 2.0 * z1r * z1i
            var mag = kotlin.math.abs(gain)
            for (section in sections) {
                val nr = section.b0 + section.b1 * z1r + section.b2 * z2r
                val ni = section.b1 * z1i + section.b2 * z2i
                val dr = 1.0 + section.a1 * z1r + section.a2 * z2r
                val di = section.a1 * z1i + section.a2 * z2i
                mag *= sqrt(nr * nr + ni * ni) / sqrt(dr * dr + di * di)
            }
            return mag
        }
    }
}
