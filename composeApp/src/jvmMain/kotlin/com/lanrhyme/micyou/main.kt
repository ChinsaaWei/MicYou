package com.lanrhyme.micyou

import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Font
import javax.swing.UIManager
import micyou.composeapp.generated.resources.Res
import micyou.composeapp.generated.resources.app_icon
import org.jetbrains.compose.resources.painterResource

/**
 * 托盘菜单配置
 */
data class TrayMenuConfig(
    val showHideText: String,
    val connectText: String,
    val settingsText: String,
    val exitText: String
)

/**
 * 初始化系统托盘
 */
fun initializeSystemTray(
    config: TrayMenuConfig,
    onShowHideClick: () -> Unit,
    onConnectDisconnectClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onExitClick: () -> Unit
): TrayManager {
    val trayManager = TrayManager.getInstance()

    // 检查是否支持系统托盘
    if (!trayManager.isSupported) {
        Logger.w("Main", "SystemTray is not supported on this platform")
        return trayManager
    }

    // 获取图标路径
    val iconPath = TrayMenu.getDefaultIconPath()
    Logger.i("Main", "Using icon path: $iconPath")

    // 初始化托盘
    trayManager.initialize(iconPath, "MicYou")

    // 设置托盘状态
    if (trayManager.state == TrayManager.TrayState.READY) {
        // 清除现有菜单
        trayManager.clearMenu()

        // 添加菜单项
        trayManager.addMenuItem(
            text = config.showHideText,
            enabled = true,
            callback = onShowHideClick
        )

        trayManager.addMenuItem(
            text = config.connectText,
            enabled = true,
            callback = onConnectDisconnectClick
        )

        trayManager.addMenuItem(
            text = config.settingsText,
            enabled = true,
            callback = onSettingsClick
        )

        trayManager.addSeparator()

        trayManager.addMenuItem(
            text = config.exitText,
            enabled = true,
            callback = onExitClick
        )

        Logger.i("Main", "SystemTray initialized with menu items")
    } else {
        Logger.w("Main", "SystemTray initialization failed, state: ${trayManager.state}")
    }

    return trayManager
}

/**
 * 更新系统托盘菜单项
 */
fun updateSystemTrayMenu(
    trayManager: TrayManager,
    config: TrayMenuConfig
) {
    if (trayManager.state != TrayManager.TrayState.READY) return

    // 更新显示/隐藏菜单项
    trayManager.updateMenuItem(
        TrayMenu.DefaultItems.SHOW_HIDE,
        text = config.showHideText
    )

    // 更新连接/断开菜单项
    trayManager.updateMenuItem(
        TrayMenu.DefaultItems.CONNECT_DISCONNECT,
        text = config.connectText
    )

    Logger.d("Main", "Tray menu updated: showHide=${config.showHideText}, connect=${config.connectText}")
}

