package com.jonardca.pocomanualcam

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Range
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
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var textureView: TextureView
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
    private lateinit var seekBarsContainer: LinearLayout
    private lateinit var toggleControls: TextView

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var backgroundHandler: Handler? = null
    private var backgroundThread: HandlerThread? = null
    private var imageReader: ImageReader? = null

    private var isoRange: Range<Int>? = null
    private var exposureRange: Range<Long>? = null
    private var focusRange: Range<Float>? = null
    private var sensorRect: Rect? = null

    private var currentIso = 100
    private var currentExposure = 30_000_000L
    private var currentFocus = 0f
    private var currentWbMode = CaptureRequest.CONTROL_AWB_MODE_AUTO
    private var currentEv = 0
    private var currentZoom = 1.0f
    private var controlsVisible = true
    private var isShutterAuto = true
    private var isWbAuto = true

    // ========== DISCRETE SHUTTER SPEEDS ==========
    // Index 0 = Auto
    private val shutterSpeeds = arrayOf(
        -1L, // Auto
        250_000L,      // 1/4000
        312_500L,      // 1/3200
        400_000L,      // 1/2500
        500_000L,      // 1/2000
        625_000L,      // 1/1600
        800_000L,      // 1/1250
        1_000_000L,    // 1/1000
        1_250_000L,    // 1/800
        1_562_500L,    // 1/640
        2_000_000L,    // 1/500
        2_500_000L,    // 1/400
        3_125_000L,    // 1/320
        4_000_000L,    // 1/250
        5_000_000L,    // 1/200
        6_250_000L,    // 1/160
        8_000_000L,    // 1/125
        10_000_000L,   // 1/100
        12_500_000L,   // 1/80
        16_666_667L,   // 1/60
        20_000_000L,   // 1/50
        25_000_000L,   // 1/40
        33_333_333L,   // 1/30
        40_000_000L,   // 1/25
        50_000_000L,   // 1/20
        66_666_667L,   // 1/15
        76_923_077L,   // 1/13
        100_000_000L,  // 1/10
        125_000_000L,  // 1/8
        166_666_667L,  // 1/6
        200_000_000L,  // 1/5
        250_000_000L,  // 1/4
        300_000_000L,  // 0.3s
        400_000_000L,  // 0.4s
        500_000_000L,  // 0.5s
        600_000_000L,  // 0.6s
        800_000_000L,  // 0.8s
        900_000_000L,  // 0.9s
        1_000_000_000L, // 1s
        1_300_000_000L, // 1.3s
        1_600_000_000L, // 1.6s
        1_800_000_000L, // 1.8s
        2_000_000_000L, // 2s
        2_300_000_000L, // 2.3s
        2_500_000_000L, // 2.5s
        2_800_000_000L, // 2.8s
        3_000_000_000L, // 3s
        3_200_000_000L, // 3.2s
        3_600_000_000L, // 3.6s
        3_800_000_000L, // 3.8s
        4_000_000_000L, // 4s
        5_000_000_000L, // 5s
        6_000_000_000L, // 6s
        8_000_000_000L, // 8s
        10_000_000_000L, // 10s
        13_000_000_000L, // 13s
        15_000_000_000L, // 15s
        20_000_000_000L, // 20s
        25_000_000_000L, // 25s
        30_000_000_000L  // 30s
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

    // ========== WHITE BALANCE (Kelvin) ==========
    // Index 0 = Auto
    private val kelvinValues = intArrayOf(
        0, // Auto
        2000, 2200, 2400, 2600, 2800, 3000, 3200, 3400, 3600, 3800,
        4000, 4200, 4400, 4600, 4800, 5000, 5200, 5400, 5600, 5800,
        6000, 6200, 6400, 6600, 6800, 7000, 7200, 7400, 7600, 7800, 8000
    )

    private val cameraManager by lazy { getSystemService(CAMERA_SERVICE) as CameraManager }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textureView = findViewById(R.id.textureView)
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
        seekBarsContainer = findViewById(R.id.seekBarsContainer)
        toggleControls = findViewById(R.id.toggleControls)

        // Set correct max values
        shutterSeekBar.max = shutterSpeeds.size - 1
        wbSeekBar.max = kelvinValues.size - 1

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 100)
        }

        setupSeekBars()

        captureButton.setOnClickListener { takePhoto() }
        galleryButton.setOnClickListener { openGallery() }

        toggleControls.setOnClickListener {
            controlsVisible = !controlsVisible
            if (controlsVisible) {
                seekBarsContainer.visibility = View.VISIBLE
                toggleControls.text = "▲ Hide Controls"
            } else {
                seekBarsContainer.visibility = View.GONE
                toggleControls.text = "▼ Show Controls"
            }
        }
    }

    private fun setupSeekBars() {
        isoSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                isoRange?.let {
                    currentIso = it.lower + (progress * (it.upper - it.lower) / 100)
                    isoLabel.text = "ISO: $currentIso"
                    updatePreview()
                }
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
                focusRange?.let {
                    currentFocus = it.lower + (progress * (it.upper - it.lower) / 100f)
                    focusLabel.text = "Focus: ${"%.2f".format(currentFocus)}"
                    updatePreview()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        wbSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val index = progress.coerceIn(0, kelvinValues.size - 1)
                if (index == 0) {
                    isWbAuto = true
                    currentWbMode = CaptureRequest.CONTROL_AWB_MODE_AUTO
                    wbLabel.text = "WB: Auto"
                } else {
                    isWbAuto = false
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
        evSeekBar.progress = 24

        zoomSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentZoom = 1.0f + (progress / 100f) * 3.0f
                zoomLabel.text = "Zoom: ${"%.1f".format(currentZoom)}x"
                updatePreview()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    // Map Kelvin to closest standard AWB mode (most reliable across devices)
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

    private fun openCamera() {
        try {
            val cameraId = cameraManager.cameraIdList[0]
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)

            isoRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
            exposureRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
            val minFocus = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
            focusRange = Range(0f, minFocus)
            sensorRect = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)

            imageReader = ImageReader.newInstance(3472, 4624, ImageFormat.JPEG, 2)
            imageReader?.setOnImageAvailableListener({ reader ->
                reader.acquireLatestImage()?.let { saveImage(it) }
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

        try {
            cameraDevice?.createCaptureSession(listOf(previewSurface, readerSurface),
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

            if (isShutterAuto) {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            } else {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, currentExposure)
                builder.set(CaptureRequest.SENSOR_SENSITIVITY, currentIso)
            }

            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, currentFocus)

            builder.set(CaptureRequest.CONTROL_AWB_MODE, currentWbMode)
            builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, currentEv)

            applyZoom(builder)

            session.setRepeatingRequest(builder.build(), null, backgroundHandler)
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

            if (isShutterAuto) {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            } else {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, currentExposure)
                builder.set(CaptureRequest.SENSOR_SENSITIVITY, currentIso)
            }

            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, currentFocus)
            builder.set(CaptureRequest.CONTROL_AWB_MODE, currentWbMode)
            builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, currentEv)
            builder.set(CaptureRequest.JPEG_ORIENTATION, 90)

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

        val filename = "MANUAL_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PocoManualCam")
                }
                contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)?.let { uri ->
                    contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                }
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "PocoManualCam")
                if (!dir.exists()) dir.mkdirs()
                FileOutputStream(File(dir, filename)).use { it.write(bytes) }
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
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    type = "image/*"
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(Intent.createChooser(intent, "Open Gallery"))
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
        captureSession?.close()
        cameraDevice?.close()
        imageReader?.close()
        stopBackgroundThread()
        super.onPause()
    }
}
