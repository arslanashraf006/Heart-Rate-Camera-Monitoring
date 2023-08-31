package com.example.heartratepoc

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.hardware.Camera
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import android.view.*
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NavUtils
import java.util.concurrent.atomic.AtomicBoolean


class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "HeartRateMonitor"
        private val processing = AtomicBoolean(false)
    }

    private lateinit var preview: SurfaceView
    private lateinit var previewHolder: SurfaceHolder
    private var camera: Camera? = null
    private lateinit var image: View
    private lateinit var text: TextView

    private lateinit var wakeLock: PowerManager.WakeLock

    private var averageIndex = 0
    private val averageArraySize = 4
    private val averageArray = IntArray(averageArraySize)

    enum class TYPE {
        GREEN, RED
    }

    private var currentType = TYPE.GREEN
    fun getCurrent(): TYPE {
        return currentType
    }

    private var beatsIndex = 0
    private val beatsArraySize = 3
    private val beatsArray = IntArray(beatsArraySize)
    private var beats = 0
    private var startTime: Long = 0

    var heartRate: String? = null

//    private lateinit var measuringRHRLinearLayout: LinearLayout
//    private lateinit var calculatingTHRLinearLayout: LinearLayout
//    private lateinit var cardListLinearLayout: LinearLayout
//    private var maximumHeartRate: Int = 0
//    private var heartRateReserve: Int = 0
//    private var calculatingTHR = false
//    private var rhr = 0

    @SuppressLint("InvalidWakeLockTag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setUpPermissions()
        preview = findViewById(R.id.preview)
        previewHolder = preview.holder
        previewHolder.addCallback(surfaceCallback)
        previewHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)

        image = findViewById(R.id.image)
        text = findViewById(R.id.hr_text)

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, "DoNotDimScreen")

        supportActionBar?.setDisplayHomeAsUpEnabled(false)

//        val intentThatStartedThisActivity = intent
//        if (intentThatStartedThisActivity.hasExtra("age")) {
//            measuringRHRLinearLayout = findViewById(R.id.ll_measuringRHR)
//            calculatingTHRLinearLayout = findViewById(R.id.ll_calculatingTHR)
//            cardListLinearLayout = findViewById(R.id.ll_cardList)
//
//            val age = intentThatStartedThisActivity.getStringExtra("age")?.toInt() ?: 0
//            rhr = intentThatStartedThisActivity.getStringExtra("restingHeartRate")?.toInt() ?: 0
//
//            calculateTargetHeartRate(age)
//        }
    }

//    private fun calculateTargetHeartRate(age: Int) {
//        measuringRHRLinearLayout.visibility = View.GONE
//        calculatingTHRLinearLayout.visibility = View.VISIBLE
//        calculatingTHR = true
//
//        maximumHeartRate = 220 - age
//        val MHRTextView = findViewById<TextView>(R.id.tv_MHR)
//        MHRTextView.text = maximumHeartRate.toString()
//
//        heartRateReserve = maximumHeartRate - rhr
//        val HRRTextView = findViewById<TextView>(R.id.tv_HRR)
//        HRRTextView.text = heartRateReserve.toString()
//    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                NavUtils.navigateUpFromSameTask(this)
                true
            }
            R.id.action_retry -> {
                startCam()
                true
            }
            R.id.action_ok -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }

    override fun onResume() {
        super.onResume()

        wakeLock.acquire()

        camera = Camera.open()

        startTime = System.currentTimeMillis()
    }

    override fun onPause() {
        super.onPause()

        wakeLock.release()

        camera?.apply {
            setPreviewCallback(null)
            stopPreview()
            release()
        }
        camera = null
    }

    private val previewCallback = Camera.PreviewCallback { data, cam ->
        cam.setDisplayOrientation(90)
        data?.let {
            val size = cam.parameters.previewSize
            val fingerText : TextView = findViewById(R.id.finger)
            size?.let {

                val width = size.width
                val height = size.height

                // Clone the data array for processing
                val yuvData = data.clone()

                // Calculate the index for a specific pixel (for example, row 0 and column 0)
                val row = 0
                val col = 0
                val index = row * width + col

                // Extract Y, U, and V components from YUV420 semi-planar data
                val y = yuvData[index].toInt() and 0xFF
                val u = yuvData[width * height + index / 2].toInt() and 0xFF
                val v = yuvData[width * height + width * height / 4 + index / 2].toInt() and 0xFF

                val rgbColor = yuvToRgb(y, u, v)

                val redThreshold = 150

                // Check if the pixel is predominantly red
                val isRed = Color.red(rgbColor) > redThreshold

                println("rgb --------------> $isRed")
                println("rgb red --------------> ${Color.red(rgbColor)}")

                if (isRed){
                    fingerText.visibility = View.GONE
                    if (!processing.compareAndSet(false, true)) return@PreviewCallback
                    val heightValue = size.height
                    val widthValue = size.width
                    val imgAvg = ImageProcessing.decodeYUV420SPtoRedAvg(data.clone(), heightValue, widthValue)
                    if (imgAvg == 0 || imgAvg == 255) {
                        processing.set(false)
                        return@PreviewCallback
                    }

                    var averageArrayAvg = 0
                    var averageArrayCnt = 0
                    for (i in averageArray.indices) {
                        if (averageArray[i] > 0) {
                            averageArrayAvg += averageArray[i]
                            averageArrayCnt++
                        }
                    }

                    val rollingAverage = if (averageArrayCnt > 0) averageArrayAvg / averageArrayCnt else 0
                    var newType = currentType
                    if (imgAvg < rollingAverage) {
                        newType = TYPE.RED
                        if (newType != currentType) {
                            beats++
                        }
                    } else if (imgAvg > rollingAverage) {
                        newType = TYPE.GREEN
                    }

                    if (averageIndex == averageArraySize) averageIndex = 0
                    averageArray[averageIndex] = imgAvg
                    averageIndex++

                    if (newType != currentType) {
                        currentType = newType
                        image.postInvalidate()
                    }

                    val endTime = System.currentTimeMillis()
                    val totalTimeInSecs = (endTime - startTime) / 1000.0
                    if (totalTimeInSecs >= 10) {
                        val bps = beats / totalTimeInSecs
                        val dpm = (bps * 60.0).toInt()
                        if (dpm < 30 || dpm > 180) {
                            startTime = System.currentTimeMillis()
                            beats = 0
                            processing.set(false)
                            return@PreviewCallback
                        }

                        if (beatsIndex == beatsArraySize) beatsIndex = 0
                        beatsArray[beatsIndex] = dpm
                        beatsIndex++

                        var beatsArrayAvg = 0
                        var beatsArrayCnt = 0
                        for (i in beatsArray.indices) {
                            if (beatsArray[i] > 0) {
                                beatsArrayAvg += beatsArray[i]
                                beatsArrayCnt++
                            }
                        }
                        val beatsAvg = beatsArrayAvg / beatsArrayCnt
                        heartRate = beatsAvg.toString()
                        text.text = heartRate
                        startTime = System.currentTimeMillis()
                        beats = 0
                        stopCam()
                    }
                }else{
                    fingerText.visibility = View.VISIBLE
                }
                processing.set(false)
            }
        }
    }

    private fun stopCam() {
        camera?.stopPreview()
//        if (calculatingTHR) {
//            setCard()
//        }
    }

