package com.example.expirydetector

import android.os.Bundle
import androidx.navigation.NavDirections
import kotlin.Int
import kotlin.String

public class CameraFragmentDirections private constructor() {
  private data class ActionCameraFragmentToResultFragment(
    public val detectedText: String,
    public val expiryDate: String,
    public val weight: String
  ) : NavDirections {
    public override val actionId: Int = R.id.actionCameraFragmentToResultFragment

    public override val arguments: Bundle
      get() {
        val result = Bundle()
        result.putString("detectedText", this.detectedText)
        result.putString("expiryDate", this.expiryDate)
        result.putString("weight", this.weight)
        return result
      }
  }

  public companion object {
    public fun actionCameraFragmentToResultFragment(
      detectedText: String,
      expiryDate: String,
      weight: String
    ): NavDirections = ActionCameraFragmentToResultFragment(detectedText, expiryDate, weight)
  }
}
