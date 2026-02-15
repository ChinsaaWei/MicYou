package com.lanrhyme.micyou.audio

/**
 * Interface for audio processing components (effects).
 */
interface AudioEffect {
    /**
     * Process audio samples.
     * @param input Input buffer.
     * @param channelCount Number of audio channels.
     * @return Processed buffer (may be the same array or a new one).
     */
    fun process(input: ShortArray, channelCount: Int): ShortArray
    
    /**
     * Reset internal state (e.g. clear buffers, reset history).
     */
    fun reset()
    
    /**
     * Release resources (e.g. close native handles).
     */
    fun release()
}
