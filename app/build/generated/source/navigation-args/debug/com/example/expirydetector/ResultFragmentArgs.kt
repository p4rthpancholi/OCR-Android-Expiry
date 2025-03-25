package com.example.expirydetector

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavArgs
import java.lang.IllegalArgumentException
import kotlin.String
import kotlin.jvm.JvmStatic

public data class ResultFragmentArgs(
  public val detectedText: String,
  public val expiryDate: String
) : NavArgs {
  public fun toBundle(): Bundle {
    val result = Bundle()
    result.putString("detectedText", this.detectedText)
    result.putString("expiryDate", this.expiryDate)
    return result
  }

  public fun toSavedStateHandle(): SavedStateHandle {
    val result = SavedStateHandle()
    result.set("detectedText", this.detectedText)
    result.set("expiryDate", this.expiryDate)
    return result
  }

  public companion object {
    @JvmStatic
    public fun fromBundle(bundle: Bundle): ResultFragmentArgs {
      bundle.setClassLoader(ResultFragmentArgs::class.java.classLoader)
      val __detectedText : String?
      if (bundle.containsKey("detectedText")) {
        __detectedText = bundle.getString("detectedText")
        if (__detectedText == null) {
          throw IllegalArgumentException("Argument \"detectedText\" is marked as non-null but was passed a null value.")
        }
      } else {
        throw IllegalArgumentException("Required argument \"detectedText\" is missing and does not have an android:defaultValue")
      }
      val __expiryDate : String?
      if (bundle.containsKey("expiryDate")) {
        __expiryDate = bundle.getString("expiryDate")
        if (__expiryDate == null) {
          throw IllegalArgumentException("Argument \"expiryDate\" is marked as non-null but was passed a null value.")
        }
      } else {
        throw IllegalArgumentException("Required argument \"expiryDate\" is missing and does not have an android:defaultValue")
      }
      return ResultFragmentArgs(__detectedText, __expiryDate)
    }

    @JvmStatic
    public fun fromSavedStateHandle(savedStateHandle: SavedStateHandle): ResultFragmentArgs {
      val __detectedText : String?
      if (savedStateHandle.contains("detectedText")) {
        __detectedText = savedStateHandle["detectedText"]
        if (__detectedText == null) {
          throw IllegalArgumentException("Argument \"detectedText\" is marked as non-null but was passed a null value")
        }
      } else {
        throw IllegalArgumentException("Required argument \"detectedText\" is missing and does not have an android:defaultValue")
      }
      val __expiryDate : String?
      if (savedStateHandle.contains("expiryDate")) {
        __expiryDate = savedStateHandle["expiryDate"]
        if (__expiryDate == null) {
          throw IllegalArgumentException("Argument \"expiryDate\" is marked as non-null but was passed a null value")
        }
      } else {
        throw IllegalArgumentException("Required argument \"expiryDate\" is missing and does not have an android:defaultValue")
      }
      return ResultFragmentArgs(__detectedText, __expiryDate)
    }
  }
}
