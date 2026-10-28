package ir.airgesture.sense

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
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
import android.util.Range
import android.util.Size
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.MediaImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import ir.airgesture.Air
import ir.airgesture.decode.FaceSample
import ir.airgesture.decode.HandSample
import kotlin.math.abs

/**
 * One camera, two models, YUV frames handed straight to MediaPipe.
 *
 * The frames used to go JPEG -> Bitmap -> rotated Bitmap, and that plumbing cost more than the
 * model did. Now the sensor image goes in as it is and the rotation is declared through
 * ImageProcessingOptions, so nothing is decoded or copied per frame.
 *
 * In pointer mode the face model runs every frame (the cursor needs it) and the hand model every
 * Nth frame (a pinch lasts hundreds of milliseconds, so sampling it at a tenth of the rate costs
 * nothing the user can feel and buys back most of the frame time).
 */
class CameraSource(
    private val ctx: Context,
    private val onFace: (FaceSample) -> Unit,
    private val onHand: (HandSample) -> Unit,
) {

    private var thread: HandlerThread? = null
    private var worker: Handler? = null
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var face: FaceLandmarker? = null
    private var hand: HandLandmarker? = null

    private var rotation = 0
    private var frameIndex = 0
    private var stride = 2
    private var wantFace = false
    private var wantHand = false

    private var faceTotalNs = 0L
    private var faceCount = 0
    private var handTotalNs = 0L
    private var handCount = 0
    private var blinkNameMisses = 0

    @Volatile
    private var running = false

    val faceAvgMs: Float get() = if (faceCount == 0) 0f else faceTotalNs.toFloat() / faceCount / 1e6f
    val handAvgMs: Float get() = if (handCount == 0) 0f else handTotalNs.toFloat() / handCount / 1e6f
    var frameW = 0; private set
    var frameH = 0; private set

    fun start(needFace: Boolean, needHand: Boolean, handStride: Int, preferGpu: Boolean): Boolean {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Air.note("cam: CAMERA permission not granted")
            return false
        }
        val mgr = ctx.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        if (mgr == null) {
            Air.note("cam: CameraManager unavailable")
            return false
        }
        val camId = pickFront(mgr)
        if (camId == null) {
            Air.note("cam: no front-facing camera on this device")
            return false
        }
        wantFace = needFace
        wantHand = needHand
        stride = handStride.coerceIn(1, 6)
        val size = pickSize(mgr, camId)
        frameW = size.width
        frameH = size.height

        if (needFace && openFace(preferGpu) == null) return false
        if (needHand && openHand(preferGpu) == null) return false

        val t = HandlerThread("air-cam")
        thread = t
        t.start()
        worker = Handler(t.looper)
        return try {
            rotation = mgr.getCameraCharacteristics(camId)
                .get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
            val r = ImageReader.newInstance(frameW, frameH, ImageFormat.YUV_420_888, 2)
            r.setOnImageAvailableListener(onFrame, worker)
            reader = r
            mgr.openCamera(camId, stateCallback, worker)
            true
        } catch (e: Exception) {
            Air.note("cam: openCamera threw ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    private fun pickFront(mgr: CameraManager): String? {
        for (id in mgr.cameraIdList) {
            val facing = mgr.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING)
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) return id
        }
        return null
    }

    private fun pickSize(mgr: CameraManager, camId: String): Size {
        val map = mgr.getCameraCharacteristics(camId)
            .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) as? StreamConfigurationMap
        val sizes = map?.getOutputSizes(ImageFormat.YUV_420_888)
        val best = sizes?.minByOrNull {
            abs(0.75f - it.width.toFloat() / it.height.toFloat()) +
                abs(it.width - 640) / 10000f + abs(it.height - 480) / 10000f
        }
        return best ?: Size(640, 480)
    }

    /** GPU first, because it is usually twice as fast - and CPU on any ROM that refuses it. */
    private fun openFace(preferGpu: Boolean): FaceLandmarker? {
        val opts = faceOptions(preferGpu) ?: return null
        return try {
            FaceLandmarker.createFromOptions(ctx, opts).also {
                Air.note("model: face on ${if (preferGpu) "GPU" else "CPU"}, $frameW x $frameH")
            }
        } catch (e: Exception) {
            if (preferGpu) {
                Air.note("model: GPU refused the face model (${e.javaClass.simpleName}), retrying on CPU")
                val cpu = faceOptions(false) ?: return null
                try {
                    FaceLandmarker.createFromOptions(ctx, cpu)
                } catch (e2: Exception) {
                    Air.note("model: face CPU load failed too (${e2.javaClass.simpleName}: ${e2.message})")
                    null
                }
            } else {
                Air.note("model: face load failed (${e.javaClass.simpleName}: ${e.message})")
                null
            }
        }
    }

    private fun faceOptions(gpu: Boolean): FaceLandmarker.FaceLandmarkerOptions? = try {
        val base = BaseOptions.builder()
            .setModelAssetPath(FACE_MODEL)
            .setDelegate(if (gpu) Delegate.GPU else Delegate.CPU)
            .build()
        FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(base)
            .setRunningMode(RunningMode.VIDEO)
            .setNumFaces(1)
            .setMinFaceDetectionConfidence(0.5f)
            .setMinFacePresenceConfidence(0.5f)
            .setOutputFaceBlendshapes(true)
            .build()
    } catch (e: Exception) {
        Air.note("model: face options rejected (${e.javaClass.simpleName}: ${e.message})")
        null
    }

    private fun openHand(preferGpu: Boolean): HandLandmarker? {
        val opts = handOptions(preferGpu) ?: return null
        return try {
            HandLandmarker.createFromOptions(ctx, opts).also {
                Air.note("model: hand on ${if (preferGpu) "GPU" else "CPU"}")
            }
        } catch (e: Exception) {
            if (preferGpu) {
                Air.note("model: GPU refused the hand model (${e.javaClass.simpleName}), retrying on CPU")
                val cpu = handOptions(false) ?: return null
                try {
                    HandLandmarker.createFromOptions(ctx, cpu)
                } catch (e2: Exception) {
                    Air.note("model: hand CPU load failed too (${e2.javaClass.simpleName}: ${e2.message})")
                    null
                }
            } else {
                Air.note("model: hand load failed (${e.javaClass.simpleName}: ${e.message})")
                null
            }
        }
    }

    private fun handOptions(gpu: Boolean): HandLandmarker.HandLandmarkerOptions? = try {
        val base = BaseOptions.builder()
            .setModelAssetPath(HAND_MODEL)
            .setDelegate(if (gpu) Delegate.GPU else Delegate.CPU)
            .build()
        HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(base)
            .setRunningMode(RunningMode.VIDEO)
            .setNumHands(1)
            .setMinHandDetectionConfidence(0.5f)
            .setMinHandPresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .build()
    } catch (e: Exception) {
        Air.note("model: hand options rejected (${e.javaClass.simpleName}: ${e.message})")
        null
    }

    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(cam: CameraDevice) {
            device = cam
            val surface = reader?.surface
            if (surface == null) {
                Air.note("cam: no image surface, capture not started")
                return
            }
            try {
                cam.createCaptureSession(listOf(surface), sessionCallback, worker)
            } catch (e: Exception) {
                Air.note("cam: createCaptureSession threw ${e.javaClass.simpleName}: ${e.message}")
            }
        }

        override fun onDisconnected(cam: CameraDevice) {
            Air.note("cam: camera disconnected")
            cam.close()
        }

        override fun onError(cam: CameraDevice, error: Int) {
            // 3 = CAMERA_IN_USE_BY_HIGHER_PRIORITY_USER, 4 = camera disabled by policy or locked.
            Air.note("cam: camera error $error (held by another app, or disabled by policy)")
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
                req.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(20, 30))
                s.setRepeatingRequest(req.build(), null, worker)
                running = true
                Air.note("cam: streaming $frameW x $frameH, rotation $rotation, hand stride $stride")
            } catch (e: Exception) {
                Air.note("cam: repeating request refused ${e.javaClass.simpleName}: ${e.message}")
            }
        }

        override fun onConfigureFailed(s: CameraCaptureSession) {
            Air.note("cam: capture session configuration failed")
        }
    }

    private val onFrame = ImageReader.OnImageAvailableListener { r ->
        if (!running) return@OnImageAvailableListener
        val img = try {
            r.acquireLatestImage()
        } catch (e: Exception) {
            Air.note("cam: acquireLatestImage threw ${e.javaClass.simpleName}")
            null
        } ?: return@OnImageAvailableListener
        var mp: com.google.mediapipe.framework.image.MPImage? = null
        try {
            mp = MediaImageBuilder(img).build()
            val ts = SystemClock.elapsedRealtime()
            val proc = ImageProcessingOptions.builder().setRotationDegrees(rotation).build()
            frameIndex += 1
            val doHand = wantHand && (frameIndex % stride == 0)
            if (wantFace) runFace(ts, proc, mp)
            if (doHand) runHand(ts, proc, mp)
        } catch (e: Exception) {
            Air.note("cam: frame pipeline threw ${e.javaClass.simpleName}: ${e.message}")
        } finally {
            try {
                mp?.close()
            } catch (e: Exception) {
                Air.note("cam: image release threw ${e.javaClass.simpleName}")
            }
            img.close()
        }
    }

    private fun runFace(ts: Long, proc: ImageProcessingOptions, mp: com.google.mediapipe.framework.image.MPImage) {
        val m = face ?: return
        val t0 = System.nanoTime()
        val res = m.detectForVideo(mp, proc, ts)
        faceTotalNs += System.nanoTime() - t0
        faceCount += 1
        val faces = res.faceLandmarks()
        if (faces.isEmpty() || faces[0].size < 2) {
            onFace(FaceSample(ts, false, 0f, 0f, 0f))
            return
        }
        val nose = faces[0][NOSE_TIP]
        onFace(FaceSample(ts, true, nose.x(), nose.y(), blinkScore(res, ts)))
    }

    private fun runHand(ts: Long, proc: ImageProcessingOptions, mp: com.google.mediapipe.framework.image.MPImage) {
        val m = hand ?: return
        val t0 = System.nanoTime()
        val hands = m.detectForVideo(mp, proc, ts).landmarks()
        handTotalNs += System.nanoTime() - t0
        handCount += 1
        if (hands.isEmpty() || hands[0].size < 10) {
            onHand(HandSample(ts, false, 0f, 0f, 1f, 0f))
            return
        }
        val lm = hands[0]
        onHand(toSample(ts, lm))
    }

    /** The blendshape list is named; if the names ever change, say so instead of inventing a score. */
    private fun blinkScore(res: com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult, ts: Long): Float {
        val groups = res.faceBlendshapes()
        if (groups == null || !groups.isPresent) return 0f
        val list = groups.get()
        if (list.isEmpty()) return 0f
        var best = 0f
        var matched = false
        for (c in list[0]) {
            if (c.categoryName().startsWith("eyeBlink")) {
                matched = true
                if (c.score() > best) best = c.score()
            }
        }
        if (!matched) {
            blinkNameMisses += 1
            if (blinkNameMisses == 30) Air.note("model: no eyeBlink blendshape in this build, blink disabled")
        }
        return if (matched) best else 0f
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
        val span = kotlin.math.sqrt(spanX * spanX + spanY * spanY).coerceAtLeast(0.02f)
        val gap = kotlin.math.sqrt(gapX * gapX + gapY * gapY)
        return HandSample(ts, true, wrist.x(), wrist.y(), gap / span, span)
    }

    fun stop() {
        running = false
        try {
            session?.close()
        } catch (e: Exception) {
            Air.note("cam: session close threw ${e.javaClass.simpleName}")
        }
        session = null
        try {
            device?.close()
        } catch (e: Exception) {
            Air.note("cam: device close threw ${e.javaClass.simpleName}")
        }
        device = null
        reader?.close()
        reader = null
        thread?.quitSafely()
        thread = null
        worker = null
        try {
            face?.close()
        } catch (e: Exception) {
            Air.note("model: face close threw ${e.javaClass.simpleName}")
        }
        face = null
        try {
            hand?.close()
        } catch (e: Exception) {
            Air.note("model: hand close threw ${e.javaClass.simpleName}")
        }
        hand = null
        Air.note("cam: pipeline stopped")
    }

    private companion object {
        const val FACE_MODEL = "face_landmarker.task"
        const val HAND_MODEL = "hand_landmarker.task"
        const val NOSE_TIP = 1
    }
}
