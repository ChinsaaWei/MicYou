package com.lanrhyme.micyou.audio

import kotlin.math.sqrt

class AGCEffect : AudioEffect {
    var enableAGC: Boolean = false
    var agcTargetLevel: Int = 32000

    private var agcEnvelope: Float = 0f

    override fun process(input: ShortArray, channelCount: Int): ShortArray {
        if (!enableAGC || agcTargetLevel <= 0) return input

        var sumSquares = 0.0
        for (s in input) {
            val sample = s.toDouble() / 32768.0
            sumSquares += sample * sample
        }
        val rms = sqrt(sumSquares / input.size.toDouble())
        
        val targetRms = (agcTargetLevel.toDouble() / 32768.0).coerceIn(0.01, 0.9)
        
        if (rms > 0.001) {
            val error = targetRms / (rms + 1e-6)
            val desiredGain = error.toFloat().coerceIn(0.5f, 5.0f)
            
            if (agcEnvelope == 0f) {
                agcEnvelope = 1.0f
            }
            
            val smoothing = if (desiredGain < agcEnvelope) {
                0.005f 
            } else {
                0.01f  
            }
            agcEnvelope = agcEnvelope * (1f - smoothing) + desiredGain * smoothing
        } else {
            agcEnvelope = agcEnvelope * 0.999f + 1.0f * 0.001f
        }
        
        val finalGain = agcEnvelope.coerceIn(0.8f, 5.0f)
        
        for (i in input.indices) {
            val v = (input[i].toInt() * finalGain).toInt().coerceIn(-32768, 32767)
            input[i] = v.toShort()
        }
        return input
    }

    override fun reset() {
        agcEnvelope = 0f
    }

    override fun release() {
        reset()
    }
}
