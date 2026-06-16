package by.w6.my1drive.utils

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Простой кольцевой буфер для хранения ключевых логов в памяти.
 * Вместо поиска в logcat — логи доступны прямо на экране приложения
 * с кнопкой копирования.
 */
object DebugLogBuffer {
    private const val MAX_LINES = 200
    private val buffer = ArrayDeque<String>(MAX_LINES)
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    fun log(tag: String, message: String) {
        val time = dateFormat.format(Date())
        val line = "$time [$tag] $message"
        synchronized(buffer) {
            if (buffer.size >= MAX_LINES) {
                buffer.removeFirst()
            }
            buffer.add(line)
        }
        // Дублируем в стандартный logcat
        Log.d(tag, message)
    }

    fun getLogText(): String {
        synchronized(buffer) {
            return buffer.joinToString("\n")
        }
    }

    fun clear() {
        synchronized(buffer) {
            buffer.clear()
        }
    }
}
