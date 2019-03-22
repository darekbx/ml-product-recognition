package com.productrecognition

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.TextureView
import android.widget.Toast
import com.dotpad2.utils.PermissionsHelper
import kotlinx.android.synthetic.main.activity_main.*
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import java.lang.Exception


class MainActivity : AppCompatActivity() {

    val permissionsHelper by lazy { PermissionsHelper() }
    var surfaceTexture: SurfaceTexture? = null
    var camera: CameraDevice? = null
    var requestBuilder: CaptureRequest.Builder? = null
    var captureSession: CameraCaptureSession? = null

    val backgroundThread = HandlerThread("camera_background_thread").apply { start() }
    val backgroundHandler = Handler(backgroundThread.getLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (texture_view.isAvailable) {
            initCamera()
            this@MainActivity.surfaceTexture = texture_view.surfaceTexture
        } else {
            texture_view.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {}

                override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {}

                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?) = false

                override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
                    this@MainActivity.surfaceTexture = surface
                    initCamera()
                }
            }
        }

        imageReader.setOnImageAvailableListener({ image ->

            try {
                val image = image.acquireNextImage()

                val buffer = image.getPlanes()[0].getBuffer()
                val bytes = ByteArray(buffer.capacity())
                buffer.get(bytes)

                val bitmapImage = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, null)

                image_view.post {
                    image_view.setImageBitmap(bitmapImage)
                }

                imageReader.close()

            } catch (e: Exception) {
                e.printStackTrace()
            }

        }, backgroundHandler)
    }

    override fun onDestroy() {
        super.onDestroy()
        captureSession?.close()
        camera?.close()
        imageReader?.close()
        backgroundThread?.quitSafely()
    }

    private fun initCamera() {
        if (permissionsHelper.checkAllPermissionsGranted(this@MainActivity)) {
            openCamera()
        }
    }

    override fun onResume() {
        super.onResume()
        handlePermissions()
    }

    @SuppressLint("MissingPermission")
    private fun openCamera() {
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = cameraManager.cameraIdList.firstOrNull {
            val characteristics = cameraManager.getCameraCharacteristics(it)
            val direction = characteristics.get(CameraCharacteristics.LENS_FACING)
            direction == CameraCharacteristics.LENS_FACING_BACK
        }

        cameraId?.let { cameraId ->
            val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)
            val streamConfigurationMap = cameraCharacteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
            )

            val previewSize = streamConfigurationMap.getOutputSizes(SurfaceTexture::class.java)[0]
            this@MainActivity.surfaceTexture?.setDefaultBufferSize(previewSize.width, previewSize.height)

            cameraManager.openCamera(cameraId, cameraStateCallback, backgroundHandler)
        }
    }

    private val cameraStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {

            this@MainActivity.camera = camera

            val surface = Surface(surfaceTexture)
            requestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                .apply {
                    addTarget(surface)
                }

            camera.createCaptureSession(mutableListOf(surface), cameraCaptureCallback, backgroundHandler)
        }

        override fun onDisconnected(camera: CameraDevice) {
            camera.close()
        }

        override fun onError(camera: CameraDevice, error: Int) {
            camera.close()
        }
    }

    private val cameraCaptureCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigureFailed(session: CameraCaptureSession) {

        }

        override fun onConfigured(session: CameraCaptureSession) {
            captureSession = session
            val request = requestBuilder?.build()
            session.setRepeatingRequest(request, null, backgroundHandler)
        }
    }

    val imageReader by lazy {
        return@lazy ImageReader.newInstance(300, 300, ImageFormat.JPEG, 10)
    }

    private fun handlePermissions() {
        val hasPermissions = permissionsHelper.checkAllPermissionsGranted(this)
        if (!hasPermissions) {
            permissionsHelper.requestPermissions(this)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PermissionsHelper.PERMISSIONS_REQUEST_CODE) {
            val anyDenied = grantResults.any { it == PackageManager.PERMISSION_DENIED }
            if (anyDenied) {
                Toast.makeText(this, R.string.permissions_problem, Toast.LENGTH_SHORT).show()
            }
        }
    }
}