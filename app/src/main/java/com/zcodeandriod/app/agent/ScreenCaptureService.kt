package com.zcodeandriod.app.agent

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.util.Base64
import com.zcodeandriod.app.notifications.AppNotifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.math.max

/**
 * 截屏服务（骨架学 dsh-go ScreenCaptureService）：
 * - 一次授权一个 VirtualDisplay；surface 只在显式取帧时挂载（省电 + 不持续录制）
 * - 帧编码 JPEG(70)、长边 ≤1280、Base64 后仅驻内存，不落盘
 * - grant 一次性：onStartCommand 消费即失效，停止后必须重新弹系统授权
 */
class ScreenCaptureService : Service() {

    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private val busy = AtomicBoolean(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val notification: Notification = AppNotifications.captureNotification(this)
        startForeground(
            NOTIF_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            release("stopped")
            stopSelf()
            return START_NOT_STICKY
        }
        // grant 一次性消费
        val data = intent?.getParcelableExtra<android.content.Intent>(EXTRA_DATA)
        val code = intent?.getIntExtra(EXTRA_CODE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        if (data == null || code == Int.MIN_VALUE || projection != null) {
            if (projection == null) stopSelf()
            return START_NOT_STICKY
        }
        val manager = getSystemService(MediaProjectionManager::class.java)
        val metrics = resources.displayMetrics
        val mp = manager?.getMediaProjection(code, data) ?: run {
            stopSelf(); return START_NOT_STICKY
        }
        projection = mp
        mp.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                release("projection_revoked")
            }
        }, android.os.Handler(mainLooper))
        reader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2)
        display = mp.createVirtualDisplay(
            "ZcodeA-Capture",
            metrics.widthPixels,
            metrics.heightPixels,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            null,
            null,
            android.os.Handler(mainLooper),
        )
        instance = this
        return START_NOT_STICKY
    }

    /** 取一帧：挂 surface → 等帧 → 摘 surface → 编码 */
    private suspend fun awaitFrameBase64(): String? = withContext(Dispatchers.Main.immediate) {
        val r = reader ?: return@withContext null
        val d = display ?: return@withContext null
        if (!busy.compareAndSet(false, true)) return@withContext null
        try {
            d.surface = r.surface
            val image: Image = withTimeoutOrNull(2500L) {
                suspendCancellableCoroutine { cont ->
                    r.setOnImageAvailableListener({ reader ->
                        val img = reader.acquireLatestImage()
                        if (img != null) {
                            reader.setOnImageAvailableListener(null, null)
                            cont.resume(img)
                        }
                    }, android.os.Handler(mainLooper))
                    cont.invokeOnCancellation { r.setOnImageAvailableListener(null, null) }
                }
            } ?: return@withContext null
            image.use { img ->
                val bitmap = imageToBitmap(img) ?: return@withContext null
                encodeBase64(bitmap)
            }
        } finally {
            d.surface = null
            busy.set(false)
        }
    }

    private fun imageToBitmap(image: Image): Bitmap? = runCatching {
        val plane = image.planes[0]
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val w = image.width
        val h = image.height
        val rowPadding = rowStride - pixelStride * w
        val full = Bitmap.createBitmap(
            w + rowPadding / pixelStride,
            h,
            Bitmap.Config.ARGB_8888,
        )
        full.copyPixelsFromBuffer(plane.buffer)
        Bitmap.createBitmap(full, 0, 0, w, h).also { if (it !== full) full.recycle() }
    }.getOrNull()

    private fun encodeBase64(bitmap: Bitmap): String? = runCatching {
        val scale = max(1f, max(bitmap.width, bitmap.height) / 1280f)
        val scaled = if (scale > 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width / scale).toInt(),
                (bitmap.height / scale).toInt(),
                true,
            )
        } else bitmap
        val bos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 70, bos)
        if (scaled !== bitmap) scaled.recycle()
        Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
    }.getOrNull()

    private fun release(why: String) {
        display?.release(); display = null
        reader?.close(); reader = null
        projection?.stop(); projection = null
        lastFrameBase64 = null
        if (instance === this) instance = null
    }

    override fun onDestroy() {
        release("destroyed")
        super.onDestroy()
    }

    companion object {
        private const val NOTIF_ID = 2002
        private const val EXTRA_CODE = "code"
        private const val EXTRA_DATA = "data"
        private const val ACTION_STOP = "com.zcodeandriod.app.CAPTURE_STOP"

        @Volatile
        private var instance: ScreenCaptureService? = null

        /** 最近一帧（Base64 JPEG），仅驻内存 */
        @Volatile
        var lastFrameBase64: String? = null
            private set

        fun ready(): Boolean = instance != null

        suspend fun grabFrame(): String? {
            val svc = instance ?: return null
            val b64 = svc.awaitFrameBase64() ?: return null
            lastFrameBase64 = b64
            return b64
        }

        fun start(context: Context, resultCode: Int, data: Intent) {
            val i = Intent(context, ScreenCaptureService::class.java)
                .putExtra(EXTRA_CODE, resultCode)
                .putExtra(EXTRA_DATA, data)
            context.startForegroundService(i)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, ScreenCaptureService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
