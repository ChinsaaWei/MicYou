package com.lanrhyme.micyou

import dorkbox.systemTray.SystemTray
import dorkbox.systemTray.Menu
import dorkbox.systemTray.MenuItem
import java.awt.event.ActionListener
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.swing.ImageIcon

/**
 * SystemTray 跨平台托盘实现
 * 使用 dorkbox SystemTray 库，支持 Linux (X11/Wayland)、Windows 和 macOS
 */
class SystemTrayWrapper : TrayManager {

    private var systemTray: SystemTray? = null
    private var menu: Menu? = null
    private var currentState: TrayManager.TrayState = TrayManager.TrayState.INITIALIZING
    private var leftClickCallback: (() -> Unit)? = null
    private var rightClickCallback: (() -> Unit)? = null

    // 菜单项存储
    private val menuItems = ConcurrentHashMap<String, MenuItemInfo>()
    private var separatorAdded = false

    /**
     * 菜单项信息
     */
    private data class MenuItemInfo(
        val item: MenuItem,
        val callback: () -> Unit
    )

    override val state: TrayManager.TrayState
        get() = currentState

    override val isSupported: Boolean
        get() = SystemTray.get() != null

    override fun initialize(iconPath: String, tooltip: String) {
        try {
            // 配置 SystemTray 属性
            configureSystemTray()

            // 获取 SystemTray 实例
            val tray = SystemTray.get()
            if (tray == null) {
                Logger.w("SystemTrayWrapper", "SystemTray not supported on this platform")
                currentState = TrayManager.TrayState.DISABLED
                return
            }

            systemTray = tray

            // 设置图标
            setIcon(iconPath)

            // 设置工具提示
            setTooltip(tooltip)

            // 创建菜单
            createMenu()

            currentState = TrayManager.TrayState.READY
            Logger.i("SystemTrayWrapper", "SystemTray initialized successfully")

        } catch (e: Exception) {
            Logger.e("SystemTrayWrapper", "Failed to initialize SystemTray", e)
            currentState = TrayManager.TrayState.ERROR
        }
    }

    /**
     * 配置 SystemTray 属性
     */
    private fun configureSystemTray() {
        try {
            // 对于 Linux，可以设置 GTK 版本偏好
            val osName = System.getProperty("os.name", "").lowercase()
            if (osName.contains("linux") || osName.contains("unix")) {
                // Linux 特定配置
                // 如果遇到 GTK 问题，可以取消注释以下行：
                // SystemTray.FORCE_GTK2 = true
                // SystemTray.PREFER_GTK3 = true
            }

        } catch (e: Exception) {
            Logger.w("SystemTrayWrapper", "Failed to configure SystemTray properties: ${e.message}")
        }
    }

    /**
     * 创建菜单
     */
    private fun createMenu() {
        val tray = systemTray ?: return

        // 获取或创建菜单
        menu = tray.menu
    }

    override fun setIcon(iconPath: String) {
        val tray = systemTray ?: return

        try {
            val iconFile = File(iconPath)
            if (!iconFile.exists()) {
                Logger.w("SystemTrayWrapper", "Icon file not found: $iconPath")
                return
            }

            // 使用 ImageIcon 加载图像
            val imageIcon = ImageIcon(iconFile.absolutePath)
            val image = imageIcon.image

            if (image != null) {
                tray.setImage(image)
                Logger.i("SystemTrayWrapper", "Icon set successfully from: $iconPath")
            }

        } catch (e: Exception) {
            Logger.e("SystemTrayWrapper", "Failed to set icon: ${e.message}", e)
        }
    }

    override fun setTooltip(tooltip: String) {
        val tray = systemTray ?: return

        try {
            tray.setTooltip(tooltip)
        } catch (e: Exception) {
            Logger.w("SystemTrayWrapper", "Failed to set tooltip: ${e.message}")
        }
    }

    override fun setStatus(status: String) {
        val tray = systemTray ?: return

        try {
            tray.setStatus(status)
        } catch (e: Exception) {
            Logger.w("SystemTrayWrapper", "Failed to set status: ${e.message}")
        }
    }

