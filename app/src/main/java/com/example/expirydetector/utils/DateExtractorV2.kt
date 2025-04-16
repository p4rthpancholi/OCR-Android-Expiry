package com.example.expirydetector.utils

import android.util.Log
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

/**
 * Utility class for extracting expiration dates from text.
 */
object DateExtractorV2 {

    // Month abbreviations and full names
    private val months = listOf(
        "jan", "january",
        "feb", "february",
        "mar", "march",
        "apr", "april",
        "may", "may",
        "jun", "june",
        "jul", "july",
        "aug", "august",
        "sep", "september",
        "oct", "october",
        "nov", "november",
        "dec", "december"
    )

    // Month abbreviations in other languages and formats
    private val alternativeMonthFormats = listOf(
        "de", // Used in some European product labels (22/DE/21)
        "ja", "fe", "mr", "ab", "my", "jn", "jl", "ag", "au", "se", "oc", "no", "dc" // Shorter abbreviations
    )

    // Combine all month formats
    private val allMonthFormats = months + alternativeMonthFormats

    // Month pattern for regex
    private val monthPattern = months.joinToString("|")

    // Extended month pattern including alternative formats
    private val extendedMonthPattern = allMonthFormats.joinToString("|")

    // Common patterns for expiration dates
    private val datePatterns = listOf(
        // MM/DD/YYYY or MM-DD-YYYY
        Pattern.compile("(0[1-9]|1[0-2])[/\\-](0[1-9]|[12][0-9]|3[01])[/\\-](20\\d{2})"),

        // DD/MM/YYYY or DD-MM-YYYY
        Pattern.compile("(0[1-9]|[12][0-9]|3[01])[/\\-](0[1-9]|1[0-2])[/\\-](20\\d{2})"),

        //DD.MM.YY
        Pattern.compile("(0[1-9]|[12][0-9]|3[01])[/\\\\.-](0[1-9]|1[0-2])[/\\\\.-](\\d{2})"),

        // YYYY/MM/DD or YYYY-MM-DD
        Pattern.compile("(20\\d{2})[/\\-](0[1-9]|1[0-2])[/\\-](0[1-9]|[12][0-9]|3[01])"),

        // MM/YYYY or MM-YYYY
        Pattern.compile("(0[1-9]|1[0-2])[/\\-](20\\d{2})"),

        // YYYY/MM or YYYY-MM
        Pattern.compile("(20\\d{2})[/\\-](0[1-9]|1[0-2])"),

        // "MMM DD, YY" and "MMM DD, YYYY" and "MMM D, YY" and "MMM D, YYYY"
        Pattern.compile("(^(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec) [0-3]?[0-9], \\d{2,4}$)", Pattern.CASE_INSENSITIVE),

        // Common text markers followed by date
        Pattern.compile("(?i)(?:exp|exp date|expiry|expiry date|expires|best before|best by|use by|sell by|sel by|se1 by|sell thru)[\\s:]+([0-9]{1,2}[/\\-][0-9]{1,2}[/\\-](?:20)?[0-9]{2})"),

        Pattern.compile(
            "(" +
                    // Format: Dec 06, 2016 or DEC 6, 16
                    "(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[\\s.-]?[0-3]?[0-9][,\\s]+\\d{2,4}" +
                    "|" +
                    // Format: 06 Dec 2016
                    "[0-3]?[0-9][\\s.-]?(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[\\s.,-]+\\d{2,4}" +
                    ")", Pattern.CASE_INSENSITIVE),

        // MMM DD
        Pattern.compile("^((Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)|([1-9]|0[1-9]|1[0-2]))[,]? ?([1-9]|0[1-9]|[12][0-9]|3[01])\$\n",
            Pattern.CASE_INSENSITIVE
        )
    )

    // Common text markers for expiration dates
    private val expiryMarkers = listOf(
        // Standard expiration indicators
        "EXP", "EXP DATE", "EXPIRY", "EXPIRY DATE", "EXPIRES", "EXPIRATION", "EXPIRATION DATE",

        // Best before indicators
        "BEST BEFORE", "BEST BY", "BB", "BBE", "BEST BEFORE END",

        // Use by indicators
        "USE BY", "USE BEFORE", "USE OR FREEZE BY",

        // Sell by indicators
        "SELL BY", "SELL THRU", "SELL THROUGH", "SELL UNTIL", "SB", "SBD",

        // Display until indicators
        "DISPLAY UNTIL", "DISPLAY BY", "DISP BY",

        // Consume by indicators
        "CONSUME BY", "CONSUME BEFORE",

        // Good until indicators
        "GOOD UNTIL", "GOOD THRU", "GOOD THROUGH",

        // Better if used by
        "BETTER IF USED BY", "BETTER IF USED BEFORE",

        // Date indicators
        "MFG DATE", "MANUFACTURED", "PRODUCTION DATE", "DATE OF PRODUCTION", "DOP",
        "PACK DATE", "PACKED ON", "PACKAGED ON", "PKG DATE",

        // Batch and lot indicators (may sometimes contain date info)
        "BATCH", "LOT", "LOT NO", "BATCH NO", "BATCH NUMBER",

        // Freshness indicators
        "GUARANTEED FRESH", "FRESH UNTIL",

        // International variants (common in different countries)
        "DATE", "FECHA", "DATE DE", "VENCIMIENTO", "CONSUMIR", "MINDESTENS HALTBAR BIS",
        "À CONSOMMER AVANT", "SCADENZA", "VERFALLDATUM", "VERFALLSDATUM", "HOUDBAAR TOT",
        "ГОДЕН ДО", "유통기한", "賞味期限", "保质期", "有効期限",

        // Additional indicators found in sample images
        "MEILLEUR AVANT", "BEST IF USED BY", "BEST IF USED BEFORE"
    )

