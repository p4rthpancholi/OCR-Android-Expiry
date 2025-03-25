package com.example.expirydetector

import android.os.Bundle
import androidx.navigation.NavDirections
import kotlin.Int
import kotlin.String

public class CameraFragmentDirections private constructor() {
  private data class ActionCameraFragmentToResultFragment(
    public val detectedText: String,
    public val expiryDate: String
  ) : NavDirections {
    public override val actionId: Int = R.id.action_cameraFragment_to_resultFragment

    public override val arguments: Bundle
      get() {
        val result = Bundle()
        result.putString("detectedText", this.detectedText)
        result.putString("expiryDate", this.expiryDate)
        return result
      }
  }

  public companion object {
    public fun actionCameraFragmentToResultFragment(detectedText: String, expiryDate: String):
        NavDirections = ActionCameraFragmentToResultFragment(detectedText, expiryDate)
  }
}
