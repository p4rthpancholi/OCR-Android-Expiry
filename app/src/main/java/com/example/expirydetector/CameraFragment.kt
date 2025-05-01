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
import com.example.expirydetector.utils.DateExtractorV2
import com.example.expirydetector.utils.PermissionUtils
import com.example.expirydetector.utils.WeightExtractor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    private var continuousScanJob: Job? = null
    private var lastDetectedDate: String = ""
    private var lastProcessedTime: Long = 0
    private var lastNavigatedTime: Long = 0

    // Time between scans (milliseconds)
    private val SCAN_INTERVAL = 1500L // Increased to reduce processing load

    // Minimum time between showing results (milliseconds)
    private val NAVIGATION_COOLDOWN = 3000L

    // Number of consecutive scans that must show the same date to confirm it
    private val DETECTION_CONFIDENCE_THRESHOLD = 2
    private var consecutiveDetections = mutableMapOf<String, Int>()

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

        // Change caption for the button to indicate what it does now
        binding.captureButton.text = getString(R.string.pause_scanning)
        binding.helpText.text = getString(R.string.point_camera)

        binding.captureButton.setOnClickListener {
            toggleScanning()
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

    private fun toggleScanning() {
        if (continuousScanJob?.isActive == true) {
            // If scanning is active, pause it
            stopContinuousScanning()
            binding.captureButton.text = getString(R.string.resume_scanning)
            binding.processingText.text = getString(R.string.scanning_paused)
            binding.processingText.visibility = View.VISIBLE
        } else {
            // If scanning is paused, resume it
            startContinuousScanning()
            binding.captureButton.text = getString(R.string.pause_scanning)
            binding.processingText.visibility = View.GONE
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

                // Start continuous scanning
                startContinuousScanning()

            } catch (e: Exception) {
                Log.e(TAG, "Use case binding failed", e)
                Toast.makeText(context, getString(R.string.error_camera_init), Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun startContinuousScanning() {
        // Stop any existing scan job
        stopContinuousScanning()

        // Reset detection state
        consecutiveDetections.clear()

        // Start new continuous scanning job
        continuousScanJob = lifecycleScope.launch {
            while (isActive) {
                if (!processingImage && isAdded && !isDetached) {
                    scanForExpiryDate()
                }
                delay(SCAN_INTERVAL)
            }
        }

        // Update UI to show scanning is active
        binding.processingText.text = getString(R.string.scanning)
        binding.captureButton.text = getString(R.string.pause_scanning)
    }

    private fun stopContinuousScanning() {
        continuousScanJob?.cancel()
        continuousScanJob = null
    }

    private fun scanForExpiryDate() {
        // Skip if we're already processing an image
        if (processingImage || !isAdded) return

        // Skip if previewer isn't ready
        val bitmap = try {
            binding.viewFinder.bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error getting bitmap from preview", e)
            null
        } ?: return

        // Check cooldown period between processing frames
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastProcessedTime < SCAN_INTERVAL) return
        lastProcessedTime = currentTime

        setProcessingState(true)

        // Process bitmap in background
        lifecycleScope.launch {
            try {
                val results = processImageWithOCR(bitmap)
                if (isAdded && !isDetached) {
                    handleScanResults(results)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing image", e)
                // Don't show toast for continuous scanning errors - it would spam the user
                if (isAdded && !isDetached) {
                    setProcessingState(false)
                }
            }
        }
    }

    private fun handleScanResults(results: Triple<String, String, String>) {
        if (!isAdded || isDetached) return

        val extractedText = results.first
        val expiryDate = results.second
        val weight = results.third

        // Skip empty results
        if (expiryDate.isBlank() && weight.isBlank()) {
            setProcessingState(false)
            return
        }

        // Update consecutive detections counter
        if (expiryDate.isNotBlank()) {
            // Increment the count for this date
            val currentCount = consecutiveDetections.getOrDefault(expiryDate, 0) + 1
            consecutiveDetections[expiryDate] = currentCount

            // If we've seen this date consistently, consider it confirmed
            if (currentCount >= DETECTION_CONFIDENCE_THRESHOLD && expiryDate != lastDetectedDate) {
                lastDetectedDate = expiryDate

                // Check if enough time has passed since the last navigation
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastNavigatedTime >= NAVIGATION_COOLDOWN) {
                    lastNavigatedTime = currentTime

                    try {
                        navigateToResults(results)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error navigating to results", e)
                    }

                    // Reset counts after a successful detection
                    consecutiveDetections.clear()
                } else {
                    // Just update the UI with the detected date and weight
                    updateDetectedUI(expiryDate, weight)
                }
            } else {
                // Update UI with candidate date and weight
                updateDetectedUI(expiryDate, weight)
            }
        } else if (weight.isNotBlank()) {
            // If only weight is detected, show it
            updateDetectedUI(expiryDate, weight)
        }

        // Prune old detections to prevent the map from growing too large
        if (consecutiveDetections.size > 10) {
            // Keep only the top 5 most detected dates
            val top5 = consecutiveDetections.entries
                .sortedByDescending { it.value }
                .take(5)
                .associate { it.key to it.value }
            consecutiveDetections.clear()
            consecutiveDetections.putAll(top5)
        }

        setProcessingState(false)
    }

    private fun updateDetectedUI(expiryDate: String, weight: String) {
        if (!isAdded) return

        val detectionInfo = StringBuilder()

        if (expiryDate.isNotBlank()) {
            detectionInfo.append("Date: ").append(expiryDate)
        }

        if (weight.isNotBlank()) {
            if (detectionInfo.isNotEmpty()) detectionInfo.append(" | ")
            detectionInfo.append("Weight: ").append(weight)
        }

        if (detectionInfo.isNotEmpty()) {
            binding.processingText.text = detectionInfo.toString()
            binding.processingText.visibility = View.VISIBLE
        }
    }

    private suspend fun processImageWithOCR(bitmap: Bitmap): Triple<String, String, String> = withContext(Dispatchers.IO) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val image = InputImage.fromBitmap(bitmap, 0)

        try {
            val result = recognizer.process(image).await()
            val extractedText = result.text
            val expiryDate = DateExtractorV2.extractExpiryDate(extractedText)
            val weight = WeightExtractor.extractWeight(extractedText)

            Triple(extractedText, expiryDate, weight)
        } catch (e: Exception) {
            Log.e(TAG, "Text recognition failed", e)
            throw e
        }
    }

    private fun navigateToResults(results: Triple<String, String, String>) {
        if (!isAdded || isDetached) return

        val extractedText = results.first
        val expiryDate = results.second
        val weight = results.third

        try {
            findNavController().navigate(
                CameraFragmentDirections.actionCameraFragmentToResultFragment(
                    detectedText = extractedText,
                    expiryDate = expiryDate,
                    weight = weight
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Navigation error: ${e.message}", e)
            Toast.makeText(context, "Error displaying results", Toast.LENGTH_SHORT).show()
        }

        setProcessingState(false)
    }

    private fun setProcessingState(isProcessing: Boolean) {
        if (!isAdded) return

        processingImage = isProcessing
        binding.apply {
            progressBar.visibility = if (isProcessing) View.VISIBLE else View.GONE
            // Keep the processing text visible for showing detection results
            captureButton.isEnabled = true  // Always keep enabled for pausing
        }
    }

    override fun onPause() {
        super.onPause()
        // Stop scanning when fragment is paused
        stopContinuousScanning()
    }

    override fun onResume() {
        super.onResume()
        // Resume scanning when fragment is resumed (if camera is ready)
        if (isAdded && binding.viewFinder.visibility == View.VISIBLE && continuousScanJob?.isActive != true) {
            startContinuousScanning()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopContinuousScanning()
        cameraExecutor.shutdown()
        _binding = null
    }

    companion object {
        private const val TAG = "CameraFragment"
    }
}
