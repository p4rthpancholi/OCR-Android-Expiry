package com.example.expirydetector

import androidx.navigation.ActionOnlyNavDirections
import androidx.navigation.NavDirections

public class ResultFragmentDirections private constructor() {
  public companion object {
    public fun actionResultFragmentToCameraFragment(): NavDirections =
        ActionOnlyNavDirections(R.id.action_resultFragment_to_cameraFragment)
  }
}
