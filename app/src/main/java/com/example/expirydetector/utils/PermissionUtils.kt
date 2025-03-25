package com.example.expirydetector.utils

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.expirydetector.R

/**
 * Utility class for permission-related operations.
 */
object PermissionUtils {
    
    /**
     * Check if the camera permission is granted.
     * 
     * @param context The context
     * @return true if camera permission is granted, false otherwise
     */
    fun hasCameraPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Shows a rationale dialog explaining why the permission is needed.
     * 
     * @param context The context
     * @param onPositiveClick Callback to be invoked when the user clicks the positive button
     */
    fun showPermissionRationale(context: Context, onPositiveClick: () -> Unit) {
        AlertDialog.Builder(context)
            .setTitle(R.string.camera_permission_required)
            .setMessage(R.string.permission_rationale)
            .setPositiveButton(android.R.string.ok) { _, _ -> onPositiveClick() }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
            .show()
    }
}
