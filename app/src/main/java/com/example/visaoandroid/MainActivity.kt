package com.example.visaoandroid

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.vision.detector.Detection
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var overlayView: OverlayView
    private lateinit var objectDetector: ObjectDetector
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    // CONFIGURAÇÃO DE REDE
    private val NOTEBOOK_IP = "192.168.0.20" // <-- Mude para o IP do seu notebook
    private val NOTEBOOK_PORT = 5005

    private val personThreshold = 0.35f
    private val otherThreshold = 0.5f

    private val faceDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
        FaceDetection.getClient(options)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        overlayView = findViewById(R.id.overlayView)

        setupDetector()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 10)
        }
    }

    private fun setupDetector() {
        val options = ObjectDetector.ObjectDetectorOptions.builder()
            .setBaseOptions(BaseOptions.builder().setNumThreads(4).build())
            .setMaxResults(15)
            .setScoreThreshold(0.1f)
            .build()
        objectDetector = ObjectDetector.createFromFileAndOptions(
            this, "efficientdet-lite2.tflite", options
        )
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                val rotation = imageProxy.imageInfo.rotationDegrees
                val original = imageProxy.toBitmap()
                val rotatedBitmap = if (rotation != 0) {
                    val matrix = Matrix()
                    matrix.postRotate(rotation.toFloat())
                    Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
                } else original

                val tensorImage = TensorImage.fromBitmap(rotatedBitmap)
                val rawResults = objectDetector.detect(tensorImage)

                val filtered = rawResults.filter { detection: Detection ->
                    val label = detection.categories.firstOrNull()?.label ?: ""
                    val score = detection.categories.firstOrNull()?.score ?: 0f
                    if (label == "person") score >= personThreshold else score >= otherThreshold
                }

                // LÓGICA DE ENVIO PARA O NOTEBOOK
                if (filtered.isNotEmpty()) {
                    val box = filtered[0].boundingBox
                    val centerX = (box.left + box.right) / 2 / rotatedBitmap.width
                    val centerY = (box.top + box.bottom) / 2 / rotatedBitmap.height
                    sendUdpData(centerX, centerY)
                }

                val faceRects: List<RectF> = try {
                    val inputImage = InputImage.fromBitmap(rotatedBitmap, 0)
                    val faces = Tasks.await(faceDetector.process(inputImage))
                    faces.map { face -> RectF(face.boundingBox) }
                } catch (e: Exception) { listOf() }

                runOnUiThread {
                    overlayView.setResults(filtered, faceRects, rotatedBitmap.width, rotatedBitmap.height)
                }
                imageProxy.close()
            }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun sendUdpData(x: Float, y: Float) {
        Thread {
            try {
                val socket = DatagramSocket()
                val message = "$x,$y".toByteArray()
                val address = InetAddress.getByName(NOTEBOOK_IP)
                val packet = DatagramPacket(message, message.size, address, NOTEBOOK_PORT)
                socket.send(packet)
                socket.close()
            } catch (e: Exception) { e.printStackTrace() }
        }.start()
    }
}
