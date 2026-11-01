package com.skillexchange.app.presentation.call.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.livekit.android.renderer.SurfaceViewRenderer
import io.livekit.android.room.track.VideoTrack

/**
 * Composable hiển thị video WebRTC từ LiveKit VideoTrack (cục bộ hoặc đối tác).
 * Sử dụng SurfaceViewRenderer từ SDK lõi livekit-android:2.29.0 mà không cần thêm dependency ngoài.
 * Đảm bảo dọn dẹp và giải phóng renderer an toàn khi thoát màn hình / hủy Composable.
 */
@Composable
fun VideoTrackView(
    track: VideoTrack?,
    onInitRenderer: (SurfaceViewRenderer) -> Unit,
    modifier: Modifier = Modifier,
    mirror: Boolean = false
) {
    val context = LocalContext.current
    val renderer = remember {
        SurfaceViewRenderer(context).apply {
            setMirror(mirror)
            setEnableHardwareScaler(true)
            onInitRenderer(this)
        }
    }

    DisposableEffect(track) {
        track?.addRenderer(renderer)
        onDispose {
            track?.removeRenderer(renderer)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            renderer.release()
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { renderer },
            modifier = Modifier.fillMaxSize()
        )
    }
}

