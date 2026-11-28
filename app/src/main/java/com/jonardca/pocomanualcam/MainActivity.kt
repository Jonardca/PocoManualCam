package com.jonardca.pocomanualcam

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Range
import android.view.OrientationEventListener
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.atan2

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var textureView: TextureView
    private lateinit var aspectOverlay: AspectRatioOverlayView
    private lateinit var isoSeekBar: SeekBar
    private lateinit var shutterSeekBar: SeekBar
    private lateinit var focusSeekBar: SeekBar
    private lateinit var wbSeekBar: SeekBar
    private lateinit var evSeekBar: SeekBar
    private lateinit var zoomSeekBar: SeekBar

    private lateinit var isoLabel: TextView
    private lateinit var shutterLabel: TextView
    private lateinit var focusLabel: TextView
    private lateinit var wbLabel: TextView
    private lateinit var evLabel: TextView
    private lateinit var zoomLabel: TextView

    private lateinit var captureButton: Button
    private lateinit var galleryButton: Button
    private lateinit var ratioButton: Button
    private lateinit var btnRatioGuide: Button
    private lateinit var btnFaceDetect: Button
    private lateinit var btnMotionTrack: Button
    private lateinit var seekBarsContainer: LinearLayout
    private lateinit var toggleControls: TextView
    private lateinit var crossVertical: View
    private lateinit var crossHorizontal: View

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var backgroundHandler: Handler? = null
    private var backgroundThread: HandlerThread? = null
    private var imageReader: ImageReader? = null
    private var motionImageReader: ImageReader? = null

    private var isoRange: Range<Int>? = null
    private var exposureRange: Range<Long>? = null
    private var focusRange: Range<Float>? = null
    private var sensorRect: Rect? = null
    private var sensorOrientation = 90
    private var currentCropRegion: Rect? = null

    private var currentIso = 100
    private var currentExposure = 30_000_000L
    private var currentFocus = 0f
    private var currentWbMode = CaptureRequest.CONTROL_AWB_MODE_AUTO
    private var currentEv = 0
    private var currentZoom = 1.0f
    private var controlsVisible = true
    private var isFourFive = false
    private var isFourFiveGuide = false
    private var isFaceDetectEnabled = false
    private var isMotionTrackEnabled = false

    private var isIsoAuto = true
    private var isShutterAuto = true
    private var isFocusAuto = true

    private var currentJpegOrientation = 90
    private var orientationEventListener: OrientationEventListener? = null
    private var motionDetector: MotionFocusDetector? = null

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private val shutterSpeeds = arrayOf(
        -1L,
        250_000L, 312_500L, 400_000L, 500_000L, 625_000L, 800_000L, 1_000_000L,
        1_250_000L, 1_562_500L, 2_000_000L, 2_500_000L, 3_125_000L, 4_000_000L,
        5_000_000L, 6_250_000L, 8_000_000L, 10_000_000L, 12_500_000L, 16_666_667L,
        20_000_000L, 25_000_000L, 33_333_333L, 40_000_000L, 50_000_000L, 66_666_667L,
        76_923_077L, 100_000_000L, 125_000_000L, 166_666_667L, 200_000_000L, 250_000_000L,
        300_000_000L, 400_000_000L, 500_000_000L, 600_000_000L, 800_000_000L, 900_000_000L,
        1_000_000_000L, 1_300_000_000L, 1_600_000_000L, 1_800_000_000L, 2_000_000_000L,
        2_300_000_000L, 2_500_000_000L, 2_800_000_000L, 3_000_000_000L, 3_200_000_000L,
        3_600_000_000L, 3_800_000_000L, 4_000_000_000L, 5_000_000_000L, 6_000_000_000L,
        8_000_000_000L, 10_000_000_000L, 13_000_000_000L, 15_000_000_000L, 20_000_000_000L,
        25_000_000_000L, 30_000_000_000L
    )

    private val shutterLabels = arrayOf(
        "Auto",
        "1/4000", "1/3200", "1/2500", "1/2000", "1/1600", "1/1250", "1/1000",
        "1/800", "1/640", "1/500", "1/400", "1/320", "1/250", "1/200", "1/160",
        "1/125", "1/100", "1/80", "1/60", "1/50", "1/40", "1/30", "1/25", "1/20",
        "1/15", "1/13", "1/10", "1/8", "1/6", "1/5", "1/4",
        "0.3s", "0.4s", "0.5s", "0.6s", "0.8s", "0.9s", "1s",
        "1.3s", "1.6s", "1.8s", "2s", "2.3s", "2.5s", "2.8s", "3s",
        "3.2s", "3.6s", "3.8s", "4s", "5s", "6s", "8s", "10s",
        "13s", "15s", "20s", "25s", "30s"
    )

    private val kelvinValues = intArrayOf(
        0, 2000, 2200, 2400, 2600, 2800, 3000, 3200, 3400, 3600, 3800,
        4000, 4200, 4400, 4600, 4800, 5000, 5200, 5400, 5600, 5800,
        6000, 6200, 6400, 6600, 6800, 7000, 7200, 7400, 7600, 7800, 8000
    )

    private val cameraManager by lazy { getSystemService(CAMERA_SERVICE) as CameraManager }

    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            if (!isFaceDetectEnabled) return

            val faces = result.get(CaptureResult.STATISTICS_FACES) ?: return
            val activeArray = sensorRect ?: return
            val viewW = textureView.width
            val viewH = textureView.height
            if (viewW == 0 || viewH == 0) return

            val boxes = mutableListOf<RectF>()
            for (face in faces) {
                mapSensorRectToView(face.bounds, activeArray, viewW, viewH, sensorOrientation)?.let {
                    boxes.add(it)
                }
            }
            runOnUiThread { aspectOverlay.updateFaceBoxes(boxes) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textureView = findViewById(R.id.textureView)
        aspectOverlay = findViewById(R.id.aspectOverlay)
        isoSeekBar = findViewById(R.id.isoSeekBar)
        shutterSeekBar = findViewById(R.id.shutterSeekBar)
        focusSeekBar = findViewById(R.id.focusSeekBar)
        wbSeekBar = findViewById(R.id.wbSeekBar)
        evSeekBar = findViewById(R.id.evSeekBar)
        zoomSeekBar = findViewById(R.id.zoomSeekBar)

        isoLabel = findViewById(R.id.isoLabel)
        shutterLabel = findViewById(R.id.shutterLabel)
        focusLabel = findViewById(R.id.focusLabel)
        wbLabel = findViewById(R.id.wbLabel)
        evLabel = findViewById(R.id.evLabel)
        zoomLabel = findViewById(R.id.zoomLabel)

        captureButton = findViewById(R.id.captureButton)
        galleryButton = findViewById(R.id.galleryButton)
        ratioButton = findViewById(R.id.ratioButton)
        btnRatioGuide = findViewById(R.id.btnRatioGuide)
        btnFaceDetect = findViewById(R.id.btnFaceDetect)
        btnMotionTrack = findViewById(R.id.btnMotionTrack)
        seekBarsContainer = findViewById(R.id.seekBarsContainer)
        toggleControls = findViewById(R.id.toggleControls)
        crossVertical = findViewById(R.id.crossVertical)
        crossHorizontal = findViewById(R.id.crossHorizontal)

        btnFaceDetect.isEnabled = true
        btnMotionTrack.isEnabled = true

        shutterSeekBar.max = shutterSpeeds.size - 1
        wbSeekBar.max = kelvinValues.size - 1
        isoSeekBar.max = 100
        focusSeekBar.max = 100
        zoomSeekBar.max = 100

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        setupOrientationListener()
        setupSeekBars()

        isoSeekBar.progress = 0
        focusSeekBar.progress = 0
        shutterSeekBar.progress = 0
        wbSeekBar.progress = 0
        evSeekBar.progress = 24
        zoomSeekBar.progress = 0

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 100)
        }

        captureButton.setOnClickListener { takePhoto() }
        galleryButton.setOnClickListener { openGallery() }

        ratioButton.setOnClickListener {
            isFourFive = !isFourFive
            ratioButton.text = if (isFourFive) "4:5" else "3:4"
            ratioButton.setBackgroundColor(if (isFourFive) Color.parseColor("#4CAF50") else Color.parseColor("#666666"))
        }

        btnRatioGuide.setOnClickListener {
            isFourFiveGuide = !isFourFiveGuide
            aspectOverlay.isFourFiveEnabled = isFourFiveGuide
            btnRatioGuide.text = if (isFourFiveGuide) "4:5 Guide" else "3:4"
            btnRatioGuide.setBackgroundColor(if (isFourFiveGuide) Color.parseColor("#4CAF50") else Color.parseColor("#555555"))
        }

        btnFaceDetect.setOnClickListener {
            isFaceDetectEnabled = !isFaceDetectEnabled
            btnFaceDetect.text = if (isFaceDetectEnabled) "Face ON" else "Face"
            btnFaceDetect.setBackgroundColor(if (isFaceDetectEnabled) Color.parseColor("#4CAF50") else Color.parseColor("#555555"))
            if (!isFaceDetectEnabled) aspectOverlay.clearFaceBoxes()
            updatePreview()
        }

        btnMotionTrack.setOnClickListener {
            isMotionTrackEnabled = !isMotionTrackEnabled
            btnMotionTrack.text = if (isMotionTrackEnabled) "Track ON" else "Track"
            btnMotionTrack.setBackgroundColor(if (isMotionTrackEnabled) Color.parseColor("#4CAF50") else Color.parseColor("#555555"))
            if (!isMotionTrackEnabled) {
                motionDetector?.reset()
                aspectOverlay.clearFaceBoxes()
            }
        }

        toggleControls.setOnClickListener {
            controlsVisible = !controlsVisible
            seekBarsContainer.visibility = if (controlsVisible) View.VISIBLE else View.GONE
            toggleControls.text = if (controlsVisible) "▲ Hide Controls" else "▼ Show Controls"
        }
    }

    private fun mapSensorRectToView(
        sensorFace: Rect,
        activeArray: Rect,
        viewW: Int,
        viewH: Int,
        orientation: Int
    ): RectF? {
        val sensorW = activeArray.width().toFloat()
        val sensorH = activeArray.height().toFloat()
        if (sensorW <= 0 || sensorH <= 0) return null

        val left: Float
        val top: Float
        val right: Float
        val bottom: Float

        when (orientation) {
            90 -> {
                val scaleX = viewW / sensorH
                val scaleY = viewH / sensorW
                left = (sensorH - sensorFace.bottom) * scaleX
                top = sensorFace.left * scaleY
                right = (sensorH - sensorFace.top) * scaleX
                bottom = sensorFace.right * scaleY
            }
            270 -> {
                val scaleX = viewW / sensorH
                val scaleY = viewH / sensorW
                left = sensorFace.top * scaleX
                top = (sensorW - sensorFace.right) * scaleY
                right = sensorFace.bottom * scaleX
                bottom = (sensorW - sensorFace.left) * scaleY
            }
            else -> {
                val scaleX = viewW / sensorW
                val scaleY = viewH / sensorH
                left = sensorFace.left * scaleX
                top = sensorFace.top * scaleY
                right = sensorFace.right * scaleX
                bottom = sensorFace.bottom * scaleY
            }
        }
        return RectF(left, top, right, bottom)
    }

    private fun mapPreviewBoxToView(previewBox: Rect, previewW: Int, previewH: Int): RectF {
        val viewW = textureView.width.toFloat()
        val viewH = textureView.height.toFloat()
        val scaleX = viewW / previewW
        val scaleY = viewH / previewH
        return RectF(
            previewBox.left * scaleX,
            previewBox.top * scaleY,
            previewBox.right * scaleX,
            previewBox.bottom * scaleY
        )
    }

    private fun setupOrientationListener() {
        orientationEventListener = object : OrientationEventListener(this) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                currentJpegOrientation = when {
                    orientation in 45..134 -> 180
                    orientation in 135..224 -> 270
                    orientation in 225..314 -> 0
                    else -> 90
                }
            }
        }
    }

    private fun setupSeekBars() {
        isoSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (progress == 0) {
                    isIsoAuto = true
                    isoLabel.text = "ISO: Auto"
                } else {
                    isIsoAuto = false
                    isoRange?.let {
                        currentIso = it.lower + ((progress - 1) * (it.upper - it.lower) / 99)
                        isoLabel.text = "ISO: $currentIso"
                    }
                }
                updatePreview()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        shutterSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val index = progress.coerceIn(0, shutterSpeeds.size - 1)
                if (index == 0) {
                    isShutterAuto = true
                    shutterLabel.text = "Shutter: Auto"
                } else {
                    isShutterAuto = false
                    currentExposure = shutterSpeeds[index]
                    shutterLabel.text = "Shutter: ${shutterLabels[index]}"
                }
                updatePreview()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        focusSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (progress == 0) {
                    isFocusAuto = true
                    focusLabel.text = "Focus: Auto"
                } else {
                    isFocusAuto = false
                    focusRange?.let {
                        currentFocus = it.lower + ((progress - 1) * (it.upper - it.lower) / 99f)
                        focusLabel.text = "Focus: ${"%.2f".format(currentFocus)}"
                    }
                }
                updatePreview()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        wbSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val index = progress.coerceIn(0, kelvinValues.size - 1)
                if (index == 0) {
                    currentWbMode = CaptureRequest.CONTROL_AWB_MODE_AUTO
                    wbLabel.text = "WB: Auto"
                } else {
                    val kelvin = kelvinValues[index]
                    currentWbMode = kelvinToAwbMode(kelvin)
                    wbLabel.text = "WB: ${kelvin}K"
                }
                updatePreview()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        evSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentEv = progress - 24
                evLabel.text = "EV: ${if (currentEv > 0) "+$currentEv" else "$currentEv"}"
                updatePreview()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        zoomSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentZoom = 1.0f + (progress / 100f) * 9.0f
                zoomLabel.text = "Zoom: ${"%.1f".format(currentZoom)}x"
                updatePreview()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun kelvinToAwbMode(kelvin: Int): Int {
        return when {
            kelvin <= 2800 -> CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT
            kelvin <= 3600 -> CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT
            kelvin <= 4500 -> CaptureRequest.CONTROL_AWB_MODE_WARM_FLUORESCENT
            kelvin <= 5500 -> CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT
            kelvin <= 6500 -> CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT
            kelvin <= 7500 -> CaptureRequest.CONTROL_AWB_MODE_TWILIGHT
            else -> CaptureRequest.CONTROL_AWB_MODE_SHADE
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
        val x = event.values[0]
        val y = event.values[1]
        val angle = Math.toDegrees(atan2(x.toDouble(), y.toDouble())).toFloat()
        crossHorizontal.rotation = -angle
        val isLevel = abs(angle) < 2.5f
        val color = if (isLevel) Color.GREEN else Color.WHITE
        crossHorizontal.setBackgroundColor(color)
        crossVertical.setBackgroundColor(color)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun openCamera() {
        try {
            val cameraId = cameraManager.cameraIdList[0]
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)

            isoRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
            exposureRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
            val minFocus = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
            focusRange = Range(0f, minFocus)
            sensorRect = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
            sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90

            sensorRect?.let {
                motionDetector = MotionFocusDetector(it)
            }

            imageReader = ImageReader.newInstance(3472, 4624, ImageFormat.JPEG, 2)
            imageReader?.setOnImageAvailableListener({ reader ->
                reader.acquireLatestImage()?.let { saveImage(it) }
            }, backgroundHandler)

            // Low-res for motion detection
            motionImageReader = ImageReader.newInstance(320, 240, ImageFormat.YUV_420_888, 2)
            motionImageReader?.setOnImageAvailableListener({ reader ->
                if (!isMotionTrackEnabled) {
                    reader.acquireLatestImage()?.close()
                    return@setOnImageAvailableListener
                }
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val result = motionDetector?.processFrame(image, currentCropRegion)
                    if (result != null) {
                        val (previewBox, _) = result
                        val screenBox = mapPreviewBoxToView(previewBox, 320, 240)
                        runOnUiThread {
                            aspectOverlay.updateFaceBoxes(listOf(screenBox))
                        }
                    }
                } finally {
                    image.close()
                }
            }, backgroundHandler)

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return

            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    startPreview()
                }
                override fun onDisconnected(camera: CameraDevice) { camera.close(); cameraDevice = null }
                override fun onError(camera: CameraDevice, error: Int) { camera.close(); cameraDevice = null }
            }, backgroundHandler)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startPreview() {
        val texture = textureView.surfaceTexture ?: return
        texture.setDefaultBufferSize(1920, 1080)
        val previewSurface = Surface(texture)
        val readerSurface = imageReader?.surface ?: return
        val motionSurface = motionImageReader?.surface ?: return

        try {
            cameraDevice?.createCaptureSession(
                listOf(previewSurface, readerSurface, motionSurface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        updatePreview()
                    }
                    override fun onConfigureFailed(session: CameraCaptureSession) {}
                }, backgroundHandler)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun applyZoom(builder: CaptureRequest.Builder) {
        sensorRect?.let { rect ->
            val centerX = rect.width() / 2
            val centerY = rect.height() / 2
            val halfWidth = (rect.width() / (2 * currentZoom)).toInt().coerceAtLeast(1)
            val halfHeight = (rect.height() / (2 * currentZoom)).toInt().coerceAtLeast(1)
            val crop = Rect(
                (centerX - halfWidth).coerceAtLeast(0),
                (centerY - halfHeight).coerceAtLeast(0),
                (centerX + halfWidth).coerceAtMost(rect.width()),
                (centerY + halfHeight).coerceAtMost(rect.height())
            )
            currentCropRegion = crop
            builder.set(CaptureRequest.SCALER_CROP_REGION, crop)
        }
    }

    private fun updatePreview() {
        val session = captureSession ?: return
        val device = cameraDevice ?: return
        val texture = textureView.surfaceTexture ?: return

        try {
            val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            builder.addTarget(Surface(texture))
            motionImageReader?.surface?.let { builder.addTarget(it) }

            if (isShutterAuto && isIsoAuto) {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, currentEv)
            } else {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, if (isShutterAuto) 30_000_000L else currentExposure)
                builder.set(CaptureRequest.SENSOR_SENSITIVITY, if (isIsoAuto) 100 else currentIso)
            }

            if (isFocusAuto) {
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            } else {
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, currentFocus)
            }

            builder.set(CaptureRequest.CONTROL_AWB_MODE, currentWbMode)

            if (isFaceDetectEnabled) {
                builder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, CaptureRequest.STATISTICS_FACE_DETECT_MODE_SIMPLE)
            } else {
                builder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF)
            }

            applyZoom(builder)

            session.setRepeatingRequest(builder.build(), captureCallback, backgroundHandler)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun takePhoto() {
        val session = captureSession ?: return
        val device = cameraDevice ?: return
        val readerSurface = imageReader?.surface ?: return

        try {
            val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            builder.addTarget(readerSurface)

            if (isShutterAuto && isIsoAuto) {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, currentEv)
            } else {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, if (isShutterAuto) 30_000_000L else currentExposure)
                builder.set(CaptureRequest.SENSOR_SENSITIVITY, if (isIsoAuto) 100 else currentIso)
            }

            if (isFocusAuto) {
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            } else {
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, currentFocus)
            }

            builder.set(CaptureRequest.CONTROL_AWB_MODE, currentWbMode)
            builder.set(CaptureRequest.JPEG_ORIENTATION, currentJpegOrientation)
            applyZoom(builder)

            session.capture(builder.build(), null, backgroundHandler)
            runOnUiThread { Toast.makeText(this, "Photo saved!", Toast.LENGTH_SHORT).show() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveImage(image: android.media.Image) {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        image.close()

        var finalBytes = bytes

        if (isFourFive) {
            try {
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bitmap != null) {
                    val width = bitmap.width
                    val height = bitmap.height
                    val targetHeight = (width * 5) / 4
                    val y = ((height - targetHeight) / 2).coerceAtLeast(0)
                    val cropped = android.graphics.Bitmap.createBitmap(bitmap, 0, y, width, targetHeight.coerceAtMost(height - y))
                    val stream = ByteArrayOutputStream()
                    cropped.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, stream)
                    finalBytes = stream.toByteArray()
                    bitmap.recycle()
                    cropped.recycle()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val filename = "MANUAL_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PocoManualCam")
                }
                contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)?.let { uri ->
                    contentResolver.openOutputStream(uri)?.use { it.write(finalBytes) }
                }
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "PocoManualCam")
                if (!dir.exists()) dir.mkdirs()
                FileOutputStream(File(dir, filename)).use { it.write(finalBytes) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun openGallery() {
        try {
            val miuiIntent = Intent(Intent.ACTION_VIEW).apply {
                setClassName("com.miui.gallery", "com.miui.gallery.activity.HomePageActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(miuiIntent)
        } catch (e: Exception) {
            try {
                startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).apply { type = "image/*" }, "Open Gallery"))
            } catch (e2: Exception) {
                Toast.makeText(this, "Cannot open gallery", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        orientationEventListener?.enable()
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        startBackgroundThread()
        if (textureView.isAvailable) openCamera()
        else {
            textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) = openCamera()
                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture) = true
                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
            }
        }
    }

    override fun onPause() {
        orientationEventListener?.disable()
        sensorManager.unregisterListener(this)
        captureSession?.close()
        cameraDevice?.close()
        imageReader?.close()
        motionImageReader?.close()
        stopBackgroundThread()
        super.onPause()
    }
}