    override fun addMenuItem(text: String, enabled: Boolean, callback: () -> Unit): String {
        val menu = this.menu ?: return ""

        try {
            val itemId = generateItemId()

            // 创建 ActionListener
            val actionListener = ActionListener {
                try {
                    callback()
                } catch (e: Exception) {
                    Logger.e("SystemTrayWrapper", "Menu item callback failed", e)
                }
            }

            // 创建菜单项
            val menuItem = MenuItem(text, actionListener)
            menuItem.setEnabled(enabled)

            // 添加到菜单
            menu.add(menuItem)

            // 存储菜单项信息
            menuItems[itemId] = MenuItemInfo(menuItem, callback)

            separatorAdded = false

            Logger.d("SystemTrayWrapper", "Menu item added: $text (id: $itemId)")

            return itemId

        } catch (e: Exception) {
            Logger.e("SystemTrayWrapper", "Failed to add menu item: $text", e)
            return ""
        }
    }

    override fun addSeparator() {
        val menu = this.menu ?: return

        if (separatorAdded) return

        try {
            val separator = MenuItem("-")
            menu.add(separator)
            separatorAdded = true
            Logger.d("SystemTrayWrapper", "Separator added")
        } catch (e: Exception) {
            Logger.w("SystemTrayWrapper", "Failed to add separator: ${e.message}")
        }
    }

    override fun updateMenuItem(itemId: String, text: String?, enabled: Boolean?) {
        val itemInfo = menuItems[itemId] ?: return

        try {
            text?.let {
                itemInfo.item.text = it
            }
            enabled?.let {
                itemInfo.item.setEnabled(it)
            }
            Logger.d("SystemTrayWrapper", "Menu item updated: $itemId")
        } catch (e: Exception) {
            Logger.e("SystemTrayWrapper", "Failed to update menu item: $itemId", e)
        }
    }

    override fun removeMenuItem(itemId: String) {
        val itemInfo = menuItems.remove(itemId) ?: return

        try {
            val menu = this.menu ?: return
            menu.remove(itemInfo.item)
            Logger.d("SystemTrayWrapper", "Menu item removed: $itemId")
        } catch (e: Exception) {
            Logger.e("SystemTrayWrapper", "Failed to remove menu item: $itemId", e)
        }
    }

    override fun clearMenu() {
        val menu = this.menu ?: return

        try {
            // SystemTray 的菜单清理方式
            // 注意：SystemTray API 可能不支持直接清空所有菜单项
            // 我们需要逐个移除
            val itemsToRemove = menuItems.toMap()
            itemsToRemove.forEach { (id, info) ->
                try {
                    menu.remove(info.item)
                } catch (e: Exception) {
                    // 忽略移除错误
                }
            }
            menuItems.clear()
            separatorAdded = false
            Logger.d("SystemTrayWrapper", "Menu cleared")
        } catch (e: Exception) {
            Logger.e("SystemTrayWrapper", "Failed to clear menu", e)
        }
    }

    override fun setOnLeftClick(callback: () -> Unit) {
        leftClickCallback = callback

        // SystemTray 库不支持直接设置左键点击回调
        // 我们需要通过其他方式实现
        // 注意：某些平台（如 Linux）不支持托盘图标点击事件
    }

    override fun setOnRightClick(callback: () -> Unit) {
        rightClickCallback = callback

        // SystemTray 库中右键点击会显示菜单，这是默认行为
        // 我们可以在这里添加自定义逻辑
    }

    override fun dispose() {
        try {
            // 清理菜单项
            clearMenu()

            // 销毁 SystemTray
            systemTray?.shutdown()
            systemTray = null
            menu = null

            currentState = TrayManager.TrayState.DISABLED
            menuItems.clear()

            Logger.i("SystemTrayWrapper", "SystemTray disposed")

        } catch (e: Exception) {
            Logger.e("SystemTrayWrapper", "Failed to dispose SystemTray", e)
        }
    }

    /**
     * 生成唯一的菜单项 ID
     */
    private fun generateItemId(): String {
        return "menu_item_${System.currentTimeMillis()}_${menuItems.size}"
    }
}
