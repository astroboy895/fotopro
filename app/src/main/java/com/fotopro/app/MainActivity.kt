package com.fotopro.app

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.provider.MediaStore
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

class MainActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var statusText: TextView
    private lateinit var resultText: TextView
    private lateinit var photoButton: Button
    private lateinit var videoButton: Button
    private lateinit var switchCameraButton: ImageButton
    private lateinit var cameraExecutor: ExecutorService

    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.CAMERA] == true) startCamera()
        else statusText.text = "Se necesita permiso de cámara para usar FOTOPRO."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mantiene toda la interfaz dentro del área segura, por encima de la navegación Android.
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = Color.rgb(8, 13, 18)
        window.navigationBarColor = Color.rgb(8, 13, 18)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = false
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)

        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        statusText = findViewById(R.id.statusText)
        resultText = findViewById(R.id.resultText)
        photoButton = findViewById(R.id.photoButton)
        videoButton = findViewById(R.id.videoButton)
        switchCameraButton = findViewById(R.id.switchCameraButton)

        cameraExecutor = Executors.newSingleThreadExecutor()
        photoButton.setOnClickListener { takePhoto() }
        videoButton.setOnClickListener { toggleVideo() }
        switchCameraButton.setOnClickListener { switchCamera() }

        val needed = mutableListOf(Manifest.permission.CAMERA)
        if (android.os.Build.VERSION.SDK_INT <= 32) needed.add(Manifest.permission.RECORD_AUDIO)

        if (needed.all {
                ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            }) {
            startCamera()
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            cameraProvider = provider

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()

            val recorder = Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.fromOrderedList(
                        listOf(Quality.FHD, Quality.HD, Quality.SD), Quality.HD
                    )
                ).build()
            videoCapture = VideoCapture.withOutput(recorder)

            if (!provider.hasCamera(cameraSelector)) {
                cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            }
            bindSelectedCamera()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindSelectedCamera() {
        val provider = cameraProvider ?: return
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        try {
            provider.unbindAll()
            provider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageCapture,
                videoCapture
            )
            statusText.text = if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
                "FOTOPRO activo · cámara selfie"
            } else {
                "FOTOPRO activo · cámara principal"
            }
        } catch (e: Exception) {
            statusText.text = "No se pudo iniciar la cámara: ${e.localizedMessage}"
        }
    }

    private fun switchCamera() {
        if (recording != null) {
            statusText.text = "Detén el video antes de cambiar de cámara."
            return
        }
        val provider = cameraProvider ?: return
        val target = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        try {
            if (!provider.hasCamera(target)) {
                statusText.text = "Esta cámara no está disponible en el dispositivo."
                return
            }
            cameraSelector = target
            bindSelectedCamera()
        } catch (e: Exception) {
            statusText.text = "No se pudo cambiar la cámara."
        }
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return
        val name = "FOTOPRO_${stamp()}"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "${name}_ORIGINAL.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/FOTOPRO/Originales")
        }
        val options = ImageCapture.OutputFileOptions.Builder(
            contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
        ).build()

        statusText.text = "Tomando fotografía…"
        capture.takePicture(options, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                    val uri = result.savedUri
                    if (uri == null) {
                        statusText.text = "No se pudo localizar la fotografía."
                        return
                    }
                    resultText.text = "Fotografía guardada. Aplicando optimización…"
                    cameraExecutor.execute {
                        try {
                            val bitmap = contentResolver.openInputStream(uri)?.use {
                                BitmapFactory.decodeStream(it)
                            } ?: throw Exception("No se pudo leer la imagen")
                            val enhanced = enhance(bitmap)
                            val outValues = ContentValues().apply {
                                put(MediaStore.MediaColumns.DISPLAY_NAME, "${name}_FOTOPRO.jpg")
                                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/FOTOPRO/Mejoradas")
                            }
                            val outUri = contentResolver.insert(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, outValues
                            ) ?: throw Exception("No se pudo crear el archivo")
                            contentResolver.openOutputStream(outUri)?.use { stream ->
                                enhanced.compress(Bitmap.CompressFormat.JPEG, 95, stream)
                            }
                            bitmap.recycle()
                            enhanced.recycle()
                            runOnUiThread {
                                statusText.text = "¡Fotografía optimizada!"
                                resultText.text = "Original y versión FOTOPRO guardados en la galería."
                            }
                        } catch (e: Exception) {
                            runOnUiThread {
                                statusText.text = "Foto original guardada; mejora no disponible."
                                resultText.text = e.localizedMessage ?: ""
                            }
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    statusText.text = "Error al tomar la foto: ${exception.localizedMessage}"
                }
            })
    }

    // Optimización inicial conservadora: contraste y color.
    private fun enhance(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = channel(Color.red(c), 1.08f, 3f)
            val g = channel(Color.green(c), 1.08f, 2f)
            val b = channel(Color.blue(c), 1.08f, 0f)
            pixels[i] = Color.argb(Color.alpha(c), r, g, b)
        }
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    private fun channel(v: Int, contrast: Float, offset: Float): Int =
        min(255, max(0, ((v - 128) * contrast + 128 + offset).toInt()))

    private fun toggleVideo() {
        val capture = videoCapture ?: return
        val active = recording
        if (active != null) {
            active.stop()
            recording = null
            videoButton.text = "GRABAR VIDEO"
            statusText.text = "Finalizando video…"
            return
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "FOTOPRO_${stamp()}.mp4")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/FOTOPRO")
        }
        val output = MediaStoreOutputOptions.Builder(
            contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(values).build()

        var pending = capture.output.prepareRecording(this, output)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) pending = pending.withAudioEnabled()

        recording = pending.start(ContextCompat.getMainExecutor(this)) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    videoButton.text = "DETENER"
                    statusText.text = "Grabando video…"
                }
                is VideoRecordEvent.Finalize -> {
                    recording = null
                    videoButton.text = "GRABAR VIDEO"
                    statusText.text = if (event.hasError()) "Error al guardar el video."
                    else "Video guardado. La optimización avanzada se incorporará en una próxima versión."
                }
            }
        }
    }

    private fun stamp() =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