    /**
     * Weight values for date candidates based on different criteria.
     * Higher weights indicate more likely expiration dates.
     */
    private object DateWeight {
        const val CIRCLED_OR_HIGHLIGHTED = 8.0   // Date appears to be circled, highlighted, or emphasized
        const val NEAR_EXPIRY_MARKER = 10.0     // Date is near a text marker like "EXP" or "BEST BEFORE"
        const val AFTER_PACKAGED_DATE = 7.0      // Date comes after a "packaged on" date on the same label
        const val HAS_YEAR_COMPONENT = 5.0       // Date includes a year component
        const val STANDARD_DATE_FORMAT = 3.0     // Date is in a common format (MM/DD/YY, etc.)
        const val FUTURE_DATE = 2.0              // Date is in the future (likely an expiry)
        const val PAST_DATE = -2.0               // Date is in the past (likely not an expiry)
        const val NEAR_NUMERICAL_DATA = -3.0     // Date is near nutrition facts or pricing info
    }

    /**
     * Extracts expiration date from the given text.
     *
     * @param text The text to extract expiration date from
     * @return The extracted expiration date as a string, or empty string if none found
     */
    fun extractExpiryDate(text: String): String {
        if (text.isBlank()) return ""

        // Store all date candidates with their weights
        val dateCandidates = mutableListOf<Pair<String, Double>>()

        // First check for exact patterns globally
        for (pattern in datePatterns) {
            val matcher = pattern.matcher(text)
//            Log.d("ExpirationT", text)
            while (matcher.find()) {
                val date = matcher.group(0)
                var weight = DateWeight.STANDARD_DATE_FORMAT

                // Check if date is near an expiry marker
                if (isNearExpiryMarker(text, matcher.start())) {
                    weight += DateWeight.NEAR_EXPIRY_MARKER
                }

                // Check if date appears to be highlighted (surrounded by special chars)
                if (isHighlightedOrCircled(text, matcher.start(), matcher.end())) {
                    weight += DateWeight.CIRCLED_OR_HIGHLIGHTED
                }

                // Add extra weight if it has a year component
                Log.d("ExpirationY", date.toString())
                if (hasYearComponent(date)) {
                    weight += DateWeight.HAS_YEAR_COMPONENT
                }

                dateCandidates.add(Pair(date, weight))
            }
        }

        // Process line by line for contextual analysis
        val lines = text.split("\n")
        for (line in lines) {
            // Look for lines with expiry markers
            val lowerLine = line.lowercase(Locale.getDefault())
            for (marker in expiryMarkers) {
                if (lowerLine.contains(marker.lowercase(Locale.getDefault()))) {
                    // Extract date from this line
                    val dateCandidate = extractDateFromLine(line)
                    if (dateCandidate.isNotEmpty()) {
                        // Higher weight since it's on a line with an expiry marker
                        dateCandidates.add(Pair(dateCandidate, DateWeight.NEAR_EXPIRY_MARKER + DateWeight.STANDARD_DATE_FORMAT))
                    }
                }
            }

            // Check for labeled sections like "SELL BY" with a date
            if (lowerLine.contains("sell by") || lowerLine.contains("best before") ||
                lowerLine.contains("best by") || lowerLine.contains("use by")) {
                val dateCandidate = extractDateFromLine(line.substringAfter(":", line))
                if (dateCandidate.isNotEmpty()) {
                    dateCandidates.add(Pair(dateCandidate, DateWeight.NEAR_EXPIRY_MARKER * 1.5))
                }
            }

            // If no expiry markers, still extract dates for consideration
            if (!dateCandidates.any { it.first in line }) {
                val dateCandidate = extractDateFromLine(line)
                if (dateCandidate.isNotEmpty()) {
                    var weight = DateWeight.STANDARD_DATE_FORMAT
                    // Lower weight if it's near numerical data like pricing
                    if (line.contains("$") || line.contains("kg") || line.contains("lb") ||
                        line.contains("price") || line.contains("weight") || Regex("\\d+\\.\\d{2}").find(line) != null) {
                        weight += DateWeight.NEAR_NUMERICAL_DATA
                    }
                    dateCandidates.add(Pair(dateCandidate, weight))
                }
            }
        }

        // If we have multiple candidates, select the highest weighted one
        return if (dateCandidates.isNotEmpty()) {
            Log.d("ExpirationC", dateCandidates.toString())
            dateCandidates.maxByOrNull { it.second }?.first ?: ""
        } else {
            // Last resort fallback: try to find any date-like pattern
            for (line in lines) {
                val dateCandidate = extractDateFromLine(line)
                if (dateCandidate.isNotEmpty()) {
                    return dateCandidate
                }
            }
            ""
        }
    }

