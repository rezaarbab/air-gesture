package ir.airgesture.sense

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Size
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import ir.airgesture.Air
import ir.airgesture.decode.HandSample
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Front camera, one hand, twenty-one landmarks, reduced to the four numbers the decoder needs.
 *
 * JPEG frames are decoded to a Bitmap because that is the one MediaPipe input that never talks
 * about sensor formats: YUV plans, row strides and rotation then stay out of this file. The
 * frame is physically rotated by (360 - sensorOrientation) so "hand up" really is +y-up.
 */
class CameraHandSource(
    private val ctx: Context,
    private val onSample: (HandSample) -> Unit,
) {

    private var thread: HandlerThread? = null
    private var worker: Handler? = null
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var marker: HandLandmarker? = null
    private var sensorOrientation = 0
    private var frameW = 0
    private var frameH = 0
    private var frames = 0
    private var framesWithHand = 0

    @Volatile
    private var running = false

    fun start(): Boolean {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Air.note("hand: CAMERA permission not granted")
            return false
        }
        val mgr = ctx.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        if (mgr == null) {
            Air.note("hand: CameraManager unavailable")
            return false
        }
        val camId = pickFront(mgr)
        if (camId == null) {
            Air.note("hand: no front-facing camera on this device")
            return false
        }
        val size = pickSize(mgr, camId)
        frameW = size.width
        frameH = size.height
        if (!openMarker()) return false

        val t = HandlerThread("air-hand")
        thread = t
        t.start()
        worker = Handler(t.looper)
        return try {
            sensorOrientation = mgr.getCameraCharacteristics(camId)
                .get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
            val r = ImageReader.newInstance(frameW, frameH, ImageFormat.JPEG, 2)
            r.setOnImageAvailableListener(onFrame, worker)
            reader = r
            mgr.openCamera(camId, stateCallback, worker)
            true
        } catch (e: Exception) {
            Air.note("hand: openCamera threw ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    private fun pickSize(mgr: CameraManager, camId: String): Size {
        val map = mgr.getCameraCharacteristics(camId)
            .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) as? StreamConfigurationMap
        val sizes = map?.getOutputSizes(ImageFormat.JPEG)
        val best = sizes
            ?.filter { it.width in 320..1600 && it.height in 240..1200 }
            ?.minByOrNull { abs(0.75f - it.width.toFloat() / it.height.toFloat()) + it.width / 250000f }
        return best ?: Size(640, 480)
    }

    private fun pickFront(mgr: CameraManager): String? {
        for (id in mgr.cameraIdList) {
            val facing = mgr.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING)
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) return id
        }
        return null
    }

    private fun openMarker(): Boolean = try {
        val base = BaseOptions.builder().setModelAssetPath(MODEL).build()
        val opts = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(base)
            .setRunningMode(RunningMode.VIDEO)
            .setNumHands(1)
            .setMinHandDetectionConfidence(0.5f)
            .setMinHandPresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .build()
        marker = HandLandmarker.createFromOptions(ctx, opts)
        Air.note("hand: model loaded, target $frameW x $frameH")
        true
    } catch (e: Exception) {
        Air.note("hand: model load failed (${e.javaClass.simpleName}: ${e.message})")
        false
    }

    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(cam: CameraDevice) {
            device = cam
            val surface = reader?.surface
            if (surface == null) {
                Air.note("hand: no image surface, capture not started")
                return
            }
            try {
                cam.createCaptureSession(listOf(surface), sessionCallback, worker)
            } catch (e: Exception) {
                Air.note("hand: createCaptureSession threw ${e.javaClass.simpleName}: ${e.message}")
            }
        }

        override fun onDisconnected(cam: CameraDevice) {
            Air.note("hand: camera disconnected")
            cam.close()
        }

        override fun onError(cam: CameraDevice, error: Int) {
            // 3 = CAMERA_IN_USE_BY_HIGHER_PRIORITY_USER, 4 = typically a locked/disabled camera.
            Air.note("hand: camera error $error (held by another app, or disabled by policy)")
            cam.close()
        }
    }

    @Suppress("DEPRECATION")
    private val sessionCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(s: CameraCaptureSession) {
            session = s
            val cam = device ?: return
            val surface = reader?.surface ?: return
            try {
                val req = cam.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                req.addTarget(surface)
                req.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, android.util.Range(15, 30))
                req.set(CaptureRequest.JPEG_QUALITY, 70.toByte())
                s.setRepeatingRequest(req.build(), null, worker)
                running = true
                Air.note("hand: streaming $frameW x $frameH")
            } catch (e: Exception) {
                Air.note("hand: repeating request refused ${e.javaClass.simpleName}: ${e.message}")
            }
        }

        override fun onConfigureFailed(s: CameraCaptureSession) {
            Air.note("hand: capture session configuration failed")
        }
    }

    private val onFrame = ImageReader.OnImageAvailableListener { r ->
        if (!running) return@OnImageAvailableListener
        val img = try {
            r.acquireLatestImage()
        } catch (e: Exception) {
            Air.note("hand: acquireLatestImage threw ${e.javaClass.simpleName}")
            null
        } ?: return@OnImageAvailableListener
        var bmp: Bitmap? = null
        var raw: Bitmap? = null
        try {
            val buf: java.nio.ByteBuffer = img.planes[0].buffer
            val bytes = ByteArray(buf.remaining())
            buf.get(bytes)
            raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (raw == null) {
                Air.note("hand: JPEG frame did not decode")
            } else {
                val rot = (360 - sensorOrientation) % 360
                bmp = if (rot == 0) {
                    raw
                } else {
                    val m = Matrix()
                    m.postRotate(rot.toFloat())
                    Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, false)
                }
                val ts = SystemClock.elapsedRealtime()
                val mp = BitmapImageBuilder(bmp).build()
                val hands = marker?.detectForVideo(mp, ts)?.landmarks()
                frames += 1
                if (hands != null && hands.isNotEmpty()) {
                    framesWithHand += 1
                    onSample(toSample(ts, hands[0]))
                } else {
                    onSample(HandSample(ts, false, 0f, 0f, 1f, 0f))
                }
                if (frames % 90 == 0) {
                    Air.note("hand: tracked $framesWithHand of $frames frames")
                }
            }
        } catch (e: Exception) {
            Air.note("hand: frame pipeline threw ${e.javaClass.simpleName}: ${e.message}")
        } finally {
            img.close()
            if (raw != null && raw !== bmp && !raw.isRecycled) raw.recycle()
            if (bmp != null && !bmp.isRecycled) bmp.recycle()
        }
    }

    private fun toSample(ts: Long, lm: List<NormalizedLandmark>): HandSample {
        val wrist = lm[0]
        val thumb = lm[4]
        val index = lm[8]
        val middleBase = lm[9]
        val gapX = thumb.x() - index.x()
        val gapY = thumb.y() - index.y()
        val spanX = wrist.x() - middleBase.x()
        val spanY = wrist.y() - middleBase.y()
        // Dividing the pinch gap by the hand's own length keeps the threshold distance-proof.
        val span = sqrt(spanX * spanX + spanY * spanY).coerceAtLeast(0.02f)
        val gap = sqrt(gapX * gapX + gapY * gapY)
        return HandSample(ts, true, wrist.x(), wrist.y(), gap / span, span)
    }

    fun stop() {
        running = false
        try {
            session?.close()
        } catch (e: Exception) {
            Air.note("hand: session close threw ${e.javaClass.simpleName}")
        }
        session = null
        try {
            device?.close()
        } catch (e: Exception) {
            Air.note("hand: device close threw ${e.javaClass.simpleName}")
        }
        device = null
        reader?.close()
        reader = null
        thread?.quitSafely()
        thread = null
        worker = null
        try {
            marker?.close()
        } catch (e: Exception) {
            Air.note("hand: model close threw ${e.javaClass.simpleName}")
        }
        marker = null
        Air.note("hand: pipeline stopped")
    }

    private companion object {
        const val MODEL = "hand_landmarker.task"
    }
}
