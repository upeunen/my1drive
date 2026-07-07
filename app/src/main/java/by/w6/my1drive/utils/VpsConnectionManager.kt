package by.w6.my1drive.utils

import android.content.Context
import android.content.SharedPreferences
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.Properties

class VpsConnectionManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("my1drive_prefs", Context.MODE_PRIVATE)

    fun isVpsEnabled(): Boolean = prefs.getBoolean("vps_enabled", false)
    fun setVpsEnabled(enabled: Boolean) = prefs.edit().putBoolean("vps_enabled", enabled).apply()

    fun getVpsLimitGb(): Int = prefs.getInt("vps_limit_gb", 10)
    fun setVpsLimitGb(limit: Int) = prefs.edit().putInt("vps_limit_gb", limit).apply()

    fun getHost(): String = prefs.getString("vps_host", "") ?: ""
    fun getPort(): Int = prefs.getInt("vps_port", 22)
    fun getUsername(): String = prefs.getString("vps_username", "") ?: ""
    fun getPassword(): String = prefs.getString("vps_password", "") ?: ""
    fun getRemotePath(): String = prefs.getString("vps_remote_path", "/home") ?: "/home"

    fun saveConfig(host: String, port: Int, username: String, password: String, remotePath: String) {
        prefs.edit()
            .putString("vps_host", host)
            .putInt("vps_port", port)
            .putString("vps_username", username)
            .putString("vps_password", password)
            .putString("vps_remote_path", remotePath)
            .apply()
    }

    suspend fun testConnection(
        host: String, port: Int, username: String, password: String, remotePath: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var session: Session? = null
        try {
            val jsch = JSch()
            session = jsch.getSession(username, host, port)
            session.setPassword(password)
            
            val config = Properties()
            config["StrictHostKeyChecking"] = "no"
            session.setConfig(config)
            session.connect(10000) // 10s timeout
            
            val channel = session.openChannel("sftp") as ChannelSftp
            channel.connect(5000)
            
            // Try to access or create the remote path
            try {
                channel.cd(remotePath)
            } catch (e: Exception) {
                // Try to create folder structure if mkdir fails recursively
                createRemoteDirectory(channel, remotePath)
                channel.cd(remotePath)
            }
            
            channel.disconnect()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            session?.disconnect()
        }
    }

    private fun createRemoteDirectory(channel: ChannelSftp, path: String) {
        val dirs = path.split("/").filter { it.isNotEmpty() }
        var currentPath = ""
        if (path.startsWith("/")) {
            currentPath = "/"
        }
        for (dir in dirs) {
            currentPath = if (currentPath == "/") "/$dir" else "$currentPath/$dir"
            try {
                channel.cd(currentPath)
            } catch (e: Exception) {
                try {
                    channel.mkdir(currentPath)
                } catch (ex: Exception) {
                    // ignore or rethrow
                }
            }
        }
    }

    suspend fun uploadFile(
        inputStream: InputStream,
        fileName: String,
        onProgress: (Long) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        val host = getHost()
        val port = getPort()
        val username = getUsername()
        val password = getPassword()
        val remotePath = getRemotePath()

        if (host.isEmpty() || username.isEmpty()) {
            return@withContext Result.failure(Exception("VPS credentials are not configured"))
        }

        var session: Session? = null
        try {
            val jsch = JSch()
            session = jsch.getSession(username, host, port)
            session.setPassword(password)
            
            val config = Properties()
            config["StrictHostKeyChecking"] = "no"
            session.setConfig(config)
            session.connect(20000)
            
            val channel = session.openChannel("sftp") as ChannelSftp
            channel.connect(10000)
            
            try {
                channel.cd(remotePath)
            } catch (e: Exception) {
                createRemoteDirectory(channel, remotePath)
                channel.cd(remotePath)
            }
            
            // Upload file
            channel.put(inputStream, fileName, object : com.jcraft.jsch.SftpProgressMonitor {
                private var count: Long = 0
                override fun init(op: Int, src: String?, dest: String?, max: Long) {}
                override fun count(count: Long): Boolean {
                    this.count += count
                    onProgress(this.count)
                    return true
                }
                override fun end() {}
            })
            
            channel.disconnect()
            val fullPath = if (remotePath.endsWith("/")) "$remotePath$fileName" else "$remotePath/$fileName"
            Result.success(fullPath)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            session?.disconnect()
        }
    }
}
