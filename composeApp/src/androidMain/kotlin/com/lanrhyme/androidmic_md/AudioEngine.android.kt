package com.lanrhyme.androidmic_md

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.app.ActivityCompat
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.protobuf.*

actual class AudioEngine actual constructor() {
    private val _state = MutableStateFlow(StreamState.Idle)
    actual val streamState: Flow<StreamState> = _state
    private val _audioLevels = MutableStateFlow(0f)
    actual val audioLevels: Flow<Float> = _audioLevels
    private val _lastError = MutableStateFlow<String?>(null)
    actual val lastError: Flow<String?> = _lastError
    private var job: Job? = null
    private val startStopMutex = Mutex()
    private val proto = ProtoBuf { }

    private val CHECK_1 = "AndroidMic1"
    private val CHECK_2 = "AndroidMic2"

    actual suspend fun start(
        ip: String, 
        port: Int, 
        mode: ConnectionMode, 
        isClient: Boolean,
        sampleRate: SampleRate,
        channelCount: ChannelCount,
        audioFormat: com.lanrhyme.androidmic_md.AudioFormat
    ) {
        if (!isClient) return
        _lastError.value = null

        val jobToJoin = startStopMutex.withLock {
            val currentJob = job
            if (currentJob != null && !currentJob.isCompleted) {
                null
            } else {
                _state.value = StreamState.Connecting
                CoroutineScope(Dispatchers.IO).launch {
                    var socket: Socket? = null
                    var recorder: AudioRecord? = null
                    
                    try {
                        // 音频设置
                        val androidSampleRate = sampleRate.value
                        val androidChannelConfig = if (channelCount == ChannelCount.Stereo) 
                            AudioFormat.CHANNEL_IN_STEREO 
                        else 
                            AudioFormat.CHANNEL_IN_MONO
                            
                        val androidAudioFormat = when(audioFormat) {
                            com.lanrhyme.androidmic_md.AudioFormat.PCM_8BIT -> AudioFormat.ENCODING_PCM_8BIT
                            com.lanrhyme.androidmic_md.AudioFormat.PCM_16BIT -> AudioFormat.ENCODING_PCM_16BIT
                            com.lanrhyme.androidmic_md.AudioFormat.PCM_FLOAT -> AudioFormat.ENCODING_PCM_FLOAT
                            else -> AudioFormat.ENCODING_PCM_16BIT // Default fallback
                        }
                        
                        val minBufSize = AudioRecord.getMinBufferSize(androidSampleRate, androidChannelConfig, androidAudioFormat)

                        recorder = try {
                            AudioRecord(
                                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                                androidSampleRate,
                                androidChannelConfig,
                                androidAudioFormat,
                                minBufSize * 2
                            )
                        } catch (e: SecurityException) {
                            e.printStackTrace()
                            _state.value = StreamState.Error
                            _lastError.value = "录音权限不足"
                            return@launch
                        }

                        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                            val msg = "AudioRecord 初始化失败"
                            println(msg)
                            _state.value = StreamState.Error
                            _lastError.value = msg
                            return@launch
                        }
                        
                        // 网络设置
                        val selectorManager = SelectorManager(Dispatchers.IO)
                        val socketBuilder = aSocket(selectorManager)
                        
                        if (mode == ConnectionMode.WifiUdp) {
                             throw UnsupportedOperationException("UDP Not fully implemented yet")
                        } else {
                            socket = socketBuilder.tcp().connect(ip, port)
                        }
                        
                        val output = socket!!.openWriteChannel(autoFlush = true)
                        val input = socket!!.openReadChannel()

                        // 握手
                        output.writeFully(CHECK_1.encodeToByteArray())
                        output.flush()

                        val responseBuffer = ByteArray(CHECK_2.length)
                        input.readFully(responseBuffer, 0, responseBuffer.size)

                        if (!responseBuffer.decodeToString().equals(CHECK_2)) {
                            val msg = "握手失败"
                            println(msg)
                            _state.value = StreamState.Error
                            _lastError.value = msg
                            socket.close()
                            return@launch
                        }

                        recorder.startRecording()
                        _state.value = StreamState.Streaming
                        _lastError.value = null

                        val buffer = ByteArray(minBufSize)
                        
                        while (isActive) {
                            val readBytes = recorder.read(buffer, 0, buffer.size)
                            if (readBytes > 0) {
                                val audioData = buffer.copyOfRange(0, readBytes)
                                
                                // 计算电平
                                val rms = calculateRMS(audioData)
                                _audioLevels.value = rms

                                // 创建数据包
                                val packet = AudioPacketMessage(
                                    buffer = audioData,
                                    sampleRate = androidSampleRate,
                                    channelCount = if (channelCount == ChannelCount.Stereo) 2 else 1,
                                    audioFormat = when(audioFormat) {
                                        com.lanrhyme.androidmic_md.AudioFormat.PCM_8BIT -> 8
                                        com.lanrhyme.androidmic_md.AudioFormat.PCM_16BIT -> 16
                                        com.lanrhyme.androidmic_md.AudioFormat.PCM_FLOAT -> 32
                                    }
                                )
                                
                                // 序列化
                                val packetBytes = proto.encodeToByteArray(AudioPacketMessage.serializer(), packet)
                                
                                // 写入长度 (大端序)
                                val length = packetBytes.size
                                output.writeByte((length shr 24).toByte())
                                output.writeByte((length shr 16).toByte())
                                output.writeByte((length shr 8).toByte())
                                output.writeByte(length.toByte())
                                
                                // 写入数据
                                output.writeFully(packetBytes)
                            }
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (isActive) {
                            e.printStackTrace()
                            _state.value = StreamState.Error
                            _lastError.value = "连接断开: ${e.message}"
                        }
                    } finally {
                        try {
                            recorder?.stop()
                            recorder?.release()
                            socket?.close()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        _state.value = StreamState.Idle
                    }
                }.also { job = it }
            }
        }
        jobToJoin?.join()
    }
    
    actual fun updateConfig(
        enableNS: Boolean,
        nsType: NoiseReductionType,
        enableAGC: Boolean,
        agcTargetLevel: Int,
        enableVAD: Boolean,
        vadThreshold: Int,
        enableDereverb: Boolean,
        dereverbLevel: Float,
        amplification: Float
    ) {
        // Android 端无需处理，功能仅存在于桌面端
    }
    
    private fun calculateRMS(buffer: ByteArray): Float {
        var sum = 0.0
        for (i in 0 until buffer.size step 2) {
             if (i+1 >= buffer.size) break
             val sample = ((buffer[i+1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort()
             sum += sample * sample
        }
        val mean = sum / (buffer.size / 2)
        val root = kotlin.math.sqrt(mean)
        return (root / 32768.0).toFloat().coerceIn(0f, 1f)
    }

    actual fun stop() {
        CoroutineScope(Dispatchers.IO).launch {
            startStopMutex.withLock {
                job?.cancelAndJoin()
                job = null
            }
        }
    }

    actual fun setMonitoring(enabled: Boolean) {
        // Android 端不需要本地监听
    }
    
    actual val installProgress: Flow<String?> = kotlinx.coroutines.flow.flowOf(null)
    
    actual suspend fun installDriver() {
        // Android 端不需要安装驱动
    }
}
