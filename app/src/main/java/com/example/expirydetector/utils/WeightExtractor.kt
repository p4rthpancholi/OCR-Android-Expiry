package com.example.expirydetector.utils

import android.util.Log
import java.util.*
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * Utility class for extracting weight and quantity information from product labels.
 * Specifically designed to handle meat labels with Net Wt/Ct and Tare weights.
 */
object WeightExtractor {
    private const val TAG = "WeightExtractor"

    /**
     * Tracks the last extracted weight across frames to provide a fallback
     * This helps prevent "flickering" of weight detection
     */
    private var lastSuccessfulExtraction: String = ""
    private var lastExtractionTimestamp: Long = 0
    private const val EXTRACTION_CACHE_DURATION = 5000 // 5 seconds

    /**
     * Extracts weight information from the given text.
     *
     * @param text The text to extract weight from
     * @return The extracted weight as a string, or the last successful extraction if current extraction fails
     */
    fun extractWeight(text: String): String {
        if (text.isBlank()) {
            return getFallbackWeight()
        }

        try {
            Log.d(TAG, "Starting weight extraction on text: ${text.take(100)}...")

            // First, dump the full text for debugging
            Log.d(TAG, "Full text for extraction:\n$text")

            // Step 1: Try "NET WT. LBS X.XX" format (chicken label format)
            val netWtLbsFormatPattern = Pattern.compile("NET\\s+WT\\.?\\s*LBS\\.?\\s+([0-9]+\\.[0-9]+)",
                Pattern.CASE_INSENSITIVE)
            val netWtLbsFormatMatcher = netWtLbsFormatPattern.matcher(text)

            if (netWtLbsFormatMatcher.find()) {
                val weightValue = netWtLbsFormatMatcher.group(1)
                val netWeight = "$weightValue lb"
                Log.d(TAG, "Found NET WT. LBS X.XX format weight: $netWeight")
                updateLastSuccessfulExtraction(netWeight)
                return netWeight
            }

            // Step 2: Try to find exact Net Wt/Ct patterns with decimal
            val netWtCtPattern = Pattern.compile("Net\\s+Wt[/]?Ct\\s+([0-9]+\\.[0-9]+\\s*lb)",
                Pattern.CASE_INSENSITIVE)
            val netWtCtMatcher = netWtCtPattern.matcher(text)

            if (netWtCtMatcher.find()) {
                val exactMatch = netWtCtMatcher.group(1)
                Log.d(TAG, "Found exact Net Wt/Ct match: $exactMatch")
                updateLastSuccessfulExtraction(exactMatch)
                return exactMatch
            }

            // Step 2b: Special handling for meat labels - find "Net Wt/Ct" line followed by weight on next line
            val lines = text.split("\n")
            for (i in 0 until lines.size - 1) {
                val line = lines[i].trim()
                val nextLine = lines[i + 1].trim()

                if ((line.lowercase(Locale.getDefault()).startsWith("net wt") ||
                            line.lowercase(Locale.getDefault()).contains("net wt/ct") ||
                            line.lowercase(Locale.getDefault()).contains("net weight"))) {

                    Log.d(TAG, "Found Net Wt line: $line")
                    Log.d(TAG, "Checking next line: $nextLine")

                    // Look for weight on next line
                    val nextLineWeightPattern = Pattern.compile("([0-9]+\\.[0-9]+)\\s*(?:lb|lbs)",
                        Pattern.CASE_INSENSITIVE)
                    val nextLineWeightMatcher = nextLineWeightPattern.matcher(nextLine)

                    if (nextLineWeightMatcher.find()) {
                        val weight = nextLineWeightMatcher.group(0)
                        Log.d(TAG, "Found weight on line after Net Wt: $weight")
                        return weight
                    }
                }
            }

            // Step 3: Try to find exact Net Wt/Ct patterns without decimal (e.g., "143 lb" instead of "1.43 lb")
            val netWtCtIntPattern = Pattern.compile("Net\\s+Wt[/]?Ct\\s+([0-9]+\\s*lb)",
                Pattern.CASE_INSENSITIVE)
            val netWtCtIntMatcher = netWtCtIntPattern.matcher(text)

            if (netWtCtIntMatcher.find()) {
                val exactIntMatch = netWtCtIntMatcher.group(1)
                Log.d(TAG, "Found exact Net Wt/Ct match without decimal: $exactIntMatch")

                // Convert integer weight to decimal if it's likely a decimal weight
                // (weights over 10 lb are often correctly detected, but smaller weights
                // might be missing the decimal point)
                val weightValue = extractNumericValue(exactIntMatch)
                if (weightValue != null && weightValue > 100 && weightValue < 1000) {
                    // Convert 123 -> 1.23
                    val decimalWeight = weightValue / 100.0
                    val correctedWeight = "$decimalWeight lb"
                    Log.d(TAG, "Converted likely decimal weight: $exactIntMatch -> $correctedWeight")
                    return correctedWeight
                }

                return exactIntMatch
            }

            // Step 4: Try to find "NET WT. X.XX LBS." format (usual format)
            val netWtLbsPattern = Pattern.compile("NET\\s+WT\\.?\\s+([0-9]+\\.[0-9]+)\\s+LBS?\\.?",
                Pattern.CASE_INSENSITIVE)
            val netWtLbsMatcher = netWtLbsPattern.matcher(text)

            if (netWtLbsMatcher.find()) {
                val weightValue = netWtLbsMatcher.group(1)
                val netWeight = "$weightValue lb"
                Log.d(TAG, "Found NET WT. X.XX LBS format weight: $netWeight")
                return netWeight
            }

            // Step 5: Collect weights and categorize by context (Tare vs Net Wt)
            val tareWeights = mutableListOf<String>()
            val netWtWeights = mutableListOf<String>()
            val allWeights = mutableListOf<String>()

            for (i in 0 until lines.size) {
                val line = lines[i].trim()
                if (line.isBlank()) continue

                Log.d(TAG, "Analyzing line $i: $line")

                // Check if this is a Tare line
                if (line.contains("Tare", ignoreCase = true)) {
                    // Find all weights on this line
                    val tareWeightPattern = Pattern.compile("([0-9]+\\.[0-9]+)\\s*(?:lb|lbs|pound|pounds|oz|ounce|ounces)\\.?",
                        Pattern.CASE_INSENSITIVE)
                    val tareWeightMatcher = tareWeightPattern.matcher(line)

                    while (tareWeightMatcher.find()) {
                        val tareWeight = tareWeightMatcher.group(0)
                        tareWeights.add(tareWeight)
                        Log.d(TAG, "Found Tare weight: $tareWeight")
                    }
                }

                // Check if this is a Net Wt line
                if (containsNetWeightIndicator(line)) {
                    // Find all weights on this line
                    val netWtWeightPattern = Pattern.compile("([0-9]+\\.[0-9]+)\\s*(?:lb|lbs|pound|pounds|oz|ounce|ounces|g|gram|grams|kg|kilogram|kilograms)\\.?",
                        Pattern.CASE_INSENSITIVE)
                    val netWtWeightMatcher = netWtWeightPattern.matcher(line)

                    while (netWtWeightMatcher.find()) {
                        val netWtWeight = netWtWeightMatcher.group(0)
                        netWtWeights.add(netWtWeight)
                        Log.d(TAG, "Found Net Wt weight: $netWtWeight")
                    }

                    // Also look for bare decimal numbers
                    val bareNumberPattern = Pattern.compile("\\b([0-9]+\\.[0-9]+)\\b")
                    val bareNumberMatcher = bareNumberPattern.matcher(line)

                    while (bareNumberMatcher.find()) {
                        val bareNumber = bareNumberMatcher.group(1)
                        val weightStr = "$bareNumber lb"

                        // Avoid adding price-like values (typically over 10 for meat)
                        val numValue = bareNumber.toDoubleOrNull() ?: 0.0
                        if (numValue <= 10.0) {
                            // Only add if not already added
                            if (!netWtWeights.any { it.contains(bareNumber) }) {
                                netWtWeights.add(weightStr)
                                Log.d(TAG, "Found bare number on Net Wt line: $weightStr")
                            }
                        } else {
                            Log.d(TAG, "Skipping potential price value: $bareNumber")
                        }
                    }
                }

                // Extract all weights from each line (for comprehensive collection)
                val weights = extractWeightsFromLine(line)
                for (weight in weights) {
                    if (!allWeights.contains(weight) &&
                        !tareWeights.contains(weight) &&
                        !netWtWeights.contains(weight)) {

                        allWeights.add(weight)
                        Log.d(TAG, "Found general weight: $weight")
                    }
                }

                // Special handling for integer weights (missing decimal point)
                if (containsNetWeightIndicator(line)) {
                    // Check next line for integer weights
                    if (i < lines.size - 1) {
                        val nextLine = lines[i + 1].trim()

                        // Look for integer weights on next line
                        val intWeightPattern = Pattern.compile("\\b([0-9]{2,3})\\s*lb\\b",
                            Pattern.CASE_INSENSITIVE)
                        val intWeightMatcher = intWeightPattern.matcher(nextLine)

                        if (intWeightMatcher.find()) {
                            val intWeight = intWeightMatcher.group(0)
                            val intValue = extractNumericValue(intWeight) ?: 0.0

                            if (intValue > 100 && intValue < 1000) {
                                val decimalValue = intValue / 100.0
                                val correctedWeight = "$decimalValue lb"

                                netWtWeights.add(correctedWeight)
                                Log.d(TAG, "Found integer weight after Net Wt line: $intWeight -> $correctedWeight")
                            }
                        }

                        // Also look for bare numbers
                        val bareNumPattern = Pattern.compile("\\b([0-9]{2,3})\\b")
                        val bareNumMatcher = bareNumPattern.matcher(nextLine)

                        if (bareNumMatcher.find()) {
                            val bareNum = bareNumMatcher.group(1)
                            val numValue = bareNum.toIntOrNull() ?: 0

                            if (numValue > 100 && numValue < 1000) {
                                val decimalValue = numValue / 100.0
                                val correctedWeight = "$decimalValue lb"

                                netWtWeights.add(correctedWeight)
                                Log.d(TAG, "Found bare number after Net Wt line: $bareNum -> $correctedWeight")
                            }
                        }
                    }
                }
            }

            // Step 6: If we found weights on Net Wt lines, use the largest one
            if (netWtWeights.isNotEmpty()) {
                val largestNetWt = netWtWeights.maxByOrNull { extractNumericValue(it) ?: 0.0 }

                if (largestNetWt != null) {
                    Log.d(TAG, "Selected largest Net Wt weight: $largestNetWt")
                    return largestNetWt
                }
            }

            // Step 7: Filter out Tare weights from all weights
            val validWeights = allWeights.filter { weight -> !tareWeights.contains(weight) }

            // Add any weights we extracted directly
            val combinedWeights = mutableListOf<String>()
            combinedWeights.addAll(validWeights)
            combinedWeights.addAll(netWtWeights)

            if (combinedWeights.isNotEmpty()) {
                val largestWeight = combinedWeights.maxByOrNull { extractNumericValue(it) ?: 0.0 }

                if (largestWeight != null) {
                    Log.d(TAG, "Selected largest valid weight: $largestWeight")
                    return largestWeight
                }
            }

            // Step 8: If no valid weights after filtering, use any weight
            val anyWeight = allWeights.maxByOrNull { extractNumericValue(it) ?: 0.0 }
            if (anyWeight != null) {
                Log.d(TAG, "Using any available weight: $anyWeight")
                return anyWeight
            }

            // Step 9: Special handling for lines with "NET WT. LBS" or similar
            for (line in lines) {
                if (line.contains("NET WT", ignoreCase = true) ||
                    line.contains("WEIGHT", ignoreCase = true)) {

                    // Look for a number on this line
                    val numberPattern = Pattern.compile("([0-9]+\\.[0-9]+)")
                    val numberMatcher = numberPattern.matcher(line)

                    if (numberMatcher.find()) {
                        val weightValue = numberMatcher.group(1)
                        val netWeight = "$weightValue lb"
                        Log.d(TAG, "Found number on NET WT line: $netWeight")
                        return netWeight
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error extracting weight", e)
        }

        // If we couldn't extract a weight, use fallback
        return getFallbackWeight()
    }

    /**
     * Updates the last successfully extracted weight and timestamp
     */
    private fun updateLastSuccessfulExtraction(weight: String) {
        if (weight.isNotBlank()) {
            lastSuccessfulExtraction = weight
            lastExtractionTimestamp = System.currentTimeMillis()
            Log.d(TAG, "Updated last successful extraction: $weight")
        }
    }

    /**
     * Provides a fallback weight if one was recently extracted
     * This helps prevent "flickering" of the UI when OCR doesn't catch the weight in every frame
     */
    private fun getFallbackWeight(): String {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastSuccess = currentTime - lastExtractionTimestamp

        if (lastSuccessfulExtraction.isNotBlank() && timeSinceLastSuccess < EXTRACTION_CACHE_DURATION) {
            Log.d(TAG, "Using cached weight: $lastSuccessfulExtraction (${timeSinceLastSuccess}ms old)")
            return lastSuccessfulExtraction
        }

        return ""
    }

    /**
     * Extract all weights from a single line of text
     */
    private fun extractWeightsFromLine(line: String): List<String> {
        val weights = mutableListOf<String>()

        try {
            // Standard weight pattern with decimal (lb/lbs)
            val weightPattern = Pattern.compile("([0-9]+\\.[0-9]+)\\s*(?:lb|lbs|pound|pounds)\\.?",
                Pattern.CASE_INSENSITIVE)
            val matcher = weightPattern.matcher(line)

            while (matcher.find()) {
                weights.add(matcher.group(0))
            }

            // Integer weight pattern - could be missing decimal point
            val intWeightPattern = Pattern.compile("\\b([0-9]{2,3})\\s*(?:lb|lbs|pound|pounds)\\.?",
                Pattern.CASE_INSENSITIVE)
            val intMatcher = intWeightPattern.matcher(line)

            while (intMatcher.find()) {
                val intWeight = intMatcher.group(0)
                // Check if we already have this weight (might have matched both patterns)
                if (!weights.contains(intWeight)) {
                    weights.add(intWeight)
                }
            }

            // Also look for bare numbers on Net Wt lines
            if (containsNetWeightIndicator(line)) {
                val bareNumberPattern = Pattern.compile("\\b([0-9]+\\.[0-9]+)\\b")
                val bareNumberMatcher = bareNumberPattern.matcher(line)

                while (bareNumberMatcher.find()) {
                    val bareNumber = bareNumberMatcher.group(1)
                    val weightStr = "$bareNumber lb"

                    // Only add if we don't already have this weight
                    if (!weights.any { it.contains(bareNumber) }) {
                        weights.add(weightStr)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting weights from line: $line", e)
        }

        return weights
    }

    /**
     * Check if line contains net weight indicators
     */
    private fun containsNetWeightIndicator(line: String): Boolean {
        val lowerLine = line.lowercase(Locale.getDefault())

        return lowerLine.contains("net wt") ||
                lowerLine.contains("net weight") ||
                lowerLine.contains("net w") ||
                lowerLine.contains("net. wt") ||
                lowerLine.contains("weight")
    }

    /**
     * Extract numeric value from weight string (e.g., "1.43 lb" -> 1.43)
     */
    private fun extractNumericValue(weightStr: String): Double? {
        try {
            // First try to match decimal numbers
            val decimalPattern = Pattern.compile("([0-9]+\\.[0-9]+)")
            val decimalMatcher = decimalPattern.matcher(weightStr)

            if (decimalMatcher.find()) {
                return decimalMatcher.group(1)?.toDoubleOrNull()
            }

            // Then try to match integer numbers
            val intPattern = Pattern.compile("([0-9]+)")
            val intMatcher = intPattern.matcher(weightStr)

            if (intMatcher.find()) {
                return intMatcher.group(1)?.toDoubleOrNull()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting numeric value from $weightStr", e)
        }

        return null
    }

    /**
     * Normalizes weight to a standard format.
     * This can be expanded to convert different units to a standard one.
     */
    fun normalizeWeight(weightStr: String): String {
        // Just remove extra spaces and return
        return weightStr.trim()
    }
}
