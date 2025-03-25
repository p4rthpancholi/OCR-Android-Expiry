package com.example.expirydetector.utils

import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

/**
 * Utility class for extracting expiration dates from text.
 */
object DateExtractor {

    // Common patterns for expiration dates
    private val datePatterns = listOf(
        // MM/DD/YYYY or MM-DD-YYYY
        Pattern.compile("(0[1-9]|1[0-2])[/\\-](0[1-9]|[12][0-9]|3[01])[/\\-](20\\d{2})"),
        
        // DD/MM/YYYY or DD-MM-YYYY
        Pattern.compile("(0[1-9]|[12][0-9]|3[01])[/\\-](0[1-9]|1[0-2])[/\\-](20\\d{2})"),
        
        // YYYY/MM/DD or YYYY-MM-DD
        Pattern.compile("(20\\d{2})[/\\-](0[1-9]|1[0-2])[/\\-](0[1-9]|[12][0-9]|3[01])"),
        
        // MM/YYYY or MM-YYYY
        Pattern.compile("(0[1-9]|1[0-2])[/\\-](20\\d{2})"),
        
        // YYYY/MM or YYYY-MM
        Pattern.compile("(20\\d{2})[/\\-](0[1-9]|1[0-2])"),
        
        // Common text markers followed by date
        Pattern.compile("(?i)(?:exp|exp date|expiry|best before|best by|use by)[\\s:]+([0-9]{1,2}[/\\-][0-9]{1,2}[/\\-](?:20)?[0-9]{2})")
    )

    // Common text markers for expiration dates
    private val expiryMarkers = listOf(
        "EXP", "EXP DATE", "EXPIRY", "EXPIRY DATE", "BEST BEFORE", "BEST BY", "USE BY",
        "EXPIRES", "EXPIRATION", "EXPIRATION DATE"
    )

    /**
     * Extracts expiration date from the given text.
     * 
     * @param text The text to extract expiration date from
     * @return The extracted expiration date as a string, or empty string if none found
     */
    fun extractExpiryDate(text: String): String {
        if (text.isBlank()) return ""
        
        // First check for exact patterns
        for (pattern in datePatterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                return matcher.group(0)
            }
        }
        
        // Split text into lines and check each line
        val lines = text.split("\n")
        for (line in lines) {
            // Look for lines with expiry markers
            val lowerLine = line.lowercase(Locale.getDefault())
            for (marker in expiryMarkers) {
                if (lowerLine.contains(marker.lowercase(Locale.getDefault()))) {
                    // Try to extract date from this line
                    val dateCandidate = extractDateFromLine(line)
                    if (dateCandidate.isNotEmpty()) {
                        return dateCandidate
                    }
                }
            }
        }
        
        return ""
    }
    
    /**
     * Attempts to extract a date from a single line of text.
     */
    private fun extractDateFromLine(line: String): String {
        // First check for numerical patterns
        val numericPattern = Pattern.compile("\\d{1,4}[/\\-.]\\d{1,4}([/\\-.]\\d{2,4})?")
        val matcher = numericPattern.matcher(line)
        
        if (matcher.find()) {
            val potentialDate = matcher.group(0)
            // Validate if it looks like a real date
            if (isValidDateFormat(potentialDate)) {
                return potentialDate
            }
        }
        
        return ""
    }
    
    /**
     * Basic validation to check if a string looks like a date.
     */
    private fun isValidDateFormat(dateStr: String): Boolean {
        // If it's just numbers with separators, it's probably a date
        val parts = dateStr.split(Regex("[/\\-.]"))
        
        // Check if we have 2 or 3 parts, as most dates are MM/YY, MM/DD/YY, etc.
        return parts.size in 2..3 && parts.all { it.all { c -> c.isDigit() } }
    }
}
