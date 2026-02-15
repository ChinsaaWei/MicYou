package com.lanrhyme.micyou.audio

/**
 * Coordinates the audio processing pipeline.
 * Holds instances of various audio effects and executes them in sequence.
 */
class AudioProcessorPipeline {
    val noiseReducer = NoiseReducer()
    val dereverbEffect = DereverbEffect()
    val agcEffect = AGCEffect()
    val vadEffect = VADEffect()
    val amplifierEffect = AmplifierEffect()
    val resamplerEffect = ResamplerEffect()

    private val effects: List<AudioEffect> = listOf(
        noiseReducer,
        dereverbEffect,
        agcEffect,
        vadEffect,
        amplifierEffect,
        resamplerEffect
    )

    /**
     * Process a frame of audio through the pipeline.
     * Sequence: Noise Reduction -> Dereverb -> AGC -> VAD -> Amplification -> Resampling.
     *
     * @param input Input audio samples (interleaved if stereo).
     * @param channelCount Number of channels.
     * @return Processed audio samples.
     */
    fun process(input: ShortArray, channelCount: Int): ShortArray {
        var currentBuffer = input

        // Pass the buffer through each effect in the defined order
        // Note: Some effects might modify the buffer in-place, while others (like Resampler) might return a new buffer.
        
        currentBuffer = noiseReducer.process(currentBuffer, channelCount)
        currentBuffer = dereverbEffect.process(currentBuffer, channelCount)
        currentBuffer = agcEffect.process(currentBuffer, channelCount)
        currentBuffer = vadEffect.process(currentBuffer, channelCount)
        currentBuffer = amplifierEffect.process(currentBuffer, channelCount)
        currentBuffer = resamplerEffect.process(currentBuffer, channelCount)

        return currentBuffer
    }

    /**
     * Reset the state of all effects in the pipeline.
     */
    fun reset() {
        effects.forEach { it.reset() }
    }

    /**
     * Release resources held by all effects in the pipeline.
     */
    fun release() {
        effects.forEach { it.release() }
    }
}
