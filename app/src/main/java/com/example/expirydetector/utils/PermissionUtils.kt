package com.example.expirydetector.utils

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.expirydetector.R

/**
 * Utility class for handling permissions in the app.
 */
object PermissionUtils {

    /**
     * Checks if the app has camera permission.
     *
     * @param context The application context
     * @return True if camera permission is granted, false otherwise
     */
    fun hasCameraPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Shows a rationale dialog explaining why the app needs camera permission.
     *
     * @param context The application context
     * @param onRequestPermission Callback to be called when user agrees to request permission
     */
    fun showPermissionRationale(context: Context, onRequestPermission: () -> Unit) {
        AlertDialog.Builder(context)
            .setTitle("Camera Permission Required")
            .setMessage(context.getString(R.string.camera_permission_required))
            .setPositiveButton("Grant") { _, _ ->
                onRequestPermission()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }
}