    /**
     * Check if a date has a year component
     */
    private fun hasYearComponent(date: String): Boolean {
        // Check for year patterns: 4-digit year or 2-digit year with separator
        return date.contains(Regex("\\b(19|20)\\d{2}\\b")) || // 4-digit year like 2023
                date.contains(Regex("[/\\-\\.]\\d{2}\\b"))    // 2-digit year like /23 or -23
    }

    /**
     * Check if date appears to be highlighted by special characters, formatting, or circled
     */
    private fun isHighlightedOrCircled(text: String, startIndex: Int, endIndex: Int): Boolean {
        // Check characters before and after the date for special formatting indicators
        val beforeIndex = maxOf(0, startIndex - 5)
        val afterIndex = minOf(text.length, endIndex + 5)

        val beforeText = text.substring(beforeIndex, startIndex)
        val afterText = text.substring(endIndex, afterIndex)

        // Look for signs of emphasis like asterisks, brackets, etc.
        val emphasisMarkers = listOf("*", "(", ")", "[", "]", "{", "}", "<", ">", "~", "_", "^", "#")
        return emphasisMarkers.any { beforeText.contains(it) && afterText.contains(it) }
    }

    /**
     * Check if a position in text is near an expiry marker
     */
    private fun isNearExpiryMarker(text: String, position: Int): Boolean {
        // Define a window to check for markers (chars before and after position)
        val windowSize = 40
        val startWindow = maxOf(0, position - windowSize)
        val endWindow = minOf(text.length, position + windowSize)

        val windowText = text.substring(startWindow, endWindow).lowercase(Locale.getDefault())

        // Check if any expiry marker is in the window
        for (marker in expiryMarkers) {
            if (windowText.contains(marker.lowercase(Locale.getDefault()))) {
                return true
            }
        }

        return false
    }

    /**
     * Attempts to extract a date from a single line of text.
     */
    private fun extractDateFromLine(line: String): String {
        // Special case for European text month formats (22/DE/21)
        val europeanPattern = Pattern.compile("(0?[1-9]|[12][0-9]|3[01])[/\\-\\.]((?i)$extendedMonthPattern)[/\\-\\.](20\\d{2}|\\d{2})")
        var matcher = europeanPattern.matcher(line)
        if (matcher.find()) {
            return matcher.group(0)
        }

        // First check for numerical patterns (00/00/00, 00-00-00, etc.)
        val numericPattern = Pattern.compile("\\d{1,4}[/\\-\\.]\\d{1,4}([/\\-\\.]\\d{2,4})?")
        matcher = numericPattern.matcher(line)

        if (matcher.find()) {
            val potentialDate = matcher.group(0)
            // Validate if it looks like a real date
            if (isValidDateFormat(potentialDate)) {
                return potentialDate
            }
        }

        // Check for text month patterns
        val textMonthPattern = Pattern.compile("(?i)\\b($monthPattern)\\b[\\s.,]*\\d{1,2}(?:[\\s.,]*\\d{2,4})?")
        matcher = textMonthPattern.matcher(line)

        if (matcher.find()) {
            return matcher.group(0)
        }

        // Check for DD MMM YYYY format
        val dayFirstPattern = Pattern.compile("(?i)\\b\\d{1,2}[\\s.,]*($monthPattern)(?:[\\s.,]*\\d{2,4})?\\b")
        matcher = dayFirstPattern.matcher(line)

        if (matcher.find()) {
            return matcher.group(0)
        }

        // Check for MM, DD or MM DD format
        val monthDayPattern = Pattern.compile("\\b(0?[1-9]|1[0-2])[\\s.,]+(0?[1-9]|[12][0-9]|3[01])\\b")
        matcher = monthDayPattern.matcher(line)

        if (matcher.find()) {
            return matcher.group(0)
        }

        // Check for standalone date patterns that are likely to be expiration dates
        // such as "USE BY DEC21" or "22/DE/21" format
        val standaloneDatePattern = Pattern.compile("(?i)\\b((?:$extendedMonthPattern)\\s?\\d{2}(?:\\d{2})?|\\d{1,2}[/\\-\\.](?:$extendedMonthPattern)[/\\-\\.]\\d{2}(?:\\d{2})?)\\b")
        matcher = standaloneDatePattern.matcher(line)

        if (matcher.find()) {
            return matcher.group(0)
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
        if (parts.size in 2..3 && parts.all { it.all { c -> c.isDigit() } }) {
            // Additional validation could be added here
            return true
        }

        return false
    }

    /**
     * Parse and normalize an extracted date string.
     * This function can be expanded to convert all date formats to a standard format.
     */
    fun normalizeDate(dateStr: String): String {
        // For now, just return the extracted date string
        // In a future enhancement, this could parse various formats and return a standardized one
        return dateStr.trim()
    }
}
