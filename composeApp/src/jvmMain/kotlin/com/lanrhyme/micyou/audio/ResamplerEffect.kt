package com.lanrhyme.micyou.audio

import kotlin.math.abs

class ResamplerEffect : AudioEffect {
    var playbackRatio: Double = 1.0
    
    private var resamplePosFrames: Double = 0.0
    private var resamplePrevFrame: ShortArray = ShortArray(0)
    private var scratchResampledShorts: ShortArray = ShortArray(0)
    
    // Playback ratio control
    var playbackRatioIntegral: Double = 0.0
        private set

    override fun process(input: ShortArray, channelCount: Int): ShortArray {
        if (abs(playbackRatio - 1.0) < 0.00005) {
            return input
        }
        
        val processedShortCount = resampleInterleavedShorts(input, channelCount, playbackRatio)
        
        // If the count matches input size, it might be that we didn't resample enough or it was a pass-through
        // But for safety and API consistency, we return the valid part of scratch buffer
        return scratchResampledShorts.copyOf(processedShortCount)
    }
    
    fun updatePlaybackRatio(queuedMs: Long): Double {
        val targetMs = 60.0
        val errorMs = queuedMs.toDouble() - targetMs
        
        if (errorMs > 100) {
            playbackRatio = 1.10
            return playbackRatio
        } else if (errorMs < -100) {
            playbackRatio = 0.95
            return playbackRatio
        }
        
        val kP = 0.0002
        val kI = 0.000002
        val maxAdjust = 0.10
        
        var integral = (playbackRatioIntegral + errorMs).coerceIn(-10000.0, 10000.0)
        playbackRatioIntegral = integral
        val adjust = (errorMs * kP + integral * kI).coerceIn(-maxAdjust, maxAdjust)
        playbackRatio = (1.0 + adjust).coerceIn(1.0 - maxAdjust, 1.0 + maxAdjust)
        return playbackRatio
    }

    private fun resampleInterleavedShorts(processedShorts: ShortArray, channelCount: Int, ratio: Double): Int {
        if (channelCount <= 0) return 0
        val inputFrames = processedShorts.size / channelCount
        if (inputFrames <= 1) {
            // Not enough frames to interpolate, just copy to scratch
            if (scratchResampledShorts.size < processedShorts.size) {
                scratchResampledShorts = ShortArray(processedShorts.size)
            }
            System.arraycopy(processedShorts, 0, scratchResampledShorts, 0, processedShorts.size)
            return processedShorts.size
        }

        if (resamplePrevFrame.size != channelCount) {
            resamplePrevFrame = ShortArray(channelCount)
            for (c in 0 until channelCount) {
                resamplePrevFrame[c] = processedShorts[c]
            }
            resamplePosFrames = 1.0
        }

        val effectiveFrames = inputFrames + 1
        var outFrames = 0
        var pos = resamplePosFrames
        
        val estimatedOutFrames = ((inputFrames.toDouble() / ratio) + 4.0).toInt().coerceAtLeast(8)
        val neededShorts = estimatedOutFrames * channelCount
        if (scratchResampledShorts.size < neededShorts) {
            scratchResampledShorts = ShortArray(neededShorts)
        }

        fun sample(frameIndex: Int, channel: Int): Int {
            return if (frameIndex == 0) {
                resamplePrevFrame[channel].toInt()
            } else {
                processedShorts[(frameIndex - 1) * channelCount + channel].toInt()
            }
        }

        while (true) {
            val base = pos.toInt()
            if (base + 1 >= effectiveFrames) break
            val frac = pos - base.toDouble()

            val outBase = outFrames * channelCount
            for (c in 0 until channelCount) {
                val s0 = sample(base, c)
                val s1 = sample(base + 1, c)
                val v = (s0 + (s1 - s0) * frac).toInt().coerceIn(-32768, 32767)
                scratchResampledShorts[outBase + c] = v.toShort()
            }

            outFrames++
            pos += ratio

            val required = (outFrames + 1) * channelCount
            if (required > scratchResampledShorts.size) {
                scratchResampledShorts = scratchResampledShorts.copyOf((scratchResampledShorts.size * 2).coerceAtLeast(required))
            }
        }

        val lastFrameOffset = (inputFrames - 1) * channelCount
        for (c in 0 until channelCount) {
            resamplePrevFrame[c] = processedShorts[lastFrameOffset + c]
        }

        resamplePosFrames = pos - inputFrames.toDouble()

        return outFrames * channelCount
    }

    override fun reset() {
        resamplePosFrames = 0.0
        resamplePrevFrame = ShortArray(0)
        playbackRatioIntegral = 0.0
        playbackRatio = 1.0
    }

    override fun release() {
        reset()
    }
}
