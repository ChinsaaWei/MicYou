package com.lanrhyme.micyou

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object FirewallManager {
    private val isWindows = System.getProperty("os.name").lowercase().contains("win")

    fun isPortAllowed(port: Int, protocol: String = "TCP"): Boolean {
        if (!isWindows) return true

        return try {
            // 使用 PowerShell 检查是否有允许该端口的入站规则
            // 这种方式比解析 netsh 输出更可靠，且支持检查特定端口
            val command = "if (Get-NetFirewallRule -Enabled True -Direction Inbound -Action Allow | Get-NetFirewallPortFilter | Where-Object { \$_.LocalPort -eq '$port' -and \$_.Protocol -eq '$protocol' }) { exit 0 } else { exit 1 }"
            val process = ProcessBuilder("powershell", "-NoProfile", "-Command", command)
                .start()

            val finished = process.waitFor(5, TimeUnit.SECONDS)
            if (!finished) {
                process.destroy()
                return true // 超时则保守认为已放行
            }
            
            process.exitValue() == 0
        } catch (e: Exception) {
            Logger.e("FirewallManager", "Failed to check firewall status", e)
            // 如果检查失败（例如系统不支持 PowerShell 4.0+ 的网络命令），返回 true 避免干扰
            true
        }
    }

    fun addFirewallRule(port: Int, protocol: String = "TCP"): Result<Unit> {
        if (!isWindows) return Result.success(Unit)

        return try {
            val ruleName = "MicYou ($protocol-In-$port)"
            // 使用 PowerShell 以管理员身份运行 netsh 命令添加规则
            val command = "Start-Process netsh -ArgumentList 'advfirewall firewall add rule name=\"$ruleName\" dir=in action=allow protocol=$protocol localport=$port' -Verb RunAs -Wait"
            
            val process = ProcessBuilder("powershell", "-NoProfile", "-Command", command)
                .redirectErrorStream(true)
                .start()

            val finished = process.waitFor(30, TimeUnit.SECONDS)
            if (!finished) {
                process.destroy()
                return Result.failure(IOException("Add firewall rule timeout"))
            }

            if (process.exitValue() == 0) {
                // 再次检查是否真的添加成功了
                if (isPortAllowed(port, protocol)) {
                    Result.success(Unit)
                } else {
                    Result.failure(IOException("Failed to verify firewall rule after adding"))
                }
            } else {
                val output = process.inputStream.bufferedReader().readText()
                Result.failure(IOException("Failed to add firewall rule: $output"))
            }
        } catch (e: Exception) {
            Logger.e("FirewallManager", "Failed to add firewall rule", e)
            Result.failure(e)
        }
    }
}
