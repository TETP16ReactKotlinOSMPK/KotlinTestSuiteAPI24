package com.example.kotlintestsuiteapi24

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.Manifest
import android.app.DownloadManager
import android.content.pm.PackageManager
import android.graphics.drawable.BitmapDrawable
import android.location.Location
import android.net.Uri
import android.provider.MediaStore
import android.provider.MediaStore.Images.Media.insertImage
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import androidx.camera.core.*
import androidx.camera.core.ImageCapture
import androidx.camera.lifecycle.ProcessCameraProvider
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.android.volley.toolbox.JsonObjectRequest
import com.beust.klaxon.JsonArray
import com.beust.klaxon.Parser
import com.beust.klaxon.JsonObject
import com.example.kotlintestsuiteapi24.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.android.synthetic.main.activity_testscenario1.*
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.lang.StringBuilder
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService

typealias LumaListener = (luma: Double) -> Unit

class TestScenario1Activity : AppCompatActivity() {
    private var imageCapture: ImageCapture? = null

    // API
    val apiKey = "0a39e670ebc117a265e000dd2f5ef474"

    // Camera
    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService

    // GPS
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_testscenario1)

        // Create instance of location provider client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Request camera permissions
        if (allPermissionsGrantedCamera()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS_CAMERA, REQUEST_CODE_PERMISSIONS
            )
        }

        // Set up the listener for take photo button
        camera_capture_button.setOnClickListener { takePhoto() }

        outputDirectory = getOutputDirectory()

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGrantedCamera()) {
                startCamera()
            } else {
                Toast.makeText(
                    this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    private fun takePhoto() {

        val queue = Volley.newRequestQueue(this)

        //Request location permissions
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS_LOCATION, REQUEST_CODE_PERMISSIONS
            )
            return
        }
        // if permitted, get location
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                println(location)
                // get weather data from location
                val url = "https://api.openweathermap.org/data/2.5/weather?lat=${location?.latitude}&lon=${location?.longitude}&appid=${apiKey}"

                try {
                    val jsonObjectRequest = JsonObjectRequest(Request.Method.GET, url, null,

                        // On Success
                        Response.Listener {
                            val parser: Parser = Parser.default()
                            val stringBuilder: StringBuilder = StringBuilder(it.toString())
                            val json: JsonObject = parser.parse(stringBuilder) as JsonObject
                            val weatherArray = json.array<JsonObject>("weather") as JsonArray<JsonObject>
                            val weather = weatherArray[0].string("main").toString()
                            val city = json.string("name").toString()
                            println(weather)
                            println(city)
                        },
                        Response.ErrorListener {
                            Toast.makeText(this, "something went wrong: $it", Toast.LENGTH_LONG).show()
                        })

                    queue.add(jsonObjectRequest)
                } catch (error: JSONException){
                    println(error)
                }

            }

        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Create time-stamped output file to hold the image
        val photoFile = File(
            outputDirectory,
            SimpleDateFormat(
                FILENAME_FORMAT, Locale.US
            ).format(System.currentTimeMillis()) + ".jpg"
        )

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    val msg = "Photo capture succeeded: $savedUri"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                    // Upload to database and get weather data here


                }
            })
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.createSurfaceProvider())
                }

            imageCapture = ImageCapture.Builder()
                .build()

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))

    }

    private fun allPermissionsGrantedCamera() = REQUIRED_PERMISSIONS_CAMERA.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun allPermissionsGrantedLocation() = REQUIRED_PERMISSIONS_LOCATION.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED

    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXBasic"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS_CAMERA = arrayOf(Manifest.permission.CAMERA)
        private val REQUIRED_PERMISSIONS_LOCATION = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

}