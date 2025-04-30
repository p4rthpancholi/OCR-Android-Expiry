package com.example.expirydetector.utils

import android.util.Log
import java.util.*
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * Utility class for extracting weight and quantity information from product labels.
 */
object WeightExtractor {
    private const val TAG = "WeightExtractor"

    // Common patterns for weight and quantity
    private val weightPatterns by lazy {
        createWeightPatterns()
    }

    private fun createWeightPatterns(): List<Pattern> {
        val patterns = mutableListOf<Pattern>()

        try {
            // Weight in pounds (lb, lbs) - examples: 1.25 lb, 2 lbs, 0.5lb
            patterns.add(Pattern.compile("(\\d+\\.?\\d*)\\s*(?:lb|lbs|pound|pounds)\\b", Pattern.CASE_INSENSITIVE))

            // Weight in kilograms (kg) - examples: 1.5 kg, 0.25kg
            patterns.add(Pattern.compile("(\\d+\\.?\\d*)\\s*(?:kg|kgs|kilogram|kilograms)\\b", Pattern.CASE_INSENSITIVE))

            // Weight in grams (g) - examples: 500g, 500 g, 500 grams
            patterns.add(Pattern.compile("(\\d+\\.?\\d*)\\s*(?:g|gram|grams)\\b(?!\\w)", Pattern.CASE_INSENSITIVE))

            // Weight in ounces (oz) - examples: 8 oz, 16oz, 8-oz
            patterns.add(Pattern.compile("(\\d+\\.?\\d*)\\s*(?:-)?(?:oz|ounce|ounces)\\b", Pattern.CASE_INSENSITIVE))

            // Weight with NET WT prefix - examples: NET WT 16 OZ, Net Wt. 1.5 LB
            patterns.add(Pattern.compile("(?:net\\s+wt|net\\s+weight)[.:]?\\s*(\\d+\\.?\\d*)\\s*(?:oz|ounce|ounces|lb|lbs|g|gram|grams|kg)\\b",
                Pattern.CASE_INSENSITIVE))

            // Generic numerical with units (for cases not covered above)
            patterns.add(Pattern.compile("(?:weight|wt|net)[.:]?\\s*(\\d+\\.?\\d*)\\s*(?:oz|ounce|ounces|lb|lbs|g|gram|grams|kg)\\b",
                Pattern.CASE_INSENSITIVE))

            // Specific format found in many labels - examples: 1.19 lb
            patterns.add(Pattern.compile("(?:^|\\s)(\\d+\\.\\d+)\\s*(?:lb|lbs|pound|pounds|oz|ounce|ounces|g|gram|grams|kg)\\b",
                Pattern.CASE_INSENSITIVE))
        } catch (e: PatternSyntaxException) {
            Log.e(TAG, "Error compiling weight patterns", e)
            // Add a basic fallback pattern
            patterns.add(Pattern.compile("\\d+\\.?\\d*\\s*(?:lb|kg|g|oz)\\b", Pattern.CASE_INSENSITIVE))
        }

        return patterns
    }

    // Common weight indicators that help identify weight information
    private val weightIndicators = listOf(
        "NET WT", "NET WEIGHT", "WEIGHT", "WT", "NET", "APPROX",
        "CONTENIDO", "PESO NETO", "POIDS NET"
    )

    /**
     * Extracts weight information from the given text.
     *
     * @param text The text to extract weight from
     * @return The extracted weight as a string, or empty string if none found
     */
    fun extractWeight(text: String): String {
        if (text.isBlank()) return ""

        try {
            // Store all weight candidates with their weights
            val weightCandidates = mutableListOf<Pair<String, Double>>()

            // First check for exact patterns globally
            for (pattern in weightPatterns) {
                try {
                    val matcher = pattern.matcher(text)
                    while (matcher.find()) {
                        val weight = matcher.group(0)
                        var score = 1.0

                        // Check if weight is near a weight indicator
                        if (isNearWeightIndicator(text, matcher.start())) {
                            score += 3.0
                        }

                        // Add the candidate with its score
                        weightCandidates.add(Pair(weight, score))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing pattern: ${pattern.pattern()}", e)
                    continue
                }
            }

            // Process line by line for contextual analysis
            val lines = text.split("\n")
            for (line in lines) {
                // Look for lines with weight indicators
                val lowerLine = line.lowercase(Locale.getDefault())
                for (indicator in weightIndicators) {
                    if (lowerLine.contains(indicator.lowercase(Locale.getDefault()))) {
                        // Try to extract weight from this line with higher priority
                        for (pattern in weightPatterns) {
                            try {
                                val matcher = pattern.matcher(line)
                                if (matcher.find()) {
                                    val weight = matcher.group(0)
                                    // Higher score since it's on a line with an indicator
                                    weightCandidates.add(Pair(weight, 5.0))
                                }
                            } catch (e: Exception) {
                                continue
                            }
                        }
                    }
                }

                // Check for "NET WT/CT" or similar format often used in price labels
                if (lowerLine.contains("net wt") || lowerLine.contains("net weight") ||
                    lowerLine.contains("net wt/ct") || lowerLine.contains("wt/ct")) {
                    // Extract numerical value with unit
                    try {
                        val weightPattern = Pattern.compile("(\\d+\\.?\\d*)\\s*(?:lb|lbs|pound|pounds|oz|ounce|ounces|g|gram|grams|kg)\\b",
                            Pattern.CASE_INSENSITIVE)
                        val matcher = weightPattern.matcher(line)
                        if (matcher.find()) {
                            val weight = matcher.group(0)
                            weightCandidates.add(Pair(weight, 5.0))
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error extracting weight from NET WT line", e)
                    }
                }
            }

            // If we have multiple candidates, select the highest weighted one
            if (weightCandidates.isNotEmpty()) {
                return weightCandidates.maxByOrNull { it.second }?.first ?: ""
            } else {
                // Fallback: check for any numerical value with common weight units
                try {
                    val fallbackPattern = Pattern.compile("(\\d+\\.?\\d*)\\s*(?:lb|lbs|pounds?|oz|ounces?|g|grams?|kg|kilograms?)\\b",
                        Pattern.CASE_INSENSITIVE)
                    for (line in lines) {
                        val matcher = fallbackPattern.matcher(line)
                        if (matcher.find()) {
                            return matcher.group(0)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in fallback weight extraction", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting weight", e)
        }

        return ""
    }

    /**
     * Check if a position in text is near a weight indicator
     */
    private fun isNearWeightIndicator(text: String, position: Int): Boolean {
        try {
            // Define a window to check for indicators (chars before position)
            val windowSize = 20
            val startWindow = maxOf(0, position - windowSize)
            val endWindow = position

            val windowText = text.substring(startWindow, endWindow).lowercase(Locale.getDefault())

            // Check if any weight indicator is in the window
            for (indicator in weightIndicators) {
                if (windowText.contains(indicator.lowercase(Locale.getDefault()))) {
                    return true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for nearby weight indicator", e)
        }

        return false
    }

    /**
     * Normalizes weight to a standard format.
     * This can be expanded to convert different units to a standard one.
     */
    fun normalizeWeight(weightStr: String): String {
        // For now, just remove extra spaces and return lowercase
        return weightStr.trim()
    }
}
