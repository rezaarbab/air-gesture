package ir.airgesture.sense

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.PixelFormat
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
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.framework.image.MediaImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import ir.airgesture.Air
import ir.airgesture.decode.FaceSample
import ir.airgesture.decode.HandSample
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * One camera, two models, and instrumentation that answers the only question that matters when
 * nothing is recognized: **did a frame arrive, was a model called, and what did it return?**
 *
 * Two pixel paths, switched from the app, because they were both wrong once and each mistake was
 * only visible with these counters:
 *
 * - JPEG: `ImageReader(JPEG)` -> decode -> physically rotate -> `BitmapImageBuilder`. Proven to
 *   deliver frames and to be accepted by both models.
 * - RGBA: `ImageReader(RGBA_8888)` straight into `MediaImageBuilder`, rotation declared to
 *   `ImageProcessingOptions`. No decode and no bitmap copy, so it is the fast one. YUV_420_888 does
 *   NOT work here: MediaPipe answers `Android media image must use RGBA_8888 config` for every
 *   frame, which is what this option used to be before the log exposed it.
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
    private var rgba = false
    private var frameIndex = 0
    private var handStride = 2
    private var faceStride = 1
    private var wantFace = false
    private var wantHand = false
    private var openedAt = 0L

    private var faceTotalNs = 0L
    private var handTotalNs = 0L
    private var blinkNameMisses = 0
    private var faceDelegate = "?"
    private var handDelegate = "?"
    private var decodeFails = 0
    private var lastThrown = ""
    private var thrownRepeat = 0

    var faceCount = 0; private set
    var handCount = 0; private set
    var faceSeen = 0; private set
    var handSeen = 0; private set
    var lastProbe = ""; private set

    @Volatile
    private var running = false

    @Volatile
    private var framesDelivered = 0

    val faceAvgMs: Float get() = if (faceCount == 0) 0f else faceTotalNs.toFloat() / faceCount / 1e6f
    val handAvgMs: Float get() = if (handCount == 0) 0f else handTotalNs.toFloat() / handCount / 1e6f
    var frameW = 0; private set
    var frameH = 0; private set

    fun start(
        needFace: Boolean,
        needHand: Boolean,
        handStride: Int,
        faceStride: Int,
        preferGpu: Boolean,
        rotationOverride: Int,
        wantRgba: Boolean,
    ): Boolean {
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
        val chars = try {
            mgr.getCameraCharacteristics(camId)
        } catch (e: Exception) {
            Air.note("cam: getCameraCharacteristics threw ${e.javaClass.simpleName}")
            return false
        }
        rgba = wantRgba
        wantFace = needFace
        wantHand = needHand
        this.handStride = handStride.coerceIn(1, 6)
        this.faceStride = faceStride.coerceIn(1, 6)
        val sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        rotation = if (rotationOverride >= 0) rotationOverride else (360 - sensorOrientation) % 360
        val fmt = if (rgba) PixelFormat.RGBA_8888 else ImageFormat.JPEG
        val size = pickSize(chars, fmt)
        frameW = size.width
        frameH = size.height
        lastProbe = "${if (rgba) "RGBA" else "JPEG"} $frameW x $frameH rot=$rotation"
        Air.note("cam: format=${if (rgba) "RGBA" else "JPEG"} sensor=$sensorOrientation -> rotation=$rotation, frame $frameW x $frameH")

        if (needFace && openFace(preferGpu) == null) return false
        if (needHand && openHand(preferGpu) == null) return false
        // The line that found the "model built but never stored" bug: it names the live slot, not
        // the construction, so an unassigned field cannot hide behind a successful load log.
        Air.note("model: ready face=${slot(face, needFace)} hand=${slot(hand, needHand)} ($faceDelegate/$handDelegate)")

        val t = HandlerThread("air-cam")
        thread = t
        t.start()
        worker = Handler(t.looper)
        return try {
            val r = ImageReader.newInstance(frameW, frameH, fmt, 2)
            r.setOnImageAvailableListener(onFrame, worker)
            reader = r
            framesDelivered = 0
            openedAt = SystemClock.elapsedRealtime()
            mgr.openCamera(camId, stateCallback, worker)
            worker?.postDelayed(watchdog, WATCHDOG_MS)
            true
        } catch (e: Exception) {
            Air.note("cam: openCamera threw ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    private fun slot(o: Any?, needed: Boolean): String = when {
        !needed -> "not used"
        o != null -> "live"
        else -> "EMPTY"
    }

    /** The single line that separates "camera is silent" from "the model is not being called". */
    private val watchdog = object : Runnable {
        override fun run() {
            if (!running) {
                Air.note("cam: session never configured (HAL refused the request)")
                return
            }
            if (framesDelivered == 0) {
                Air.note(
                    "cam: NO FRAME DELIVERED in ${WATCHDOG_MS}ms with ${if (rgba) "RGBA" else "JPEG"} " +
                        "$frameW x $frameH - switch the frame format in Home",
                )
            } else {
                Air.note("cam: $framesDelivered frames in the first ${WATCHDOG_MS}ms")
            }
        }
    }

    private fun pickFront(mgr: CameraManager): String? {
        for (id in mgr.cameraIdList) {
            val facing = mgr.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING)
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) return id
        }
        return null
    }

    private fun pickSize(chars: CameraCharacteristics, fmt: Int): Size {
        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) as? StreamConfigurationMap
        val all: Array<Size> = map?.getOutputSizes(fmt) ?: emptyArray()
        val usable = all.filter { it.width in 320..1280 && it.height in 240..960 }
        val pool: List<Size> = if (usable.isEmpty()) all.toList() else usable
        Air.note("cam: ${all.size} sizes offered for ${if (rgba) "RGBA" else "JPEG"}, ${pool.size} in the 320-1280 band")
        val best = pool.minByOrNull {
            abs(0.75f - it.width.toFloat() / it.height.toFloat()) * 3f +
                abs(it.width - 640) / 640f * 0.4f + abs(it.height - 480) / 480f * 0.4f
        }
        return best ?: Size(640, 480)
    }

    private fun openFace(preferGpu: Boolean): FaceLandmarker? {
        val opts = faceOptions(preferGpu) ?: return null
        val m = try {
            FaceLandmarker.createFromOptions(ctx, opts).also { faceDelegate = if (preferGpu) "GPU" else "CPU" }
        } catch (e: Exception) {
            if (!preferGpu) {
                Air.note("model: face load failed (${e.javaClass.simpleName}: ${e.message})")
                return null
            }
            Air.note("model: GPU refused the face model (${e.javaClass.simpleName}), retrying on CPU")
            val cpu = faceOptions(false) ?: return null
            try {
                FaceLandmarker.createFromOptions(ctx, cpu).also { faceDelegate = "CPU-fallback" }
            } catch (e2: Exception) {
                Air.note("model: face CPU load failed too (${e2.javaClass.simpleName}: ${e2.message})")
                return null
            }
        }
        face = m
        return m
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
            .setMinFaceDetectionConfidence(0.4f)
            .setMinFacePresenceConfidence(0.4f)
            .setOutputFaceBlendshapes(true)
            .build()
    } catch (e: Exception) {
        Air.note("model: face options rejected (${e.javaClass.simpleName}: ${e.message})")
        null
    }

    private fun openHand(preferGpu: Boolean): HandLandmarker? {
        val opts = handOptions(preferGpu) ?: return null
        val m = try {
            HandLandmarker.createFromOptions(ctx, opts).also { handDelegate = if (preferGpu) "GPU" else "CPU" }
        } catch (e: Exception) {
            if (!preferGpu) {
                Air.note("model: hand load failed (${e.javaClass.simpleName}: ${e.message})")
                return null
            }
            Air.note("model: GPU refused the hand model (${e.javaClass.simpleName}), retrying on CPU")
            val cpu = handOptions(false) ?: return null
            try {
                HandLandmarker.createFromOptions(ctx, cpu).also { handDelegate = "CPU-fallback" }
            } catch (e2: Exception) {
                Air.note("model: hand CPU load failed too (${e2.javaClass.simpleName}: ${e2.message})")
                return null
            }
        }
        hand = m
        return m
    }

    private fun handOptions(gpu: Boolean): HandLandmarker.HandLandmarkerOptions? = try {
        val base = BaseOptions.builder()
            .setModelAssetPath(HAND_MODEL)
            .setDelegate(if (gpu) Delegate.GPU else Delegate.CPU)
            .build()
        HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(base)
            .setRunningMode(RunningMode.VIDEO)
            // Two hands: he asked for both to be tracked, and a pinch with the free hand is the
            // normal way to click while the other hand keeps the pointer where it is.
            .setNumHands(2)
            .setMinHandDetectionConfidence(0.4f)
            .setMinHandPresenceConfidence(0.4f)
            .setMinTrackingConfidence(0.4f)
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
                req.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(15, 30))
                if (!rgba) req.set(CaptureRequest.JPEG_QUALITY, 80.toByte())
                s.setRepeatingRequest(req.build(), null, worker)
                running = true
                Air.note("cam: streaming ${if (rgba) "RGBA" else "JPEG"} $frameW x $frameH rot=$rotation, hand/$faceStride face/$faceStride")
            } catch (e: Exception) {
                Air.note("cam: repeating request refused ${e.javaClass.simpleName}: ${e.message}")
            }
        }

        override fun onConfigureFailed(s: CameraCaptureSession) {
            Air.note("cam: capture session configuration failed (size or format refused)")
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
        framesDelivered += 1
        if (framesDelivered == 1) {
            Air.note("cam: first frame after ${SystemClock.elapsedRealtime() - openedAt}ms")
        }
        var mp: MPImage? = null
        var bmp: Bitmap? = null
        try {
            val ts = SystemClock.elapsedRealtime()
            frameIndex += 1
            val doHand = wantHand && (frameIndex % handStride == 0)
            val doFace = wantFace && (frameIndex % faceStride == 0)
            if (rgba) {
                mp = MediaImageBuilder(img).build()
                val proc = ImageProcessingOptions.builder().setRotationDegrees(rotation).build()
                if (doFace) runFace(ts, proc, mp)
                if (doHand) runHand(ts, proc, mp)
            } else {
                bmp = decodeJpeg(img)
                if (bmp == null) {
                    decodeFails += 1
                    if (decodeFails == 1 || decodeFails % 60 == 0) {
                        Air.note("cam: JPEG decode failed $decodeFails times (frame delivered, not decodable)")
                    }
                } else {
                    mp = BitmapImageBuilder(bmp).build()
                    if (doFace) runFace(ts, null, mp)
                    if (doHand) runHand(ts, null, mp)
                }
            }
            reportProgress()
        } catch (e: Exception) {
            reportThrown(e)
        } finally {
            try {
                mp?.close()
            } catch (e: Exception) {
                Air.note("cam: image release threw ${e.javaClass.simpleName}")
            }
            img.close()
            if (bmp != null && !bmp.isRecycled) bmp.recycle()
        }
    }

    /**
     * A per-frame exception at 26 fps is 1500 identical lines a minute, and a log nobody can read
     * is not evidence. Repeats are counted, and the count surfaces in the probe line.
     */
    private fun reportThrown(e: Exception) {
        val msg = "${e.javaClass.simpleName}: ${e.message}"
        if (msg == lastThrown) {
            thrownRepeat += 1
            return
        }
        if (thrownRepeat > 0) Air.note("cam: repeated $thrownRepeat more times")
        thrownRepeat = 0
        lastThrown = msg
        Air.note("cam: frame pipeline threw $msg")
    }

    private fun decodeJpeg(img: android.media.Image): Bitmap? {
        val plane = img.planes[0]
        val buf = plane.buffer
        val bytes = ByteArray(buf.remaining())
        buf.get(bytes)
        val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        // Clockwise by `rotation`, exactly what the build he says worked did. The complement is 180
        // degrees off, which is how "nothing is recognized" started once already.
        if (rotation % 360 == 0) return raw
        val m = Matrix()
        m.postRotate(rotation.toFloat())
        val out = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, false)
        if (out !== raw) raw.recycle()
        return out
    }

    private fun reportProgress() {
        val extra = buildString {
            if (decodeFails > 0) append(" decode-fail=$decodeFails")
            if (thrownRepeat > 0) append(" repeats=$thrownRepeat")
        }
        if (wantFace && faceCount > 0 && faceCount % 20 == 0) {
            Air.note("probe: face found in $faceSeen of $faceCount frames, ${ms(faceAvgMs)} ms each$extra")
        }
        if (wantHand && handCount > 0 && handCount % 20 == 0) {
            Air.note("probe: hand found in $handSeen of $handCount frames, ${ms(handAvgMs)} ms each$extra")
        }
    }

    private fun ms(v: Float): String = String.format(java.util.Locale.US, "%.1f", v)

    private fun runFace(ts: Long, proc: ImageProcessingOptions?, mp: MPImage) {
        val m = face ?: return
        val t0 = System.nanoTime()
        val res = if (proc == null) m.detectForVideo(mp, ts) else m.detectForVideo(mp, proc, ts)
        faceTotalNs += System.nanoTime() - t0
        faceCount += 1
        emitFace(ts, res)
    }

    private fun emitFace(ts: Long, res: FaceLandmarkerResult) {
        val faces = res.faceLandmarks()
        if (faces.isEmpty() || faces[0].size < 2) {
            onFace(FaceSample(ts, false, 0f, 0f, 0f))
            return
        }
        faceSeen += 1
        val nose = faces[0][NOSE_TIP]
        onFace(FaceSample(ts, true, nose.x(), nose.y(), blinkScore(res)))
    }

    private fun runHand(ts: Long, proc: ImageProcessingOptions?, mp: MPImage) {
        val m = hand ?: return
        val t0 = System.nanoTime()
        val hands = if (proc == null) m.detectForVideo(mp, ts).landmarks() else m.detectForVideo(mp, proc, ts).landmarks()
        handTotalNs += System.nanoTime() - t0
        handCount += 1
        if (hands.isEmpty()) {
            onHand(HandSample(ts, false, 0f, 0f, 1f, 0f, handIndex = 0))
            return
        }
        handSeen += 1
        for (i in hands.indices.take(2)) emitHand(ts, hands[i], i)
    }

    private fun emitHand(ts: Long, lm: List<NormalizedLandmark>, handIndex: Int) {
        if (lm.size < 10) {
            onHand(HandSample(ts, false, 0f, 0f, 1f, 0f, handIndex = handIndex))
            return
        }
        val wrist = lm[0]
        val thumb = lm[4]
        val index = lm[8]
        val middleBase = lm[9]
        val gapX = thumb.x() - index.x()
        val gapY = thumb.y() - index.y()
        val spanX = wrist.x() - middleBase.x()
        val spanY = wrist.y() - middleBase.y()
        val span = sqrt(spanX * spanX + spanY * spanY).coerceAtLeast(0.02f)
        val gap = sqrt(gapX * gapX + gapY * gapY)
        val pts = FloatArray(lm.size * 2)
        for (i in lm.indices) {
            pts[i * 2] = lm[i].x()
            pts[i * 2 + 1] = lm[i].y()
        }
        // x/y is the index fingertip: in finger-pointer mode that is what drives the cursor, and the
        // wrist stays in the sample for the swipe gestures.
        onHand(HandSample(ts, true, wrist.x(), wrist.y(), gap / span, span, pts, index.x(), index.y(), handIndex))
    }

    /** The blendshape list is named; if the names ever change, say so instead of inventing a score. */
    private fun blinkScore(res: FaceLandmarkerResult): Float {
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

    fun stop() {
        running = false
        worker?.removeCallbacks(watchdog)
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
        const val WATCHDOG_MS = 3000L
    }
}
