package com.example.kotlintestsuiteapi24

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.location.Location
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.beust.klaxon.JsonArray
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.android.synthetic.main.activity_testscenario1.*
import okio.Utf8
import org.json.JSONException
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

typealias LumaListener = (luma: Double) -> Unit

class TestScenario1Activity : AppCompatActivity() {

    // Create reference to firebase
    val db = Firebase.firestore
    var mediaPlayer: MediaPlayer? = null
    var weatherValue = ""
    var cityValue = ""
    var latitudeValue = 0.0
    var longitudeValue = 0.0

    // API
    val apiKey = "0a39e670ebc117a265e000dd2f5ef474"

    // Camera
    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null

    // GPS
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // UUID
    var uuid:UUID = UUID.fromString("f0d60c98-748e-4179-a962-d3111033c098")
    val btAdress = "94:E9:79:E4:09:50"
    // Bluetooth
    lateinit var bAdapter:BluetoothAdapter
    lateinit var btSocket:BluetoothSocket
    lateinit var device:BluetoothDevice
    lateinit var out:OutputStream


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        setContentView(R.layout.activity_testscenario1)
        // Create instance of location provider client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        //bluetooth
        bAdapter = BluetoothAdapter.getDefaultAdapter()

        // Request camera permissions
        if (allPermissionsGrantedCamera()) {
            startCamera()
            playAudioFile()
            connectBt()

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


    private fun connectBt(){
        device = bAdapter.getRemoteDevice(btAdress)

        try {
            btSocket = device.createInsecureRfcommSocketToServiceRecord(uuid)
        } catch (e: java.lang.Exception) {
            Toast.makeText(applicationContext, "Could not connect to socket", Toast.LENGTH_LONG)
        }
        bAdapter.cancelDiscovery()
        try {
            btSocket.connect()

        } catch (e: IOException) {
            Toast.makeText(applicationContext, "Could not connect to socket", Toast.LENGTH_LONG) }
        try {
            out = btSocket.outputStream
            val msg = "Hello world".toByteArray(Charsets.UTF_8)
            out.write(msg)
            Toast.makeText(applicationContext, "Message sent", Toast.LENGTH_LONG).show()
        } catch (a: java.lang.Exception) {
            Toast.makeText(applicationContext, "Could not send msg", Toast.LENGTH_LONG).show()
        }
    }

    private fun playAudioFile(){
        mediaPlayer = MediaPlayer.create(this, R.raw.bugle)
        mediaPlayer?.start()

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
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                println(location)
                // get weather data from location
                val url =
                    "https://api.openweathermap.org/data/2.5/weather?lat=${location?.latitude}&lon=${location?.longitude}&appid=${apiKey}"

                try {
                    val jsonObjectRequest = JsonObjectRequest(Request.Method.GET, url, null,

                        // On Success
                        Response.Listener {
                            val parser: Parser = Parser.default()
                            val stringBuilder: StringBuilder = StringBuilder(it.toString())
                            val json: JsonObject = parser.parse(stringBuilder) as JsonObject
                            val weatherArray =
                                json.array<JsonObject>("weather") as JsonArray<JsonObject>
                            val weather = weatherArray[0].string("main").toString()
                            val city = json.string("name").toString()
                            println(weather)
                            println(city)
                            weatherValue = weather
                            cityValue = city
                            latitudeValue = location!!.latitude
                            longitudeValue = location!!.longitude
                        },
                        Response.ErrorListener {
                            Toast.makeText(this, "something went wrong: $it", Toast.LENGTH_LONG)
                                .show()
                        })

                    queue.add(jsonObjectRequest)
                } catch (error: JSONException) {
                    println(error)
                }
                Toast.makeText(this, location.toString(), Toast.LENGTH_LONG).show()
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

                    // Upload to database
                    val photo = hashMapOf(
                        "city" to cityValue,
                        "latitude" to latitudeValue,
                        "longitude" to longitudeValue,
                        "photo" to photoFile.absolutePath,
                        "weather" to weatherValue
                    )

                    db.collection("photos")
                        .add(photo)
                        .addOnSuccessListener { documentReference ->
                            Log.d(TAG, "DocumentSnapShot added with ID: ${documentReference.id}")
                        }
                        .addOnFailureListener { e ->
                            Log.w(TAG, "Error adding document", e)
                        }
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