package com.lanrhyme.micyou.audio

import com.lanrhyme.micyou.AudioProcessor
import com.lanrhyme.micyou.NoiseReductionType
import de.maxhenkel.rnnoise4j.Denoiser
import java.io.File
import java.io.IOException
import java.nio.file.Files

class NoiseReducer(
    private val frameSize: Int = 480
) : AudioEffect {
    
    var enableNS: Boolean = false
    var nsType: NoiseReductionType = NoiseReductionType.Ulunas

    // RNNoise instances
    private var denoiserLeft: Denoiser? = null
    private var denoiserRight: Denoiser? = null
    private var rnnoiseFrameLeft: ShortArray = ShortArray(0)
    private var rnnoiseFrameRight: ShortArray = ShortArray(0)

    // Ulunas instances
    private var ulunasProcessorLeft: AudioProcessor? = null
    private var ulunasProcessorRight: AudioProcessor? = null
    private var ulunasFrameLeft: FloatArray = FloatArray(0)
    private var ulunasFrameRight: FloatArray = FloatArray(0)
    private var ulunasModelPath: String? = null

    var speechProbability: Float? = null
        private set

    override fun process(input: ShortArray, channelCount: Int): ShortArray {
        if (!enableNS || channelCount <= 0) return input
        
        when (nsType) {
            NoiseReductionType.RNNoise -> processRNNoise(input, channelCount)
            NoiseReductionType.Ulunas -> processUlunas(input, channelCount)
            else -> {}
        }
        return input
    }

    private fun processRNNoise(input: ShortArray, channelCount: Int) {
        if (denoiserLeft == null) denoiserLeft = Denoiser()
        if (channelCount >= 2 && denoiserRight == null) denoiserRight = Denoiser()

        val framesPerChannel = input.size / channelCount
        val frameCount = framesPerChannel / frameSize

        if (frameCount > 0 && (channelCount == 1 || channelCount == 2)) {
            if (rnnoiseFrameLeft.size != frameSize) rnnoiseFrameLeft = ShortArray(frameSize)
            if (channelCount == 2 && rnnoiseFrameRight.size != frameSize) rnnoiseFrameRight = ShortArray(frameSize)
            
            val left = rnnoiseFrameLeft
            val right = if (channelCount == 2) rnnoiseFrameRight else null

            var probSum = 0f
            var probN = 0

            for (f in 0 until frameCount) {
                val base = f * frameSize * channelCount
                if (channelCount == 1) {
                    for (i in 0 until frameSize) {
                        left[i] = input[base + i]
                    }
                    val p = denoiserLeft!!.denoiseInPlace(left)
                    for (i in 0 until frameSize) {
                        input[base + i] = left[i]
                    }
                    probSum += p
                    probN++
                } else {
                    for (i in 0 until frameSize) {
                        val idx = base + i * 2
                        left[i] = input[idx]
                        right!![i] = input[idx + 1]
                    }
                    val pL = denoiserLeft!!.denoiseInPlace(left)
                    val pR = denoiserRight!!.denoiseInPlace(right!!)
                    for (i in 0 until frameSize) {
                        val idx = base + i * 2
                        input[idx] = left[i]
                        input[idx + 1] = right!![i]
                    }
                    probSum += ((pL + pR) * 0.5f)
                    probN++
                }
            }

            if (probN > 0) {
                speechProbability = probSum / probN.toFloat()
            }
        }
    }

    private fun processUlunas(input: ShortArray, channelCount: Int) {
        val modelPath = getUlnasModelPath()
        
        // Ulunas hop length is same as frameSize in this context (480)
        val hopLength = frameSize 
        
        if (ulunasProcessorLeft == null) {
            ulunasProcessorLeft = AudioProcessor(0f, 0f, modelPath, 960, hopLength.toLong())
        }
        if (channelCount >= 2 && ulunasProcessorRight == null) {
            ulunasProcessorRight = AudioProcessor(0f, 0f, modelPath, 960, hopLength.toLong())
        }

        val framesPerChannel = input.size / channelCount
        val frameCount = framesPerChannel / hopLength

        if (frameCount > 0 && (channelCount == 1 || channelCount == 2)) {
            if (ulunasFrameLeft.size != hopLength) ulunasFrameLeft = FloatArray(hopLength)
            if (channelCount == 2 && ulunasFrameRight.size != hopLength) ulunasFrameRight = FloatArray(hopLength)
            
            val left = ulunasFrameLeft
            val right = if (channelCount == 2) ulunasFrameRight else null

            for (f in 0 until frameCount) {
                val base = f * hopLength * channelCount
                if (channelCount == 1) {
                    for (i in 0 until hopLength) {
                        left[i] = input[base + i] / 32768.0f
                    }
                    val processedLeft = ulunasProcessorLeft!!.process(left)
                    for (i in 0 until hopLength) {
                        input[base + i] = (processedLeft[i] * 32767.0f).toInt().coerceIn(-32768, 32767).toShort()
                    }
                } else {
                    for (i in 0 until hopLength) {
                        val idx = base + i * 2
                        left[i] = input[idx] / 32768.0f
                        right!![i] = input[idx + 1] / 32768.0f
                    }
                    val processedLeft = ulunasProcessorLeft!!.process(left)
                    val processedRight = ulunasProcessorRight!!.process(right!!)
                    for (i in 0 until hopLength) {
                        val idx = base + i * 2
                        input[idx] = (processedLeft[i] * 32767.0f).toInt().coerceIn(-32768, 32767).toShort()
                        input[idx + 1] = (processedRight[i] * 32767.0f).toInt().coerceIn(-32768, 32767).toShort()
                    }
                }
            }
        }
    }
    
    private fun getUlnasModelPath(): String {
        ulunasModelPath?.let { return it }
        
        System.getProperty("micyou.ulunas.model.path")?.let {
            ulunasModelPath = it
            return it
        }

        val tempDir = Files.createTempDirectory("micyou")
        val modelFile = tempDir.resolve("ulunas.onnx").toFile()

        if (modelFile.exists() && modelFile.length() > 0) {
            ulunasModelPath = modelFile.absolutePath
            return modelFile.absolutePath
        }

        val classLoader = this.javaClass.classLoader
        val resourceStream = classLoader.getResourceAsStream("models/ulunas.onnx")
            ?: throw IOException("Unable to find Ulunas model file: models/ulunas.onnx")

        resourceStream.use { input ->
            modelFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        modelFile.deleteOnExit()
        ulunasModelPath = modelFile.absolutePath
        return modelFile.absolutePath
    }

    override fun reset() {
        // No explicit reset logic for native instances, keeping them alive
    }

    override fun release() {
        try {
            denoiserLeft?.close()
            denoiserLeft = null
            denoiserRight?.close()
            denoiserRight = null
            ulunasProcessorLeft?.destroy()
            ulunasProcessorLeft = null
            ulunasProcessorRight?.destroy()
            ulunasProcessorRight = null
        } catch (e: Exception) {
            // Ignore close errors
        }
    }
}
