package com.nvzhu.bgm

import android.app.Application
import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NvZhuApp : Application() {
    
    companion object {
        private const val CRASH_LOG_FILE = "crash_log.txt"
        private const val MAX_CRASH_LOGS = 5
        
        fun getCrashLogFile(context: Context): File {
            return File(context.filesDir, CRASH_LOG_FILE)
        }
        
        fun readCrashLog(context: Context): String {
            val file = getCrashLogFile(context)
            return if (file.exists()) {
                file.readText()
            } else {
                "暂无崩溃日志"
            }
        }
        
        fun clearCrashLog(context: Context) {
            getCrashLogFile(context).delete()
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        setupCrashHandler()
    }
    
    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrashLog(throwable)
            } catch (e: Exception) {
            }
            
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
    
    private fun writeCrashLog(throwable: Throwable) {
        try {
            val file = getCrashLogFile(this)
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            throwable.printStackTrace(pw)
            val stackTrace = sw.toString()
            
            val existingLogs = if (file.exists()) {
                file.readText().split("\n\n==========\n\n")
            } else {
                emptyList()
            }
            
            val newLog = "崩溃时间: $timestamp\n线程: ${Thread.currentThread().name}\n\n$stackTrace"
            
            val allLogs = (listOf(newLog) + existingLogs).take(MAX_CRASH_LOGS)
            
            file.writeText(allLogs.joinToString("\n\n==========\n\n"))
            
        } catch (e: Exception) {
        }
    }
}
