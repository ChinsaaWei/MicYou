package com.lanrhyme.micyou

import java.net.InetAddress

class JVMPlatform: Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
    override val type: PlatformType = PlatformType.Desktop
    override val ipAddress: String
        get() = try {
            InetAddress.getLocalHost().hostAddress
        } catch (e: Exception) {
            "Unknown"
        }
}

actual fun getPlatform(): Platform = JVMPlatform()

actual fun uninstallVBCable() {
    VBCableManager.uninstallVBCable()
}

actual fun getAppVersion(): String {
    val fromManifest = object {}.javaClass.`package`?.implementationVersion
    if (!fromManifest.isNullOrBlank()) return fromManifest
    val fromProperty = System.getProperty("app.version")
    if (!fromProperty.isNullOrBlank()) return fromProperty
    return "dev"
}

