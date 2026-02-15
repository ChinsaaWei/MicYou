package com.lanrhyme.micyou.audio

class AmplifierEffect : AudioEffect {
    var amplification: Float = 1.0f

    override fun process(input: ShortArray, channelCount: Int): ShortArray {
        if (amplification == 1.0f) return input

        for (i in input.indices) {
            val sample = input[i].toInt()
            val amplified = (sample * amplification).toInt()
            input[i] = amplified.coerceIn(-32768, 32767).toShort()
        }
        return input
    }

    override fun reset() {
    }

    override fun release() {
    }
}
