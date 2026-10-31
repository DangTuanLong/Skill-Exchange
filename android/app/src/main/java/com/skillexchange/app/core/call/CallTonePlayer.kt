package com.skillexchange.app.core.call

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

interface CallTonePlayer {
    fun startRingback()
    fun stop()
}

class AndroidCallTonePlayer : CallTonePlayer {
    private val tag = "CallTonePlayer"
    private var toneGenerator: ToneGenerator? = null
    private var toneJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    @Synchronized
    override fun startRingback() {
        if (toneJob?.isActive == true) return
        stop()

        toneJob = scope.launch {
            try {
                toneGenerator = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 80)
                Log.d(tag, "Bắt đầu phát âm thanh đổ chuông gọi đi (Ringback tone)")
                while (isActive) {
                    toneGenerator?.startTone(ToneGenerator.TONE_SUP_RINGTONE, 1500)
                    delay(4000L) // Nhịp chuông điện thoại chuẩn: 1.5s chuông, 2.5s khoảng lặng
                }
            } catch (e: Exception) {
                Log.w(tag, "Không thể phát âm thanh chuông gọi đi: ${e.message}")
            }
        }
    }

    @Synchronized
    override fun stop() {
        toneJob?.cancel()
        toneJob = null
        try {
            toneGenerator?.stopTone()
            toneGenerator?.release()
        } catch (e: Exception) {
            Log.w(tag, "Lỗi khi giải phóng ToneGenerator: ${e.message}")
        } finally {
            toneGenerator = null
        }
    }
}

