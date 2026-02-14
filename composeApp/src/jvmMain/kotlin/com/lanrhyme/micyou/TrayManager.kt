package com.lanrhyme.micyou

import java.awt.Image

/**
 * 托盘管理器接口
 * 定义跨平台托盘操作的标准接口
 */
interface TrayManager {
    /**
     * 托盘状态
     */
    enum class TrayState {
        INITIALIZING,
        READY,
        ERROR,
        DISABLED
    }

    /**
     * 当前托盘状态
     */
    val state: TrayState

    /**
     * 是否支持系统托盘
     */
    val isSupported: Boolean

    /**
     * 初始化托盘
     * @param iconPath 图标路径
     * @param tooltip 工具提示文本
     */
    fun initialize(iconPath: String, tooltip: String)

    /**
     * 设置图标
     * @param iconPath 图标路径
     */
    fun setIcon(iconPath: String)

    /**
     * 设置工具提示
     * @param tooltip 工具提示文本
     */
    fun setTooltip(tooltip: String)

    /**
     * 设置状态文本（某些平台支持）
     * @param status 状态文本
     */
    fun setStatus(status: String)

    /**
     * 添加菜单项
     * @param text 菜单项文本
     * @param enabled 是否启用
     * @param callback 点击回调
     * @return 菜单项ID，用于后续更新或删除
     */
    fun addMenuItem(
        text: String,
        enabled: Boolean = true,
        callback: () -> Unit
    ): String

    /**
     * 添加分隔线
     */
    fun addSeparator()

    /**
     * 更新菜单项
     * @param itemId 菜单项ID
     * @param text 新的文本（可选）
     * @param enabled 新的启用状态（可选）
     */
    fun updateMenuItem(itemId: String, text: String? = null, enabled: Boolean? = null)

    /**
     * 移除菜单项
     * @param itemId 菜单项ID
     */
    fun removeMenuItem(itemId: String)

    /**
     * 清除所有菜单项
     */
    fun clearMenu()

    /**
     * 设置左键点击回调
     * @param callback 点击回调
     */
    fun setOnLeftClick(callback: () -> Unit)

    /**
     * 设置右键点击回调（某些平台支持）
     * @param callback 点击回调
     */
    fun setOnRightClick(callback: () -> Unit)

    /**
     * 销毁托盘
     */
    fun dispose()

    /**
     * 获取托盘实例（单例）
     */
    companion object {
        @Volatile
        private var instance: TrayManager? = null

        fun getInstance(): TrayManager {
            return instance ?: synchronized(this) {
                instance ?: SystemTrayWrapper().also { instance = it }
            }
        }

        /**
         * 重置实例（用于测试）
         */
        fun reset() {
            instance?.dispose()
            instance = null
        }
    }
}
