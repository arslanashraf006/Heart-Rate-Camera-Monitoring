package com.example.heartratepoc

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.sql.Timestamp
import java.util.*
import kotlin.collections.HashMap

class CameraActivity : AppCompatActivity() {

    private lateinit var textureView: TextureView
    private var cameraId: String? = null
    private var cameraDevice: CameraDevice? = null
    private lateinit var cameraCaptureSessions: CameraCaptureSession
    private lateinit var captureRequestBuilder: CaptureRequest.Builder
    private lateinit var imageDimension: Size
    private val REQUEST_CAMERA_PERMISSION = 1

    // Thread handler member variables
    private var mBackgroundHandler: Handler? = null
    private var mBackgroundThread: HandlerThread? = null

    // Heart rate detector member variables
    var hrtratebpm: Int = 0
    private var mCurrentRollingAverage: Int = 0
    private var mLastRollingAverage: Int = 0
    private var mLastLastRollingAverage: Int = 0
    private lateinit var mTimeArray: LongArray
    private var numCaptures: Int = 0
    private var mNumBeats: Int = 0
    private lateinit var tv: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        textureView = findViewById(R.id.texture)
        textureView.surfaceTextureListener = textureListener
        mTimeArray = LongArray(15)
        tv = findViewById(R.id.neechewalatext)
    }

    private val textureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            openCamera()
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            return false
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
            Log.d("TAG", "onSurfaceTextureUpdated")
            val bmp: Bitmap = textureView.bitmap!!
            val width: Int = bmp.width
            val height: Int = bmp.height
            val pixels = IntArray(height * width)
            bmp.getPixels(pixels, 0, width, width / 2, height / 2, width / 20, height / 20)
            var sum = 0
            for (i in pixels.indices) {
                val red = (pixels[i] shr 16) and 0xFF
                sum += red
            }
            if (numCaptures == 20) {
                mCurrentRollingAverage = sum
            } else if (numCaptures in 21..48) {
                mCurrentRollingAverage = (mCurrentRollingAverage * (numCaptures - 20) + sum) / (numCaptures - 19)
            } else if (numCaptures >= 49) {
                mCurrentRollingAverage = (mCurrentRollingAverage * 29 + sum) / 30
                if (mLastRollingAverage > mCurrentRollingAverage && mLastRollingAverage > mLastLastRollingAverage && mNumBeats < 15) {
                    mTimeArray[mNumBeats] = System.currentTimeMillis()
                    mNumBeats++
                    if (mNumBeats == 15) {
                        calcBPM()
                    }
                }
            }
            numCaptures++
            mLastLastRollingAverage = mLastRollingAverage
            mLastRollingAverage = mCurrentRollingAverage
        }
    }

    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            Log.e("TAG", "onOpened")
            cameraDevice = camera
            createCameraPreview()
        }

        override fun onDisconnected(camera: CameraDevice) {
            cameraDevice?.close()
            cameraDevice = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
            cameraDevice?.close()
            cameraDevice = null
        }
    }

    private fun startBackgroundThread() {
        mBackgroundThread = HandlerThread("Camera Background")
        mBackgroundThread?.start()
        mBackgroundHandler = Handler(mBackgroundThread?.looper!!)
    }

    private fun stopBackgroundThread() {
        mBackgroundThread?.quitSafely()
        try {
            mBackgroundThread?.join()
            mBackgroundThread = null
            mBackgroundHandler = null
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    private fun calcBPM() {
        var med: Int
        val timedist = LongArray(14)
        for (i in 0 until 14) {
            timedist[i] = mTimeArray[i + 1] - mTimeArray[i]
        }
        timedist.sort()
        med = timedist[timedist.size / 2].toInt()
        hrtratebpm = 60000 / med
        addTodb()
    }

    private fun addTodb() {
        val tv: TextView = findViewById(R.id.neechewalatext)
        tv.text = "Heart Rate = $hrtratebpm BPM"
    }

    private fun createCameraPreview() {
        try {
            val texture = textureView.surfaceTexture
            texture?.setDefaultBufferSize(imageDimension.width, imageDimension.height)
            val surface = Surface(texture)
            captureRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)!!
            captureRequestBuilder.addTarget(surface)
            cameraDevice?.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                        if (null == cameraDevice) {
                            return
                        }
                        cameraCaptureSessions = cameraCaptureSession
                        updatePreview()
                    }

                    override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                        Toast.makeText(this@CameraActivity, "Configuration change", Toast.LENGTH_SHORT).show()
                    }
                }, null
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun openCamera() {
        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        Log.e("TAG", "is camera open")
        try {
            cameraId = manager.cameraIdList[0]
            val characteristics = manager.getCameraCharacteristics(cameraId!!)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            imageDimension = map!!.getOutputSizes(SurfaceTexture::class.java)[0]
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this@CameraActivity,
                    arrayOf(Manifest.permission.CAMERA),
                    REQUEST_CAMERA_PERMISSION
                )
                return
            }
            manager.openCamera(cameraId!!, stateCallback, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
        Log.e("TAG", "openCamera X")
    }

    protected fun updatePreview() {
        if (null == cameraDevice) {
            Log.e("TAG", "updatePreview error, return")
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
        captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH)
        try {
            cameraCaptureSessions.setRepeatingRequest(
                captureRequestBuilder.build(),
                null,
                mBackgroundHandler
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun closeCamera() {
        if (cameraDevice == null) {
            cameraDevice?.close()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                // close the app
                Toast.makeText(
                    this@CameraActivity,
                    "Sorry!!!, you can't use this app without granting permission",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.e("TAG", "onResume")
        startBackgroundThread()
        if (textureView.isAvailable) {
            openCamera()
        } else {
            textureView.surfaceTextureListener = textureListener
        }
    }

    override fun onPause() {
        Log.e("TAG", "onPause")
        closeCamera()
        stopBackgroundThread()
        super.onPause()
    }
}

class HeartRate {
    val timestamp = Timestamp(System.currentTimeMillis())
    val heartrate = CameraActivity().hrtratebpm
}
