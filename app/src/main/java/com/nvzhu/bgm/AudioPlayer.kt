package com.nvzhu.bgm

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

class AudioPlayer(private val context: Context) {
    
    private var mediaPlayer: MediaPlayer? = null
    private var currentConfig: ButtonConfig? = null
    private val handler = Handler(Looper.getMainLooper())
    private var stopRunnable: Runnable? = null
    private val isReleased = AtomicBoolean(false)
    private val isOperating = AtomicBoolean(false)
    
    @Synchronized
    fun play(config: ButtonConfig, onComplete: (() -> Unit)? = null, onError: ((Exception) -> Unit)? = null) {
        if (isReleased.get()) {
            onError?.invoke(Exception("AudioPlayer has been released"))
            return
        }
        
        if (isOperating.getAndSet(true)) {
            return
        }
        
        try {
            internalStop()
            
            if (config.audioUri.isEmpty() && config.builtInResId == 0) {
                onError?.invoke(Exception("No audio URI or built-in resource"))
                isOperating.set(false)
                return
            }
            
            var player: MediaPlayer? = null
            
            try {
                player = MediaPlayer()
                
                player.setOnErrorListener { mp, what, extra ->
                    safeInvoke {
                        onError?.invoke(Exception("MediaPlayer error: what=$what, extra=$extra"))
                        internalStop()
                    }
                    true
                }
                
                if (config.audioUri.isNotEmpty()) {
                    try {
                        val uri = Uri.parse(config.audioUri)
                        player.setDataSource(context, uri)
                    } catch (e: SecurityException) {
                        throw IOException("Cannot access audio file (permission revoked?)", e)
                    } catch (e: IllegalArgumentException) {
                        throw IOException("Invalid audio URI", e)
                    }
                } else if (config.builtInResId != 0) {
                    try {
                        val afd = context.resources.openRawResourceFd(config.builtInResId)
                        player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                        afd.close()
                    } catch (e: Exception) {
                        throw IOException("Cannot open built-in audio resource", e)
                    }
                }
                
                player.prepare()
                
                val startMs = (config.startTimeSeconds * 1000).toInt().coerceAtLeast(0)
                if (startMs > 0) {
                    try {
                        player.seekTo(startMs)
                    } catch (e: IllegalStateException) {
                    }
                }
                
                if (config.endTimeSeconds > 0 && config.endTimeSeconds > config.startTimeSeconds) {
                    val duration = ((config.endTimeSeconds - config.startTimeSeconds) * 1000).toLong()
                    
                    val completionCallback = {
                        safeInvoke {
                            internalStop()
                            onComplete?.invoke()
                        }
                    }
                    
                    player.setOnCompletionListener {
                        completionCallback()
                    }
                    
                    stopRunnable = Runnable {
                        completionCallback()
                    }.also {
                        handler.postDelayed(it, duration)
                    }
                } else {
                    player.setOnCompletionListener {
                        safeInvoke {
                            internalStop()
                            onComplete?.invoke()
                        }
                    }
                }
                
                player.start()
                mediaPlayer = player
                currentConfig = config
                
            } catch (e: Exception) {
                player?.let { safeRelease(it) }
                onError?.invoke(e)
            }
            
        } finally {
            isOperating.set(false)
        }
    }
    
    @Synchronized
    fun stop() {
        if (isReleased.get()) return
        internalStop()
    }
    
    private fun internalStop() {
        stopRunnable?.let {
            handler.removeCallbacks(it)
            stopRunnable = null
        }
        
        mediaPlayer?.let { player ->
            safeRelease(player)
            mediaPlayer = null
        }
        
        currentConfig = null
    }
    
    private fun safeRelease(player: MediaPlayer) {
        try {
            if (player.isPlaying) {
                player.stop()
            }
        } catch (e: IllegalStateException) {
        } finally {
            try {
                player.reset()
                player.release()
            } catch (e: Exception) {
            }
        }
    }
    
    @Synchronized
    fun isPlaying(): Boolean {
        if (isReleased.get()) return false
        return try {
            mediaPlayer?.isPlaying == true
        } catch (e: IllegalStateException) {
            false
        }
    }
    
    @Synchronized
    fun release() {
        if (isReleased.getAndSet(true)) return
        
        internalStop()
        handler.removeCallbacksAndMessages(null)
    }
    
    private inline fun safeInvoke(block: () -> Unit) {
        try {
            if (!isReleased.get()) {
                block()
            }
        } catch (e: Exception) {
        }
    }
}
