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
    private var currentWb = CaptureRequest.CONTROL_AWB_MODE_AUTO
    private var currentEv = 0
    private var currentZoom = 1.0f
    private var controlsVisible = true

    private val wbModes = arrayOf(
        CaptureRequest.CONTROL_AWB_MODE_AUTO,
        CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT,
        CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT,
        CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT,
        CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT,
        CaptureRequest.CONTROL_AWB_MODE_TWILIGHT,
        CaptureRequest.CONTROL_AWB_MODE_SHADE
    )

    private val wbNames = arrayOf("Auto", "Daylight", "Cloudy", "Incandescent", "Fluorescent", "Twilight", "Shade")

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
                exposureRange?.let {
                    val min = it.lower
                    val max = minOf(it.upper, 1_000_000_000L)
                    currentExposure = min + (progress * (max - min) / 100)
                    val text = if (currentExposure >= 1_000_000_000L) String.format("%.1fs", currentExposure / 1_000_000_000.0)
                               else "1/${(1_000_000_000.0 / currentExposure).toInt()}"
                    shutterLabel.text = "Shutter: $text"
                    updatePreview()
                }
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
                val index = progress.coerceIn(0, wbModes.size - 1)
                currentWb = wbModes[index]
                wbLabel.text = "WB: ${wbNames[index]}"
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

            builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_OFF)
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            builder.set(CaptureRequest.CONTROL_AWB_MODE, currentWb)
            builder.set(CaptureRequest.SENSOR_SENSITIVITY, currentIso)
            builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, currentExposure)
            builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, currentFocus)
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

            builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_OFF)
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            builder.set(CaptureRequest.CONTROL_AWB_MODE, currentWb)
            builder.set(CaptureRequest.SENSOR_SENSITIVITY, currentIso)
            builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, currentExposure)
            builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, currentFocus)
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
