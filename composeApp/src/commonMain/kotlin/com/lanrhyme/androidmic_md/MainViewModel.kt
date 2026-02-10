package com.lanrhyme.androidmic_md

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ConnectionMode(val label: String) {
    Wifi( "Wi-Fi (TCP)"),
    WifiUdp("Wi-Fi (UDP)"),
    Usb("USB (ADB)")
}

enum class StreamState {
    Idle, Connecting, Streaming, Error
}

data class AppUiState(
    val mode: ConnectionMode = ConnectionMode.Wifi,
    val streamState: StreamState = StreamState.Idle,
    val ipAddress: String = "192.168.1.5", // 默认 IP
    val port: String = "6000",
    val errorMessage: String? = null,
    val themeMode: ThemeMode = ThemeMode.System,
    val seedColor: Long = 0xFF6750A4, // Default purple
    val monitoringEnabled: Boolean = false,
    val sampleRate: SampleRate = SampleRate.Rate44100,
    val channelCount: ChannelCount = ChannelCount.Mono,
    val audioFormat: AudioFormat = AudioFormat.PCM_16BIT,
    val installMessage: String? = null
)

class MainViewModel : ViewModel() {
    private val audioEngine = AudioEngine()
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()
    val audioLevels = audioEngine.audioLevels
    private val settings = SettingsFactory.getSettings()

    init {
        // Load settings
        val savedModeName = settings.getString("connection_mode", ConnectionMode.Wifi.name)
        val savedMode = try { ConnectionMode.valueOf(savedModeName) } catch(e: Exception) { ConnectionMode.Wifi }
        
        val savedIp = settings.getString("ip_address", "192.168.1.5")
        val savedPort = settings.getString("port", "6000")
        
        val savedThemeModeName = settings.getString("theme_mode", ThemeMode.System.name)
        val savedThemeMode = try { ThemeMode.valueOf(savedThemeModeName) } catch(e: Exception) { ThemeMode.System }
        
        val savedSeedColor = settings.getLong("seed_color", 0xFF6750A4)
        
        val savedMonitoring = settings.getBoolean("monitoring_enabled", false)

        val savedSampleRateName = settings.getString("sample_rate", SampleRate.Rate44100.name)
        val savedSampleRate = try { SampleRate.valueOf(savedSampleRateName) } catch(e: Exception) { SampleRate.Rate44100 }

        val savedChannelCountName = settings.getString("channel_count", ChannelCount.Mono.name)
        val savedChannelCount = try { ChannelCount.valueOf(savedChannelCountName) } catch(e: Exception) { ChannelCount.Mono }

        val savedAudioFormatName = settings.getString("audio_format", AudioFormat.PCM_16BIT.name)
        val savedAudioFormat = try { AudioFormat.valueOf(savedAudioFormatName) } catch(e: Exception) { AudioFormat.PCM_16BIT }

        _uiState.update { 
            it.copy(
                mode = savedMode,
                ipAddress = savedIp,
                port = savedPort,
                themeMode = savedThemeMode,
                seedColor = savedSeedColor,
                monitoringEnabled = savedMonitoring,
                sampleRate = savedSampleRate,
                channelCount = savedChannelCount,
                audioFormat = savedAudioFormat
            ) 
        }
        
        audioEngine.setMonitoring(savedMonitoring)

        viewModelScope.launch {
            audioEngine.streamState.collect { state ->
                _uiState.update { it.copy(streamState = state) }
            }
        }
        
        viewModelScope.launch {
            audioEngine.lastError.collect { error ->
                if (error != null) {
                     _uiState.update { it.copy(errorMessage = error) }
                }
            }
        }
        
        viewModelScope.launch {
            audioEngine.installProgress.collect { msg ->
                _uiState.update { it.copy(installMessage = msg) }
            }
        }

        if (getPlatform().type == PlatformType.Desktop) {
            viewModelScope.launch {
                audioEngine.installDriver()
            }
            startStream()
        }
    }

    fun toggleStream() {
        if (_uiState.value.streamState == StreamState.Streaming || _uiState.value.streamState == StreamState.Connecting) {
            stopStream()
        } else {
            startStream()
        }
    }

    fun startStream() {
        val ip = _uiState.value.ipAddress
        val port = _uiState.value.port.toIntOrNull() ?: 6000
        val mode = _uiState.value.mode
        val isClient = getPlatform().type == PlatformType.Android
        val sampleRate = _uiState.value.sampleRate
        val channelCount = _uiState.value.channelCount
        val audioFormat = _uiState.value.audioFormat

        viewModelScope.launch {
            audioEngine.start(ip, port, mode, isClient, sampleRate, channelCount, audioFormat)
        }
    }

    fun stopStream() {
        audioEngine.stop()
    }

    fun setMode(mode: ConnectionMode) {
        _uiState.update { it.copy(mode = mode) }
        settings.putString("connection_mode", mode.name)
    }
    
    fun setIp(ip: String) {
        _uiState.update { it.copy(ipAddress = ip) }
        settings.putString("ip_address", ip)
    }

    fun setPort(port: String) {
        _uiState.update { it.copy(port = port) }
        settings.putString("port", port)
    }

    fun setThemeMode(mode: ThemeMode) {
        _uiState.update { it.copy(themeMode = mode) }
        settings.putString("theme_mode", mode.name)
    }

    fun setSeedColor(color: Long) {
        _uiState.update { it.copy(seedColor = color) }
        settings.putLong("seed_color", color)
    }

    fun setMonitoringEnabled(enabled: Boolean) {
        _uiState.update { it.copy(monitoringEnabled = enabled) }
        settings.putBoolean("monitoring_enabled", enabled)
        audioEngine.setMonitoring(enabled)
    }

    fun setSampleRate(rate: SampleRate) {
        _uiState.update { it.copy(sampleRate = rate) }
        settings.putString("sample_rate", rate.name)
    }

    fun setChannelCount(count: ChannelCount) {
        _uiState.update { it.copy(channelCount = count) }
        settings.putString("channel_count", count.name)
    }

    fun setAudioFormat(format: AudioFormat) {
        _uiState.update { it.copy(audioFormat = format) }
        settings.putString("audio_format", format.name)
    }
}
