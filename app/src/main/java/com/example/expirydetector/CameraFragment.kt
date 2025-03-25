package com.example.expirydetector

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.expirydetector.databinding.FragmentCameraBinding
import com.example.expirydetector.utils.DateExtractor
import com.example.expirydetector.utils.PermissionUtils
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraFragment : Fragment() {

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!

    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var processingImage = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            showPermissionDeniedUI()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.captureButton.setOnClickListener {
            if (!processingImage) {
                captureImage()
            }
        }

        binding.grantPermissionButton.setOnClickListener {
            requestCameraPermission()
        }

        if (PermissionUtils.hasCameraPermission(requireContext())) {
            startCamera()
        } else {
            requestCameraPermission()
        }
    }

    private fun requestCameraPermission() {
        when {
            PermissionUtils.hasCameraPermission(requireContext()) -> {
                startCamera()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                PermissionUtils.showPermissionRationale(requireContext()) {
                    requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun showPermissionDeniedUI() {
        binding.apply {
            viewFinder.visibility = View.GONE
            overlay.visibility = View.GONE
            captureButton.visibility = View.GONE
            helpText.visibility = View.GONE
            grantPermissionButton.visibility = View.VISIBLE
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            try {
                // Camera provider is now guaranteed to be available
                val cameraProvider = cameraProviderFuture.get()

                // Set up the preview use case
                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                    }

                // Set up the image capture use case
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                // Select back camera as a default
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )

                binding.apply {
                    viewFinder.visibility = View.VISIBLE
                    overlay.visibility = View.VISIBLE
                    captureButton.visibility = View.VISIBLE
                    helpText.visibility = View.VISIBLE
                    grantPermissionButton.visibility = View.GONE
                }

            } catch (e: Exception) {
                Log.e(TAG, "Use case binding failed", e)
                Toast.makeText(context, getString(R.string.error_camera_init), Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun captureImage() {
        val imageCapture = imageCapture ?: return
        
        setProcessingState(true)
        
        // Get a bitmap from the preview
        val bitmap = binding.viewFinder.bitmap ?: return
        
        // Process bitmap in background
        lifecycleScope.launch {
            try {
                val results = processImageWithOCR(bitmap)
                navigateToResults(results)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing image", e)
                Toast.makeText(context, getString(R.string.error_ocr_process), Toast.LENGTH_SHORT).show()
                setProcessingState(false)
            }
        }
    }
    
    private suspend fun processImageWithOCR(bitmap: Bitmap): Pair<String, String> = withContext(Dispatchers.IO) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val image = InputImage.fromBitmap(bitmap, 0)
        
        try {
            val result = recognizer.process(image).await()
            val extractedText = result.text
            val expiryDate = DateExtractor.extractExpiryDate(extractedText)
            
            Pair(extractedText, expiryDate)
        } catch (e: Exception) {
            Log.e(TAG, "Text recognition failed", e)
            throw e
        }
    }
    
    private fun navigateToResults(results: Pair<String, String>) {
        val extractedText = results.first
        val expiryDate = results.second
        
        findNavController().navigate(
            CameraFragmentDirections.actionCameraFragmentToResultFragment(
                detectedText = extractedText,
                expiryDate = expiryDate
            )
        )
        
        setProcessingState(false)
    }
    
    private fun setProcessingState(isProcessing: Boolean) {
        processingImage = isProcessing
        binding.apply {
            progressBar.visibility = if (isProcessing) View.VISIBLE else View.GONE
            processingText.visibility = if (isProcessing) View.VISIBLE else View.GONE
            captureButton.isEnabled = !isProcessing
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        _binding = null
    }

    companion object {
        private const val TAG = "CameraFragment"
    }
}
