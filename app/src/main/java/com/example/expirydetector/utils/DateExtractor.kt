package com.example.expirydetector.utils

import android.util.Log
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * Enhanced utility class for extracting expiration dates from text.
 * Designed to handle various date formats from food labels.
 */
object DateExtractor {
    private const val TAG = "DateExtractor"

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
    private val monthPattern by lazy {
        try {
            months.joinToString("|")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating month pattern", e)
            "jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec"
        }
    }

    // Extended month pattern including alternative formats
    private val extendedMonthPattern by lazy {
        try {
            allMonthFormats.joinToString("|")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating extended month pattern", e)
            monthPattern
        }
    }

    // Common patterns for expiration dates
    private val datePatterns by lazy {
        createDatePatterns()
    }

    private fun createDatePatterns(): List<Pattern> {
        val patterns = mutableListOf<Pattern>()

        try {
            // MM/DD/YYYY or MM-DD-YYYY
            patterns.add(Pattern.compile("(0?[1-9]|1[0-2])[/\\-\\.](0?[1-9]|[12][0-9]|3[01])[/\\-\\.](20\\d{2}|\\d{2})"))

            // DD/MM/YYYY or DD-MM-YYYY
            patterns.add(Pattern.compile("(0?[1-9]|[12][0-9]|3[01])[/\\-\\.](0?[1-9]|1[0-2])[/\\-\\.](20\\d{2}|\\d{2})"))

            // YYYY/MM/DD or YYYY-MM-DD
            patterns.add(Pattern.compile("(20\\d{2}|\\d{2})[/\\-\\.](0?[1-9]|1[0-2])[/\\-\\.](0?[1-9]|[12][0-9]|3[01])"))

            // MM/YYYY or MM-YYYY
            patterns.add(Pattern.compile("(0?[1-9]|1[0-2])[/\\-\\.](20\\d{2}|\\d{2})"))

            // YYYY/MM or YYYY-MM
            patterns.add(Pattern.compile("(20\\d{2}|\\d{2})[/\\-\\.](0?[1-9]|1[0-2})"))

            // DD.MM.YY format (common in Europe/Australia)
            patterns.add(Pattern.compile("(0?[1-9]|[12][0-9]|3[01])\\.(0?[1-9]|1[0-2])\\.(20\\d{2}|\\d{2})"))

            // MM DD YYYY - With spaces
            patterns.add(Pattern.compile("(0?[1-9]|1[0-2])\\s+(0?[1-9]|[12][0-9]|3[01])\\s+(20\\d{2}|\\d{2})"))

            // DD MM YYYY - With spaces
            patterns.add(Pattern.compile("(0?[1-9]|[12][0-9]|3[01])\\s+(0?[1-9]|1[0-2])\\s+(20\\d{2}|\\d{2})"))

            // Month name formats (case insensitive)
            // MMM D, YYYY or MMM DD, YYYY (Jan 5, 2023 or Jan 05, 2023)
            patterns.add(Pattern.compile("(?i)($monthPattern)[\\s.,]+\\s*(0?[1-9]|[12][0-9]|3[01])(?:[\\s.,]+\\s*(20\\d{2}|\\d{2}))?"))

            // D MMM YYYY or DD MMM YYYY (5 Jan 2023 or 05 Jan 2023)
            patterns.add(Pattern.compile("(?i)(0?[1-9]|[12][0-9]|3[01])[\\s.,]+\\s*($monthPattern)(?:[\\s.,]+\\s*(20\\d{2}|\\d{2}))?"))

            // YYYY MMM D or YYYY MMM DD (2023 Jan 5 or 2023 Jan 05)
            patterns.add(Pattern.compile("(?i)(20\\d{2}|\\d{2})[\\s.,]+\\s*($monthPattern)[\\s.,]+\\s*(0?[1-9]|[12][0-9]|3[01])"))

            // DD/MMM/YY format (e.g., 22/DE/21) - European with text month
            patterns.add(Pattern.compile("(0?[1-9]|[12][0-9]|3[01])[/\\-\\.]((?i)$extendedMonthPattern)[/\\-\\.](20\\d{2}|\\d{2})"))

            // MM, DD or MM DD format (12, 31 or 12 31)
            patterns.add(Pattern.compile("(0?[1-9]|1[0-2])(?:[\\s.,]+)(0?[1-9]|[12][0-9]|3[01])\\b"))

            // Common text markers followed by date
            patterns.add(Pattern.compile("(?i)(?:exp|exp date|expiry|best before|best by|use by|sell by|sell thru|sell through|display until|good until|good thru|consume by|use before|better if used by)[\\s:]+([0-9]{1,2}[/\\-\\.\\s][0-9]{1,2}(?:[/\\-\\.\\s](?:20)?[0-9]{2})?)"))

            // Common text markers followed by text date
            patterns.add(Pattern.compile("(?i)(?:exp|exp date|expiry|best before|best by|use by|sell by|sell thru|sell through|display until|good until|good thru|consume by|use before|better if used by)[\\s:]+(?:($monthPattern)[\\s.,]+\\s*(0?[1-9]|[12][0-9]|3[01])(?:[\\s.,]+\\s*(20\\d{2}|\\d{2}))?))"))

            // Compressed format - markers directly followed by date (like EXP01JAN24 or BB01012024)
            patterns.add(Pattern.compile("(?i)(?:exp|bb|sb|use|sell)(?!\\s|[a-z])(?:(?:0?[1-9]|1[0-2])[/\\-\\.](0?[1-9]|[12][0-9]|3[01])[/\\-\\.](20\\d{2}|\\d{2})|(?:0?[1-9]|[12][0-9]|3[01])[/\\-\\.](0?[1-9]|1[0-2])[/\\-\\.](20\\d{2}|\\d{2})|(0?[1-9]|[12][0-9]|3[01])($monthPattern)(20\\d{2}|\\d{2})|($monthPattern)(0?[1-9]|[12][0-9]|3[01])(20\\d{2}|\\d{2}))"))

            // Julian date format (like 23135 meaning the 135th day of 2023)
            patterns.add(Pattern.compile("(?i)(?:exp|bb|use|mfg)[\\s:]*([2-9]\\d)(?:[\\s:]*)(00[1-9]|0[1-9]\\d|[1-2]\\d\\d|3[0-5]\\d|36[0-6])"))

        } catch (e: PatternSyntaxException) {
            Log.e(TAG, "Error compiling date patterns", e)
            // Add some basic fallback patterns
            patterns.add(Pattern.compile("\\d{1,2}/\\d{1,2}/\\d{2,4}"))
            patterns.add(Pattern.compile("\\d{1,2}-\\d{1,2}-\\d{2,4}"))
            patterns.add(Pattern.compile("\\d{1,2}\\.\\d{1,2}\\.\\d{2,4}"))
        }

        return patterns
    }

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
        "BETTER IF USED BY", "BETTER IF USED BEFORE"
    )

    // List of packaging/manufacturing date markers (not expiration dates)
    private val packagingMarkers = listOf(
        "PACKED ON", "PACKAGING DATE", "PACKAGED ON", "PACKED DATE", "PKD",
        "MFG DATE", "MANUFACTURED ON", "MFD", "PRODUCTION DATE", "PROD DATE",
        "MADE ON", "BOTTLED ON", "CANNED ON", "HARVEST DATE", "PICKED ON"
    )

    // Format strings for date parsing
    private val dateFormats = listOf(
        "MM/dd/yy", "MM/dd/yyyy", "dd/MM/yy", "dd/MM/yyyy", "yyyy/MM/dd",
        "MM-dd-yy", "MM-dd-yyyy", "dd-MM-yy", "dd-MM-yyyy", "yyyy-MM-dd",
        "MM.dd.yy", "MM.dd.yyyy", "dd.MM.yy", "dd.MM.yyyy", "yyyy.MM.dd",
        "MMM dd, yy", "MMM dd, yyyy", "dd MMM yy", "dd MMM yyyy", "yyyy MMM dd",
        "MMMM dd, yy", "MMMM dd, yyyy", "dd MMMM yy", "dd MMMM yyyy", "yyyy MMMM dd",
        "MM/yy", "MM/yyyy", "MMM yy", "MMM yyyy"
    )

    /**
     * Weight values for date candidates based on different criteria.
     * Higher weights indicate more likely expiration dates.
     */
    private object DateWeight {
        const val EXPIRY_MARKER = 10.0     // Date is associated with an expiry marker like "EXP" or "BEST BEFORE"
        const val PACKAGING_MARKER = -5.0  // Date is associated with a packaging marker like "PACKED ON"
        const val NEAR_EXPIRY_MARKER = 5.0 // Date is near a text marker like "EXP" or "BEST BEFORE"
        const val CIRCLED_OR_HIGHLIGHTED = 8.0   // Date appears to be circled, highlighted, or emphasized
        const val AFTER_PACKAGED_DATE = 7.0      // Date comes after a "packaged on" date on the same label
        const val HAS_YEAR_COMPONENT = 5.0       // Date includes a year component
        const val STANDARD_DATE_FORMAT = 3.0     // Date is in a common format (MM/DD/YY, etc.)
        const val FUTURE_DATE = 4.0              // Date is in the future (likely an expiry)
        const val PAST_DATE = -2.0               // Date is in the past (likely not an expiry)
        const val NEAR_NUMERICAL_DATA = -3.0     // Date is near nutrition facts or pricing info
    }

    /**
     * Extracts expiration date from the given text.
     * If multiple dates are present, it selects the latest one.
     *
     * @param text The text to extract expiration date from
     * @return The extracted expiration date as a string, or empty string if none found
     */
    fun extractExpiryDate(text: String): String {
        if (text.isBlank()) return ""

        try {
            // Store all date candidates with their weights
            val dateCandidates = mutableListOf<DateCandidate>()

            // First check for exact patterns globally
            for (pattern in datePatterns) {
                try {
                    val matcher = pattern.matcher(text)
                    while (matcher.find()) {
                        val dateStr = matcher.group(0)
                        var weight = DateWeight.STANDARD_DATE_FORMAT

                        // Check if date is near an expiry marker
                        val isNearExpiry = isNearExpiryMarker(text, matcher.start())
                        if (isNearExpiry) {
                            weight += DateWeight.NEAR_EXPIRY_MARKER
                        }

                        // Check if date is directly associated with an expiry marker
                        val prevText = text.substring(maxOf(0, matcher.start() - 30), matcher.start()).lowercase(Locale.getDefault())
                        if (hasExpiryMarker(prevText)) {
                            weight += DateWeight.EXPIRY_MARKER
                        }

                        // Check if the date is associated with a packaging marker (not expiry)
                        if (hasPackagingMarker(prevText)) {
                            weight += DateWeight.PACKAGING_MARKER
                        }

                        // Check if date appears to be highlighted (surrounded by special chars)
                        if (isHighlightedOrCircled(text, matcher.start(), matcher.end())) {
                            weight += DateWeight.CIRCLED_OR_HIGHLIGHTED
                        }

                        // Add extra weight if it has a year component
                        if (hasYearComponent(dateStr)) {
                            weight += DateWeight.HAS_YEAR_COMPONENT
                        }

                        // Parse the date to determine if it's in the future or past
                        val parsedDate = safeParseDate(dateStr)
                        if (parsedDate != null) {
                            val currentDate = Calendar.getInstance().time

                            // Log the parsed date for debugging
                            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                            Log.d(TAG, "Parsed date: ${sdf.format(parsedDate)}, Original: $dateStr")

                            if (parsedDate.after(currentDate)) {
                                // Future date gets higher weight (more likely an expiry date)
                                weight += DateWeight.FUTURE_DATE
                            } else if (parsedDate.before(currentDate)) {
                                // Past date gets lower weight (less likely an expiry date)
                                // But still consider it, as it could be a recently expired product
                                weight += DateWeight.PAST_DATE
                            }

                            dateCandidates.add(DateCandidate(dateStr, weight, parsedDate))
                        } else {
                            // Couldn't parse the date, but still consider it
                            dateCandidates.add(DateCandidate(dateStr, weight, null))
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing pattern: ${pattern.pattern()}", e)
                    continue
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
                            val weight = DateWeight.NEAR_EXPIRY_MARKER + DateWeight.STANDARD_DATE_FORMAT
                            val parsedDate = safeParseDate(dateCandidate)
                            dateCandidates.add(DateCandidate(dateCandidate, weight, parsedDate))
                        }
                    }
                }

                // Check for labeled sections like "SELL BY" with a date
                if (lowerLine.contains("sell by") || lowerLine.contains("best before") ||
                    lowerLine.contains("best by") || lowerLine.contains("use by")) {
                    val dateCandidate = extractDateFromLine(line.substringAfter(":", line))
                    if (dateCandidate.isNotEmpty()) {
                        val weight = DateWeight.NEAR_EXPIRY_MARKER * 1.5
                        val parsedDate = safeParseDate(dateCandidate)
                        dateCandidates.add(DateCandidate(dateCandidate, weight, parsedDate))
                    }
                }

                // If no expiry markers, still extract dates for consideration
                if (!dateCandidates.any { it.dateString in line }) {
                    val dateCandidate = extractDateFromLine(line)
                    if (dateCandidate.isNotEmpty()) {
                        var weight = DateWeight.STANDARD_DATE_FORMAT
                        // Lower weight if it's near numerical data like pricing
                        if (line.contains("$") || line.contains("kg") || line.contains("lb") ||
                            line.contains("price") || line.contains("weight") || Regex("\\d+\\.\\d{2}").find(line) != null) {
                            weight += DateWeight.NEAR_NUMERICAL_DATA
                        }
                        val parsedDate = safeParseDate(dateCandidate)
                        dateCandidates.add(DateCandidate(dateCandidate, weight, parsedDate))
                    }
                }
            }

            // Log all found date candidates for debugging
            Log.d(TAG, "Found ${dateCandidates.size} date candidates")
            dateCandidates.forEachIndexed { index, candidate ->
                val dateStr = candidate.parsedDate?.let {
                    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(it)
                } ?: "unparsed"
                Log.d(TAG, "Candidate $index: ${candidate.dateString} → $dateStr (weight: ${candidate.weight})")
            }

            // If we have multiple candidates, prioritize by strategy:
            // 1. First check for high-confidence dates (with expiry markers)
            // 2. If multiple high-confidence dates, select the latest one
            // 3. If no high-confidence dates, select the latest future date
            // 4. If no future dates, select the highest weighted date

            if (dateCandidates.isNotEmpty()) {
                // Filter to high-confidence dates (those with expiry markers)
                val highConfidenceDates = dateCandidates.filter { it.weight >= (DateWeight.STANDARD_DATE_FORMAT + DateWeight.NEAR_EXPIRY_MARKER) }

                if (highConfidenceDates.isNotEmpty()) {
                    // From high-confidence dates, select the latest one with a parsed date
                    val parsedDates = highConfidenceDates.filter { it.parsedDate != null }
                    if (parsedDates.isNotEmpty()) {
                        val latestDate = parsedDates.maxByOrNull { it.parsedDate!! }
                        Log.d(TAG, "Selected latest high-confidence date: ${latestDate?.dateString}")
                        return latestDate?.dateString ?: highConfidenceDates.first().dateString
                    } else {
                        // If no parsed dates, return the highest weighted one
                        val highestWeighted = highConfidenceDates.maxByOrNull { it.weight }
                        Log.d(TAG, "Selected highest-weighted high-confidence date: ${highestWeighted?.dateString}")
                        return highestWeighted?.dateString ?: highConfidenceDates.first().dateString
                    }
                }

                // If no high-confidence dates, look for future dates
                val futureDates = dateCandidates.filter {
                    it.parsedDate != null && it.parsedDate.after(Calendar.getInstance().time)
                }

                if (futureDates.isNotEmpty()) {
                    // Select the latest future date (most likely the expiry date)
                    val latestFutureDate = futureDates.maxByOrNull { it.parsedDate!! }
                    Log.d(TAG, "Selected latest future date: ${latestFutureDate?.dateString}")
                    return latestFutureDate?.dateString ?: futureDates.first().dateString
                }

                // If we get here, no clear expiry date was found - use the highest weighted date
                val highestWeighted = dateCandidates.maxByOrNull { it.weight }
                Log.d(TAG, "No clear expiry found, selected highest-weighted date: ${highestWeighted?.dateString}")
                return highestWeighted?.dateString ?: dateCandidates.first().dateString
            }

            // Last resort fallback: try to find any date-like pattern
            for (line in lines) {
                val dateCandidate = extractDateFromLine(line)
                if (dateCandidate.isNotEmpty()) {
                    Log.d(TAG, "Fallback: found date-like pattern: $dateCandidate")
                    return dateCandidate
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting expiry date", e)
        }

        return ""
    }

    /**
     * Safe wrapper around parseDate to handle exceptions
     */
    private fun safeParseDate(dateStr: String): Date? {
        return try {
            parseDate(dateStr)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing date: $dateStr", e)
            null
        }
    }

    /**
     * Attempts to parse a date string into a Date object using various formats
     */
    private fun parseDate(dateStr: String): Date? {
        val normalizedDate = dateStr.trim().lowercase(Locale.getDefault())
        Log.d(TAG, "Attempting to parse date: $normalizedDate")

        // Try each supported date format
        for (format in dateFormats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.US)
                sdf.isLenient = true // Allow some flexibility in parsing
                val parsedDate = sdf.parse(normalizedDate)
                if (parsedDate != null) {
                    // Verify year is reasonable (not 0000 or 9999)
                    val calendar = Calendar.getInstance()
                    calendar.time = parsedDate
                    val year = calendar.get(Calendar.YEAR)

                    if (year in 1900..2100) {
                        Log.d(TAG, "Successfully parsed with format $format: ${sdf.format(parsedDate)}")
                        return parsedDate
                    } else {
                        Log.d(TAG, "Rejected parsed date with unreasonable year $year: $normalizedDate")
                    }
                }
            } catch (e: ParseException) {
                // Continue to next format
                continue
            }
        }

        // Special case for European formats with text month (22/DE/21)
        if (isEuropeanTextMonthFormat(normalizedDate)) {
            try {
                val parts = normalizedDate.split(Regex("[/\\-.]"))
                if (parts.size == 3) {
                    // Convert text month to number
                    val day = parts[0].toIntOrNull() ?: 1
                    val month = textMonthToNumber(parts[1])
                    var year = parts[2].toIntOrNull() ?: 2000

                    // Adjust 2-digit year to 4-digit
                    if (year < 100) {
                        year += if (year < 50) 2000 else 1900
                    }

                    // Create a calendar with the date
                    val calendar = Calendar.getInstance()
                    calendar.set(year, month - 1, day) // Month is 0-based in Calendar

                    Log.d(TAG, "Parsed European date format: $normalizedDate to ${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)}")
                    return calendar.time
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing European date format", e)
                // Fallback to null if parsing fails
            }
        }

        // Special case for Julian dates (e.g., 23135 = 2023, day 135)
        if (Regex("\\d{5}").matches(normalizedDate)) {
            try {
                val year = normalizedDate.substring(0, 2).toInt() + 2000
                val dayOfYear = normalizedDate.substring(2).toInt()

                val calendar = Calendar.getInstance()
                calendar.clear()
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.DAY_OF_YEAR, dayOfYear)

                Log.d(TAG, "Parsed Julian date: $normalizedDate to ${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)}")
                return calendar.time
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing Julian date", e)
                // Fallback to null if parsing fails
            }
        }

        // Handle dates with just month and day by adding current year or next year
        if (Regex("\\d{1,2}[/\\-\\s.]\\d{1,2}").matches(normalizedDate) ||
            Regex("\\w{3}\\s+\\d{1,2}").matches(normalizedDate) ||
            Regex("\\d{1,2}\\s+\\w{3}").matches(normalizedDate)) {

            try {
                // Try adding current year and check if date is in the future
                val currentYear = Calendar.getInstance().get(Calendar.YEAR)

                // Try formats with added year
                val augmentedFormats = listOf(
                    "MM/dd/$currentYear", "dd/MM/$currentYear",
                    "MM-dd-$currentYear", "dd-MM-$currentYear",
                    "MMM dd $currentYear", "dd MMM $currentYear"
                )

                for (format in augmentedFormats) {
                    try {
                        val sdf = SimpleDateFormat(format, Locale.US)
                        sdf.isLenient = true
                        val parsedDate = sdf.parse(normalizedDate)

                        if (parsedDate != null) {
                            val currentDate = Calendar.getInstance().time

                            // If date is in the past, try next year
                            if (parsedDate.before(currentDate)) {
                                val nextYear = currentYear + 1
                                val nextYearFormat = format.replace("$currentYear", "$nextYear")
                                val nextYearSdf = SimpleDateFormat(nextYearFormat, Locale.US)
                                val nextYearDate = nextYearSdf.parse(normalizedDate)

                                if (nextYearDate != null) {
                                    Log.d(TAG, "Parsed date with next year: $normalizedDate to ${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(nextYearDate)}")
                                    return nextYearDate
                                }
                            } else {
                                Log.d(TAG, "Parsed date with current year: $normalizedDate to ${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(parsedDate)}")
                                return parsedDate
                            }
                        }
                    } catch (e: ParseException) {
                        // Continue to next format
                        continue
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error adding year to date", e)
            }
        }

        Log.d(TAG, "Failed to parse date: $normalizedDate")
        return null
    }

    /**
     * Convert text month abbreviation to its numerical value (1-12)
     */
    private fun textMonthToNumber(month: String): Int {
        val normalizedMonth = month.lowercase(Locale.getDefault())
        return when {
            normalizedMonth.startsWith("jan") || normalizedMonth == "ja" -> 1
            normalizedMonth.startsWith("feb") || normalizedMonth == "fe" -> 2
            normalizedMonth.startsWith("mar") || normalizedMonth == "mr" -> 3
            normalizedMonth.startsWith("apr") || normalizedMonth == "ab" -> 4
            normalizedMonth.startsWith("may") || normalizedMonth == "my" -> 5
            normalizedMonth.startsWith("jun") || normalizedMonth == "jn" -> 6
            normalizedMonth.startsWith("jul") || normalizedMonth == "jl" -> 7
            normalizedMonth.startsWith("aug") || normalizedMonth == "ag" -> 8
            normalizedMonth.startsWith("sep") || normalizedMonth == "se" -> 9
            normalizedMonth.startsWith("oct") || normalizedMonth == "oc" -> 10
            normalizedMonth.startsWith("nov") || normalizedMonth == "no" -> 11
            normalizedMonth.startsWith("dec") || normalizedMonth == "de" || normalizedMonth == "dc" -> 12
            else -> 1 // Default to January if unknown
        }
    }

    /**
     * Check if string is in European date format with text month (22/DE/21)
     */
    private fun isEuropeanTextMonthFormat(dateStr: String): Boolean {
        val pattern = Regex("(\\d{1,2})[/\\-\\.](\\w{2})[/\\-\\.](\\d{2})")
        val match = pattern.find(dateStr) ?: return false

        // Make sure middle part is a month abbreviation
        val monthPart = match.groupValues[2].lowercase(Locale.getDefault())
        return alternativeMonthFormats.contains(monthPart)
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
     * Check if text has an expiry marker
     */
    private fun hasExpiryMarker(text: String): Boolean {
        for (marker in expiryMarkers) {
            if (text.contains(marker.lowercase(Locale.getDefault()))) {
                return true
            }
        }
        return false
    }

    /**
     * Check if text has a packaging marker (not an expiry date)
     */
    private fun hasPackagingMarker(text: String): Boolean {
        for (marker in packagingMarkers) {
            if (text.contains(marker.lowercase(Locale.getDefault()))) {
                return true
            }
        }
        return false
    }

    /**
     * Check if date appears to be highlighted by special characters, formatting, or circled
     */
    private fun isHighlightedOrCircled(text: String, startIndex: Int, endIndex: Int): Boolean {
        try {
            // Check characters before and after the date for special formatting indicators
            val beforeIndex = maxOf(0, startIndex - 5)
            val afterIndex = minOf(text.length, endIndex + 5)

            val beforeText = text.substring(beforeIndex, startIndex)
            val afterText = text.substring(endIndex, afterIndex)

            // Look for signs of emphasis like asterisks, brackets, etc.
            val emphasisMarkers = listOf("*", "(", ")", "[", "]", "{", "}", "<", ">", "~", "_", "^", "#")
            return emphasisMarkers.any { beforeText.contains(it) && afterText.contains(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for highlighted date", e)
            return false
        }
    }

    /**
     * Check if a position in text is near an expiry marker
     */
    private fun isNearExpiryMarker(text: String, position: Int): Boolean {
        try {
            // Define a window to check for markers (chars before position)
            val windowSize = 40
            val startWindow = maxOf(0, position - windowSize)
            val endWindow = position

            val windowText = text.substring(startWindow, endWindow).lowercase(Locale.getDefault())

            // Check if any expiry marker is in the window
            for (marker in expiryMarkers) {
                if (windowText.contains(marker.lowercase(Locale.getDefault()))) {
                    return true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for nearby expiry marker", e)
        }

        return false
    }

    /**
     * Attempts to extract a date from a single line of text.
     */
    private fun extractDateFromLine(line: String): String {
        try {
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
            val standaloneDatePattern = Pattern.compile("(?i)\\b((?:$monthPattern)\\s?\\d{2}(?:\\d{2})?|\\d{1,2}[/\\-\\.](?:$monthPattern)[/\\-\\.]\\d{2}(?:\\d{2})?)\\b")
            matcher = standaloneDatePattern.matcher(line)

            if (matcher.find()) {
                return matcher.group(0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting date from line: $line", e)
        }

        return ""
    }

    /**
     * Basic validation to check if a string looks like a date.
     */
    private fun isValidDateFormat(dateStr: String): Boolean {
        try {
            // If it's just numbers with separators, it's probably a date
            val parts = dateStr.split(Regex("[/\\-.]"))

            // Check if we have 2 or 3 parts, as most dates are MM/YY, MM/DD/YY, etc.
            if (parts.size in 2..3 && parts.all { it.all { c -> c.isDigit() } }) {
                // Additional validation could be added here
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error validating date format", e)
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

    /**
     * Internal class to represent a date candidate with its weight and parsed date
     */
    private data class DateCandidate(
        val dateString: String,
        val weight: Double,
        val parsedDate: Date?
    )
}
