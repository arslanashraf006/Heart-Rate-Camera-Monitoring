package com.example.heartratepoc

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class Home : AppCompatActivity() {
    var ageEditText: EditText? = null
    var rhrLinearLayout: LinearLayout? = null
    var rhrTextView: TextView? = null
    var thrLinearLayout: LinearLayout? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        setUpPermissions()
        ageEditText = findViewById(R.id.et_age)
        rhrLinearLayout = findViewById(R.id.ll_restingHeartRate)
        rhrTextView = findViewById(R.id.tv_restingHeartRate)
        thrLinearLayout = findViewById(R.id.ll_targetHeartRate)
        if (savedInstanceState != null) {
            ageEditText!!.setText(savedInstanceState.getString("storedAge"))
            rhrTextView!!.text = savedInstanceState.getString("storedRHR")
        }
        rhrLinearLayout!!.setOnClickListener { restingHeartRate }
        thrLinearLayout!!.setOnClickListener { calculateTargetHeartRate() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val currentAge = ageEditText!!.text.toString()
        outState.putString("storedAge", currentAge)
        val currentRHR = rhrTextView!!.text.toString()
        outState.putString("storedRHR", currentRHR)
    }

    private fun calculateTargetHeartRate() {
        val age = ageEditText!!.text.toString()
        if (age == "") {
            Toast.makeText(this, "Please enter your age!", Toast.LENGTH_SHORT).show()
            return
        }
        val restingHeartRate = rhrTextView!!.text.toString()
        if (restingHeartRate == "") {
            Toast.makeText(this, "Please calculate your RHR first!", Toast.LENGTH_SHORT).show()
            return
        }
        val intentToCalculateTHR = Intent(this@Home, MainActivity::class.java)
        intentToCalculateTHR.putExtra("age", age)
        intentToCalculateTHR.putExtra("restingHeartRate", restingHeartRate)
        startActivity(intentToCalculateTHR)
    }

    private val restingHeartRate: Unit
        get() {
            val intentToGetRHR = Intent(this@Home, MainActivity::class.java)
            startActivityForResult(intentToGetRHR, 1)
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == RESULT_OK) {
            val restingHeartRate = data!!.getStringExtra("restingHeartRate")
            rhrTextView!!.text = restingHeartRate
        }
    }
}