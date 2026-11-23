package com.jonardca.pocomanualcam

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Log
import android.util.Range
import android.view.Surface
import android.view.TextureView
import android.widget.Button
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

    private lateinit var isoLabel: TextView
    private lateinit var shutterLabel: TextView
    private lateinit var focusLabel: TextView
    private lateinit var wbLabel: TextView
    private lateinit var evLabel: TextView

    private lateinit var captureButton: Button
    private lateinit var galleryButton: Button

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var backgroundHandler: Handler? = null
    private var backgroundThread: HandlerThread? = null
    private var imageReader: ImageReader? = null

    private var isoRange: Range<Int>? = null
    private var exposureRange: Range<Long>? = null
    private var focusRange: Range<Float>? = null
    private var evRange: Range<Int>? = null

    private var currentIso = 100
    private var currentExposure = 30_000_000L
    private var currentFocus = 0f
    private var currentWb = CaptureRequest.CONTROL_AWB_MODE_AUTO
    private var currentEv = 0

    private val wbModes = arrayOf(
        CaptureRequest.CONTROL_AWB_MODE_AUTO,
        CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT,
        CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT,
        CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT,
        CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT,
        CaptureRequest.CONTROL_AWB_MODE_TWILIGHT,
        CaptureRequest.CONTROL_AWB_MODE_SHADE
    )

    private val wbNames = arrayOf(
        "Auto", "Daylight", "Cloudy", "Incandescent", "Fluorescent", "Twilight", "Shade"
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

        isoLabel = findViewById(R.id.isoLabel)
        shutterLabel = findViewById(R.id.shutterLabel)
        focusLabel = findViewById(R.id.focusLabel)
        wbLabel = findViewById(R.id.wbLabel)
        evLabel = findViewById(R.id.evLabel)

        captureButton = findViewById(R.id.captureButton)
        galleryButton = findViewById(R.id.galleryButton)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 100)
        }

        setupSeekBars()

        captureButton.setOnClickListener { takePhoto() }
        galleryButton.setOnClickListener { openGallery() }
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
                    val text = if (currentExposure >= 1_000_000_000L) {
                        String.format("%.1fs", currentExposure / 1_000_000_000.0)
                    } else {
                        "1/${(1_000_000_000.0 / currentExposure).toInt()}"
                    }
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
                // Map 0-48 to -24 to +24 (common range)
                currentEv = progress - 24
                evLabel.text = "EV: ${if (currentEv > 0) "+$currentEv" else currentEv}"
                updatePreview()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Default EV center
        evSeekBar.progress = 24
    }

    private fun openCamera() {
        try {
            val cameraId = cameraManager.cameraIdList[0]
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)

            isoRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
            exposureRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
            val minFocus = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
            focusRange = Range(0f, minFocus)
            evRange = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)

            imageReader = ImageReader.newInstance(1920, 1080, ImageFormat.JPEG, 2)
            imageReader?.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                image?.let { saveImage(it) }
            }, backgroundHandler)

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) return

            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    startPreview()
                }
                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                }
                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                    Log.e("Camera", "Open error: $error")
                }
            }, backgroundHandler)

        } catch (e: Exception) {
            e.printStackTrace()
            runOnUiThread {
                Toast.makeText(this, "Camera error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startPreview() {
        val texture = textureView.surfaceTexture ?: return
        texture.setDefaultBufferSize(1920, 1080)
        val previewSurface = Surface(texture)
        val readerSurface = imageReader?.surface ?: return

        try {
            val surfaces = listOf(previewSurface, readerSurface)

            cameraDevice?.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession
