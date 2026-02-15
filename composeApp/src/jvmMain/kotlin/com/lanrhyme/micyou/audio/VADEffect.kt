package com.lanrhyme.micyou.audio

import kotlin.math.sqrt

class VADEffect : AudioEffect {
    var enableVAD: Boolean = false
    var vadThreshold: Int = 10
    var speechProbability: Float? = null

    override fun process(input: ShortArray, channelCount: Int): ShortArray {
        if (!enableVAD) return input

        val sensitivity = vadThreshold.coerceIn(0, 100) / 100f
        val requiredConfidence = 1f - sensitivity
        
        val speech = speechProbability?.let { it >= requiredConfidence } ?: run {
            var sum = 0.0
            for (s in input) {
                val n = s.toDouble() / 32768.0
                sum += n * n
            }
            val rms = if (input.isNotEmpty()) sqrt(sum / input.size.toDouble()).toFloat() else 0f
            rms >= (requiredConfidence * 0.12f)
        }
        
        if (!speech) {
            for (i in input.indices) {
                input[i] = 0
            }
        }
        return input
    }

    override fun reset() {
        speechProbability = null
    }

    override fun release() {
        reset()
    }
}
