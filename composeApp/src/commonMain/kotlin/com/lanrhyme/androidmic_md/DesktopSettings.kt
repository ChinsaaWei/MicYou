package com.lanrhyme.androidmic_md

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesktopSettings(
    viewModel: MainViewModel,
    onClose: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    
    // 预设种子颜色
    val seedColors = listOf(
        0xFF6750A4L, // Purple (Default)
        0xFFB3261EL, // Red
        0xFFFBC02DL, // Yellow
        0xFF388E3CL, // Green
        0xFF006C51L, // Teal
        0xFF2196F3L, // Blue
        0xFFE91E63L  // Pink
    )

    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("设置", style = MaterialTheme.typography.headlineSmall)
            }
            
            item { HorizontalDivider() }
            
            // 主题模式
            item {
                Text("主题模式", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = { Text(mode.name) }
                        )
                    }
                }
            }
            
            item { HorizontalDivider() }
            
            // 主题颜色
            item {
                Text("主题颜色", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    seedColors.forEach { colorHex ->
                        val color = Color(colorHex.toInt())
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(color, CircleShape)
                                .clickable { viewModel.setSeedColor(colorHex) }
                                .then(
                                    if (state.seedColor == colorHex) {
                                        Modifier.padding(2.dp).background(MaterialTheme.colorScheme.onSurface, CircleShape).padding(2.dp).background(color, CircleShape)
                                    } else Modifier
                                )
                        )
                    }
                }
            }
            
            item { HorizontalDivider() }
            
            // 音频参数
            item {
                Text("音频参数", style = MaterialTheme.typography.titleMedium)
            }
            
            // 采样率
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("采样率", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    // Use a dropdown or just chips for simplicity
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { expanded = true }) {
                            Text("${state.sampleRate.value} Hz")
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            SampleRate.entries.forEach { rate ->
                                DropdownMenuItem(
                                    text = { Text("${rate.value} Hz") },
                                    onClick = { 
                                        viewModel.setSampleRate(rate)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
            
            // 通道数
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("通道数", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { expanded = true }) {
                            Text(state.channelCount.label)
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            ChannelCount.entries.forEach { count ->
                                DropdownMenuItem(
                                    text = { Text(count.label) },
                                    onClick = { 
                                        viewModel.setChannelCount(count)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
            
            // 音频格式
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("音频格式", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { expanded = true }) {
                            Text(state.audioFormat.label)
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            AudioFormat.entries.forEach { format ->
                                DropdownMenuItem(
                                    text = { Text(format.label) },
                                    onClick = { 
                                        viewModel.setAudioFormat(format)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item { HorizontalDivider() }
            
            // 监听设置
            item {
                Text("监听设置", style = MaterialTheme.typography.titleMedium)
                
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.setMonitoringEnabled(!state.monitoringEnabled) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("监听设备 (播放收到的声音)", modifier = Modifier.weight(1f))
                    Switch(
                        checked = state.monitoringEnabled,
                        onCheckedChange = { viewModel.setMonitoringEnabled(it) }
                    )
                }
            }

            item { HorizontalDivider() }
            
            // System Actions (Only on Desktop)
            if (getPlatform().type == PlatformType.Desktop) {
                item {
                    Text("系统", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { uninstallVBCable() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("卸载音频驱动 (VB-Cable)")
                    }
                }
                item { HorizontalDivider() }
            }
            
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onClose,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Text("关闭")
                    }
                }
            }
        }
    }
}