fun main() {
    Logger.init(JvmLogger())
    // 设置编码属性以尝试修复 AWT 乱码
    System.setProperty("file.encoding", "UTF-8")
    System.setProperty("sun.jnu.encoding", "UTF-8")
    // 强制 AWT 使用 Unicode 并解决部分渲染问题
    System.setProperty("sun.java2d.noddraw", "true")
    System.setProperty("sun.java2d.d3d", "false") // 禁用 D3D 尝试解决部分系统黑屏
    
    // 修复 Windows 10 上可能出现的透明/黑色窗口问题 (渲染兼容性)
    // 优先尝试使用 SOFTWARE_FAST，这在老旧设备或驱动不兼容的 Win10 上最稳定
    System.setProperty("skiko.renderApi", "SOFTWARE_FAST")
    System.setProperty("skiko.vsync", "false") // 禁用 vsync 解决部分显卡导致的渲染延迟
    System.setProperty("skiko.fps.enabled", "false")

    // 设置全局 Swing 属性以修复托盘菜单乱码
    try {
        val font = Font("Microsoft YaHei", Font.PLAIN, 12)
        UIManager.put("MenuItem.font", font)
        UIManager.put("Menu.font", font)
        UIManager.put("PopupMenu.font", font)
        UIManager.put("CheckBoxMenuItem.font", font)
        UIManager.put("RadioButtonMenuItem.font", font)
        UIManager.put("Label.font", font)
        UIManager.put("Button.font", font)
        
        // 尝试使用系统外观
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
    } catch (e: Exception) {
        e.printStackTrace()
    }

    Logger.i("Main", "App started")
    application {
        val viewModel = remember { MainViewModel() }
        var isSettingsOpen by remember { mutableStateOf(false) }
        var isVisible by remember { mutableStateOf(true) }

        val language by viewModel.uiState.collectAsState().let { state ->
            derivedStateOf { state.value.language }
        }
        val strings = getStrings(language)

        val streamState by viewModel.uiState.collectAsState().let { state ->
            derivedStateOf { state.value.streamState }
        }
        val isStreaming = streamState == StreamState.Streaming || streamState == StreamState.Connecting

        // 图标资源
        val icon = painterResource(Res.drawable.app_icon)

        // 计算托盘菜单配置
        val trayConfig = remember(strings, isVisible, isStreaming) {
            TrayMenuConfig(
                showHideText = if (isVisible) strings.trayHide else strings.trayShow,
                connectText = if (isStreaming) strings.stop else strings.start,
                settingsText = strings.settingsTitle,
                exitText = strings.trayExit
            )
        }

        // 初始化系统托盘（在 application 块内）
        val trayManager = remember {
            initializeSystemTray(
                config = trayConfig,
                onShowHideClick = { isVisible = !isVisible },
                onConnectDisconnectClick = { viewModel.toggleStream() },
                onSettingsClick = {
                    isSettingsOpen = true
                    isVisible = true
                },
                onExitClick = {
                    // 退出前尝试恢复默认麦克风
                    kotlinx.coroutines.runBlocking {
                        VBCableManager.setSystemDefaultMicrophone(toCable = false)
                    }
                    // 清理托盘
                    TrayManager.getInstance().dispose()
                    exitApplication()
                }
            )
        }

        // 监听状态变化，更新托盘菜单
        LaunchedEffect(isVisible, isStreaming, strings) {
            val config = TrayMenuConfig(
                showHideText = if (isVisible) strings.trayHide else strings.trayShow,
                connectText = if (isStreaming) strings.stop else strings.start,
                settingsText = strings.settingsTitle,
                exitText = strings.trayExit
            )
            updateSystemTrayMenu(trayManager, config)
        }

        // 监听托盘状态变化
        LaunchedEffect(trayManager.state) {
            Logger.d("Main", "TrayManager state changed: ${trayManager.state}")
        }

        // Main Window State
        val windowState = rememberWindowState(
            width = 600.dp, 
            height = 240.dp,
            position = WindowPosition(Alignment.Center)
        )

        // Main Window (Undecorated)
        if (isVisible) {
            Window(
                onCloseRequest = { 
                    viewModel.handleCloseRequest(
                        onExit = { 
                            kotlinx.coroutines.runBlocking {
                                VBCableManager.setSystemDefaultMicrophone(toCable = false)
                            }
                            TrayManager.getInstance().dispose()
                            exitApplication() 
                        },
                        onHide = { isVisible = false }
                    )
                },
                state = windowState,
                title = strings.appName,
                icon = icon,
                undecorated = true,
                transparent = true, // Allows rounded corners via Surface in DesktopHome
                resizable = false
            ) {
                WindowDraggableArea {
                    App(
                        viewModel = viewModel,
                        onMinimize = { windowState.isMinimized = true },
                        onClose = { 
                            viewModel.handleCloseRequest(
                                onExit = { 
                                    kotlinx.coroutines.runBlocking {
                                        VBCableManager.setSystemDefaultMicrophone(toCable = false)
                                    }
                                    TrayManager.getInstance().dispose()
                                    exitApplication() 
                                },
                                onHide = { isVisible = false }
                            )
                        },
                        onExitApp = { 
                            kotlinx.coroutines.runBlocking {
                                VBCableManager.setSystemDefaultMicrophone(toCable = false)
                            }
                            TrayManager.getInstance().dispose()
                            exitApplication() 
                        },
                        onHideApp = { isVisible = false },
                        onOpenSettings = { isSettingsOpen = true }
                    )
                }
            }
        }

        // Settings Window
        if (isSettingsOpen) {
            val settingsState = rememberWindowState(
                width = 530.dp,
                height = 500.dp,
                position = WindowPosition(Alignment.Center)
            )
            
            Window(
                onCloseRequest = { isSettingsOpen = false },
                state = settingsState,
                title = strings.settingsTitle,
                icon = icon,
                resizable = false
            ) {
                // Re-use theme logic from AppTheme but apply to settings window content
                val themeMode by viewModel.uiState.collectAsState().let { state ->
                    derivedStateOf { state.value.themeMode }
                }
                val seedColor by viewModel.uiState.collectAsState().let { state ->
                    derivedStateOf { state.value.seedColor }
                }
                val language by viewModel.uiState.collectAsState().let { state ->
                    derivedStateOf { state.value.language }
                }
                val seedColorObj = androidx.compose.ui.graphics.Color(seedColor.toInt())
                val strings = getStrings(language)

                CompositionLocalProvider(LocalAppStrings provides strings) {
                    AppTheme(themeMode = themeMode, seedColor = seedColorObj) {
                        DesktopSettings(
                            viewModel = viewModel,
                            onClose = { isSettingsOpen = false }
                        )
                    }
                }
            }
        }

        // 应用退出时清理托盘
        DisposableEffect(Unit) {
            onDispose {
                Logger.i("Main", "Application disposed, cleaning up tray")
                TrayManager.getInstance().dispose()
            }
        }
    }
}