//    private fun setCard() {
//        var index = 0
//        val workoutHeartRate = heartRate?.toInt() ?: 0
//
//        var i = 0.6
//        while (i <= 1.0) {
//            if (workoutHeartRate > maximumHeartRate * i) {
//                index++
//            } else {
//                break
//            }
//            i += 0.1
//        }
//
//        for (i in 0 until 5) {
//            val card = cardListLinearLayout.getChildAt(i) as TextView
//            card.setBackgroundColor(Color.parseColor("white"))
//            card.setTextColor(Color.parseColor("red"))
//        }
//
//        val card = cardListLinearLayout.getChildAt(index) as TextView
//        card.setBackgroundColor(Color.parseColor("red"))
//        card.setTextColor(Color.parseColor("white"))
//    }

    fun yuvToRgb(y: Int, u: Int, v: Int): Int {
        val c = y - 16
        val d = u - 128
        val e = v - 128

        val r = (298 * c + 409 * e + 128) shr 8
        val g = (298 * c - 100 * d - 208 * e + 128) shr 8
        val b = (298 * c + 516 * d + 128) shr 8

        return android.graphics.Color.rgb(
            r.coerceIn(0, 255),
            g.coerceIn(0, 255),
            b.coerceIn(0, 255)
        )
    }

    private fun startCam() {
        camera?.startPreview()
        startTime = System.currentTimeMillis()
    }

    override fun finish() {
        val data = Intent()
        data.putExtra("restingHeartRate", heartRate)
        setResult(RESULT_OK, data)
        super.finish()
    }

    private val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            try {
                camera?.apply {
                    setPreviewDisplay(previewHolder)
                    setPreviewCallback(previewCallback)
                }
            } catch (t: Throwable) {
                Log.e("surfaceCallbackDemo", "Exception in setPreviewDisplay()", t)
            }
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            camera?.apply {
                val parameters = parameters
                parameters.flashMode = Camera.Parameters.FLASH_MODE_TORCH
                val size = getSmallestPreviewSize(width, height, parameters)
                size?.let {
                    parameters.setPreviewSize(size.width, size.height)
                    Log.d(TAG, "Using width=${size.width} height=${size.height}")
                }
                setParameters(parameters)
                startPreview()
            }
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            // Ignore
        }
    }

    private fun getSmallestPreviewSize(width: Int, height: Int, parameters: Camera.Parameters): Camera.Size? {
        var result: Camera.Size? = null

        for (size in parameters.supportedPreviewSizes) {
            if (size.width <= width && size.height <= height) {
                if (result == null) {
                    result = size
                } else {
                    val resultArea = result.width * result.height
                    val newArea = size.width * size.height

                    if (newArea < resultArea) result = size
                }
            }
        }

        return result
    }

    private fun setUpPermissions() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) !== PackageManager.PERMISSION_GRANTED
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val permissionsWeNeed = arrayOf(Manifest.permission.CAMERA)
                requestPermissions(permissionsWeNeed, 88)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            88 -> {

                // If request is cancelled, the result arrays are empty.
                if (grantResults.size > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED
                ) {
                    // The permission was granted!
                    //set up whatever required the permissions
                } else {
                    Toast.makeText(
                        this,
                        "Permission for camera not granted. HeartBeat Monitor can't run.",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                    // The permission was denied, so we can show a message why we can't run the app
                    // and then close the app.
                }
            }
        }
    }
}
