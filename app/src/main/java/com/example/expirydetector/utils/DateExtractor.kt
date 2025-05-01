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

    // Cache for successful date extractions to prevent "flickering" dates
    private var lastSuccessfulExtraction: String = ""
    private var lastExtractionTimestamp: Long = 0
    private const val EXTRACTION_CACHE_DURATION = 5000 // 5 seconds

    // Confidence tracking for stable date selection
    private val confidenceHistory = mutableMapOf<String, Int>()
    private const val CONFIDENCE_THRESHOLD = 3  // Number of consecutive detections needed to switch dates
    private const val CONFIDENCE_MAX = 10       // Maximum confidence to prevent unbounded growth

    /**
     * Updates the last successful extraction cache with a new date.
     * Manages confidence tracking to ensure stable date selection.
     */
    private fun updateDateConfidenceCache(date: String) {
        val currentTime = System.currentTimeMillis()

        // Reset confidence for competing dates
        if (date != lastSuccessfulExtraction) {
            // Increase confidence for this date
            val currentConfidence = confidenceHistory.getOrDefault(date, 0)
            confidenceHistory[date] = minOf(currentConfidence + 1, CONFIDENCE_MAX)

            // Decrease confidence for previous date
            if (lastSuccessfulExtraction.isNotBlank()) {
                val prevConfidence = confidenceHistory.getOrDefault(lastSuccessfulExtraction, 0)
                if (prevConfidence > 0) {
                    confidenceHistory[lastSuccessfulExtraction] = prevConfidence - 1
                }
            }

            // Only switch the date if we have sufficient confidence in the new date
            if (confidenceHistory.getOrDefault(date, 0) >= CONFIDENCE_THRESHOLD) {
                Log.d(TAG, "Switching date from '$lastSuccessfulExtraction' to '$date' with confidence ${confidenceHistory[date]}")
                lastSuccessfulExtraction = date
                lastExtractionTimestamp = currentTime
            } else {
                Log.d(TAG, "Detected new date '$date' but keeping '$lastSuccessfulExtraction' until confidence threshold reached " +
                        "(current: ${confidenceHistory.getOrDefault(date, 0)}/$CONFIDENCE_THRESHOLD)")
            }
        } else {
            // Same date as before, reinforce confidence
            val currentConfidence = confidenceHistory.getOrDefault(date, 0)
            confidenceHistory[date] = minOf(currentConfidence + 1, CONFIDENCE_MAX)
            lastExtractionTimestamp = currentTime
        }

        // Cleanup old entries to prevent memory leaks
        if (confidenceHistory.size > 5) {
            val keysToRemove = confidenceHistory.filter { it.value == 0 }.keys
            keysToRemove.forEach { confidenceHistory.remove(it) }
        }
    }

    /**
     * Gets the fallback date from cache if available and not expired
     */
    private fun getFallbackDate(): String {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastExtraction = currentTime - lastExtractionTimestamp

        if (lastSuccessfulExtraction.isNotBlank() && timeSinceLastExtraction < EXTRACTION_CACHE_DURATION) {
            return lastSuccessfulExtraction
        }

        return ""
    }

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

    // Common patterns to avoid (prices, product codes, etc.)
    private val avoidPatterns by lazy {
        try {
            listOf(
                // Price patterns
                Pattern.compile("\\$\\d+\\.\\d{2}"),       // $12.99 format
                Pattern.compile("\\d+\\.\\d{2}/lb"),       // 5.99/lb format
                Pattern.compile("\\$\\d+\\.\\d{2}/lb"),    // $5.99/lb format
                Pattern.compile("\\d+\\.\\d{2}\\s*/lb"),   // 5.99 /lb format
                Pattern.compile("price.*\\$\\d+\\.\\d{2}"), // price $5.99 format

                // Product codes, PLUs, and other numeric patterns
                Pattern.compile("\\d{3}-\\d{2}"),          // 296-01 format (common PLU code)
                Pattern.compile("PLU\\s*\\d{4}"),          // PLU 1234 format
                Pattern.compile("UPC\\s*\\d+"),            // UPC codes
                Pattern.compile("SKU\\s*\\d+"),            // SKU codes
                Pattern.compile("\\d+:\\d{2}[AP]M"),       // 10:34AM time format
                Pattern.compile("Item\\s*#\\s*\\d+"),      // Item # codes
                Pattern.compile("Lot\\s*\\d+")             // Lot numbers
            )
        } catch (e: PatternSyntaxException) {
            Log.e(TAG, "Error compiling avoid patterns", e)
            emptyList<Pattern>()
        }
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
            patterns.add(Pattern.compile("(20\\d{2}|\\d{2})[/\\-\\.](0?[1-9]|1[0-2])"))

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

            // Common text markers followed by date - this is the most reliable pattern for expiry dates
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
        "USE BY", "USE BEFORE", "USE OR FREEZE BY", "USE OR FREEZE",

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

    // Price and weight related terms that should lower confidence in nearby dates
    private val priceTerms = listOf(
        "PRICE", "TOTAL PRICE", "REG PRICE", "SALE PRICE", "UNIT PRICE",
        "NEW PRICE", "COST", "TOTAL COST", "YOU SAVE", "SAVE",
        "$", "$/LB", "$/KG", "/LB", "/KG",
        "PLU", "UPC", "SKU"
    )

    // Weight related terms to help identify weight values
    private val weightTerms = listOf(
        "NET WT", "NET WT.", "NET WEIGHT", "NET WT LBS", "NET WT. LBS",
        "WEIGHT", "NET", "WT", "LBS", "LB", "KG", "G", "OZ",
        "TARE", "NET WT/CT", "GRAMS", "OUNCES", "POUNDS"
    )

    // Product code related terms to avoid
    private val productCodeTerms = listOf(
        "PLU", "UPC", "SKU", "ITEM #", "ITEM NO", "PRODUCT ID", "ID",
        "CODE", "LOT", "BATCH", "SERIAL", "PART", "REF", "REFERENCE"
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
        const val EXPIRY_MARKER = 15.0     // Date is associated with an expiry marker like "EXP" or "BEST BEFORE"
        const val PACKAGING_MARKER = -10.0  // Date is associated with a packaging marker like "PACKED ON"
        const val NEAR_EXPIRY_MARKER = 8.0 // Date is near a text marker like "EXP" or "BEST BEFORE"
        const val CIRCLED_OR_HIGHLIGHTED = 5.0   // Date appears to be circled, highlighted, or emphasized
        const val AFTER_PACKAGED_DATE = 3.0      // Date comes after a "packaged on" date on the same label
        const val HAS_YEAR_COMPONENT = 4.0       // Date includes a year component
        const val STANDARD_DATE_FORMAT = 2.0     // Date is in a common format (MM/DD/YY, etc.)
        const val FUTURE_DATE = 7.0              // Date is in the future (likely an expiry)
        const val PAST_DATE = -5.0               // Date is in the past (likely not an expiry)
        const val NEAR_PRICE = -12.0            // Date is near price information (likely not an expiry)
        const val IS_PRICE_FORMAT = -20.0       // Date matches a price pattern (definitely not an expiry)
        const val NEAR_PROCESSING_DATE = -8.0   // Date is near processing/packaging information
        const val SELL_BY_MARKER = 12.0         // Specifically marked as "Sell By" (common on meat labels)
        const val PRODUCT_CODE_FORMAT = -25.0   // Matches product code format (definitely not a date)
        const val NEAR_PRODUCT_CODE = -15.0     // Near a product code term like PLU, UPC, etc.
    }

    /**
     * Extracts expiration date from the given text.
     * If multiple dates are present, it selects the latest one.
     *
     * @param text The text to extract expiration date from
     * @return The extracted expiration date as a string, or empty string if none found
     */
    fun extractExpiryDate(text: String): String {
        if (text.isBlank()) {
            // If no text provided, use cached date if available
            return getFallbackDate()
        }

        try {
            Log.d(TAG, "Starting date extraction on text: ${text.take(100)}...")

            // Store all date candidates with their weights
            val dateCandidates = mutableListOf<DateCandidate>()

            // Look for specific patterns like "Sell By: MM/DD/YY" first (common in meat labels)
            // This is the most reliable pattern
            val sellByPattern = Pattern.compile("(?i)sell\\s*by[\\s:]*([0-9]{1,2}[/\\-\\.][0-9]{1,2}[/\\-\\.][0-9]{2,4})")
            val sellByMatcher = sellByPattern.matcher(text)
            if (sellByMatcher.find()) {
                val dateStr = sellByMatcher.group(1)
                if (dateStr != null) {
                    var weight = DateWeight.EXPIRY_MARKER + DateWeight.SELL_BY_MARKER
                    val parsedDate = safeParseDate(dateStr)

                    if (parsedDate != null) {
                        // If this sell-by date is in the future, give it even more weight
                        val currentDate = Calendar.getInstance().time
                        if (parsedDate.after(currentDate)) {
                            weight += DateWeight.FUTURE_DATE
                        }

                        dateCandidates.add(DateCandidate(dateStr, weight, parsedDate))
                        Log.d(TAG, "Found high-confidence Sell By date: $dateStr with weight $weight")
                    } else {
                        // Even if we couldn't parse it, it's still a high-confidence date
                        dateCandidates.add(DateCandidate(dateStr, weight, null))
                    }
                }
            }

            // Look for "USE OR FREEZE BY:" pattern (common in poultry labels)
            val useOrFreezeByPattern = Pattern.compile("(?i)USE\\s+OR\\s+FREEZE\\s+BY\\s*:?\\s*([0-9]{1,2}[/\\-\\.][0-9]{1,2}[/\\-\\.][0-9]{2,4})")
            val useOrFreezeMatcher = useOrFreezeByPattern.matcher(text)
            if (useOrFreezeMatcher.find()) {
                val dateStr = useOrFreezeMatcher.group(1)
                if (dateStr != null) {
                    var weight = DateWeight.EXPIRY_MARKER + 20.0 // Even higher weight than sell-by
                    val parsedDate = safeParseDate(dateStr)

                    if (parsedDate != null) {
                        // If this date is in the future, give it even more weight
                        val currentDate = Calendar.getInstance().time
                        if (parsedDate.after(currentDate)) {
                            weight += DateWeight.FUTURE_DATE
                        }

                        dateCandidates.add(DateCandidate(dateStr, weight, parsedDate))
                        Log.d(TAG, "Found high-confidence USE OR FREEZE BY date: $dateStr with weight $weight")
                    } else {
                        // Even if we couldn't parse it, it's still a high-confidence date
                        dateCandidates.add(DateCandidate(dateStr, weight, null))
                    }
                }
            }

            // Special handling for chicken label pattern "USE OR FREEZE BY:" followed by MM/DD/YY on next line
            val textLines = text.split("\n")
            for (i in 0 until textLines.size - 1) {
                val line = textLines[i].trim()
                val nextLine = textLines[i+1].trim()

                if (line.uppercase(Locale.getDefault()).contains("USE OR FREEZE BY")) {
                    // Check if next line contains a date in MM/DD/YY format
                    val datePattern = Pattern.compile("([0-9]{1,2}[/\\-\\.][0-9]{1,2}[/\\-\\.][0-9]{2,4})")
                    val dateMatcher = datePattern.matcher(nextLine)

                    if (dateMatcher.find()) {
                        val dateStr = dateMatcher.group(1)
                        if (dateStr != null) {
                            var weight = DateWeight.EXPIRY_MARKER + 25.0 // Highest confidence
                            val parsedDate = safeParseDate(dateStr)

                            if (parsedDate != null) {
                                dateCandidates.add(DateCandidate(dateStr, weight, parsedDate))
                                Log.d(TAG, "Found USE OR FREEZE BY date on next line: $dateStr with weight $weight")
                            } else {
                                dateCandidates.add(DateCandidate(dateStr, weight, null))
                            }
                        }
                    }
                }
            }

            // Special handling for grocery format with "Sell Thru" and abbreviated month with dot notation: "Apr. 29.25"
            for (i in 0 until textLines.size - 1) {
                val line = textLines[i].trim().lowercase(Locale.getDefault())
                val nextLine = textLines[i+1].trim()

                if (line.contains("sell thru")) {
                    // Check for common abbreviated month format on next line: "Apr. 29.25" or similar
                    val abbrMonthPattern = Pattern.compile("(?i)([A-Za-z]{3})\\s*\\.?\\s+(\\d{1,2})\\s*\\.\\s*(\\d{1,2})")
                    val abbrMonthMatcher = abbrMonthPattern.matcher(nextLine)

                    if (abbrMonthMatcher.find()) {
                        val monthStr = abbrMonthMatcher.group(1)
                        val dayStr = abbrMonthMatcher.group(2)
                        val yearPart = abbrMonthMatcher.group(3)

                        if (monthStr != null && dayStr != null && yearPart != null) {
                            // Format the date string in a standard way
                            val formattedDate = "$monthStr $dayStr, 20$yearPart"
                            Log.d(TAG, "Found abbreviated month format after Sell Thru: $formattedDate")

                            var weight = DateWeight.EXPIRY_MARKER + 30.0 // Very high confidence
                            val parsedDate = safeParseDate(formattedDate)

                            if (parsedDate != null) {
                                dateCandidates.add(DateCandidate(formattedDate, weight, parsedDate))
                                Log.d(TAG, "Successfully parsed abbreviated month format: $formattedDate -> $parsedDate")
                            } else {
                                // Try alternative parsing by converting month abbreviation
                                try {
                                    val monthNum = monthNameToNumber(monthStr)
                                    val day = dayStr.toInt()
                                    val year = 2000 + yearPart.toInt()

                                    val calendar = Calendar.getInstance()
                                    calendar.set(year, monthNum - 1, day)

                                    dateCandidates.add(DateCandidate(formattedDate, weight, calendar.time))
                                    Log.d(TAG, "Parsed abbreviated month with calendar: $monthStr $dayStr, $yearPart -> ${calendar.time}")
                                } catch (e: Exception) {
                                    // Still add as a candidate even if parsing fails
                                    dateCandidates.add(DateCandidate(formattedDate, weight, null))
                                    Log.e(TAG, "Could not parse abbreviated month format: $formattedDate", e)
                                }
                            }
                        }
                    }
                }
            }

            // Check for abbreviated month format directly in the text (not just after Sell Thru)
            val abbrMonthGlobalPattern = Pattern.compile("(?i)([A-Za-z]{3})\\s*\\.?\\s+(\\d{1,2})\\s*\\.\\s*(\\d{1,2})")
            val abbrMonthGlobalMatcher = abbrMonthGlobalPattern.matcher(text)

            while (abbrMonthGlobalMatcher.find()) {
                val monthStr = abbrMonthGlobalMatcher.group(1)
                val dayStr = abbrMonthGlobalMatcher.group(2)
                val yearPart = abbrMonthGlobalMatcher.group(3)

                if (monthStr != null && dayStr != null && yearPart != null) {
                    // Format the date string in a standard way
                    val formattedDate = "$monthStr $dayStr, 20$yearPart"
                    Log.d(TAG, "Found general abbreviated month format: $formattedDate")

                    // Context check - if it's in a line with "Sell Thru", "Best Before", etc. it gets higher weight
                    val position = abbrMonthGlobalMatcher.start()
                    val lineStart = maxOf(0, text.lastIndexOf('\n', position) + 1)
                    val lineEnd = minOf(text.length, text.indexOf('\n', position).let { if (it == -1) text.length else it })
                    val line = text.substring(lineStart, lineEnd).lowercase(Locale.getDefault())

                    var weight = DateWeight.STANDARD_DATE_FORMAT + DateWeight.HAS_YEAR_COMPONENT

                    // Increase weight if it's on a line with expiry markers
                    if (line.contains("sell thru") || line.contains("sell by") ||
                        line.contains("use by") || line.contains("best before")) {
                        weight += DateWeight.EXPIRY_MARKER
                    }

                    val parsedDate = safeParseDate(formattedDate)

                    if (parsedDate != null) {
                        // If date is in future, increase weight
                        val currentDate = Calendar.getInstance().time
                        if (parsedDate.after(currentDate)) {
                            weight += DateWeight.FUTURE_DATE
                        }

                        dateCandidates.add(DateCandidate(formattedDate, weight, parsedDate))
                        Log.d(TAG, "Parsed general abbreviated month format: $formattedDate -> $parsedDate with weight $weight")
                    } else {
                        // Try alternative parsing
                        try {
                            val monthNum = monthNameToNumber(monthStr)
                            val day = dayStr.toInt()
                            val year = 2000 + yearPart.toInt()

                            val calendar = Calendar.getInstance()
                            calendar.set(year, monthNum - 1, day)

                            dateCandidates.add(DateCandidate(formattedDate, weight, calendar.time))
                            Log.d(TAG, "Parsed general abbreviated month with calendar: $monthStr $dayStr, $yearPart -> ${calendar.time}")
                        } catch (e: Exception) {
                            dateCandidates.add(DateCandidate(formattedDate, weight, null))
                            Log.e(TAG, "Could not parse general abbreviated month format: $formattedDate", e)
                        }
                    }
                }
            }

            // Identify price sections to avoid extracting prices as dates
            val priceLines = identifyPriceLines(text)
            Log.d(TAG, "Identified price lines: $priceLines")

            // Identify product code sections
            val productCodeLines = identifyProductCodeLines(text)
            Log.d(TAG, "Identified product code lines: $productCodeLines")

            // First check for patterns that we want to avoid (product codes, PLUs, etc.)
            val avoidList = mutableListOf<Pair<String, Int>>() // (match, position)
            for (pattern in avoidPatterns) {
                try {
                    val matcher = pattern.matcher(text)
                    while (matcher.find()) {
                        val match = matcher.group(0)
                        val position = matcher.start()
                        avoidList.add(Pair(match, position))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error finding patterns to avoid", e)
                    continue
                }
            }

            // Check for exact date patterns globally
            for (pattern in datePatterns) {
                try {
                    val matcher = pattern.matcher(text)
                    while (matcher.find()) {
                        val dateStr = matcher.group(0)
                        val position = matcher.start()

                        // Check if this match should be avoided
                        val shouldAvoid = avoidList.any {
                            val avoidStr = it.first
                            val avoidPos = it.second
                            dateStr.contains(avoidStr) ||
                                    (position >= avoidPos && position <= avoidPos + avoidStr.length) ||
                                    (avoidPos >= position && avoidPos <= position + dateStr.length)
                        }

                        if (shouldAvoid) {
                            Log.d(TAG, "Skipping potential date: $dateStr (matches an avoid pattern)")
                            continue
                        }

                        // Skip things that look like product codes (XXX-YY format)
                        if (isProductCode(dateStr, text, position)) {
                            Log.d(TAG, "Skipping product code-like date: $dateStr")
                            continue
                        }

                        // Skip if the "date" is actually a price
                        if (isPriceFormat(dateStr, text, position)) {
                            Log.d(TAG, "Skipping price-like date: $dateStr")
                            continue
                        }

                        var weight = DateWeight.STANDARD_DATE_FORMAT

                        // Check if date is on a product code line
                        if (isOnProductCodeLine(text, position, productCodeLines)) {
                            weight += DateWeight.NEAR_PRODUCT_CODE
                            Log.d(TAG, "Date $dateStr is near product code, reducing weight")
                            continue // Skip entirely if it's on a product code line
                        }

                        // Check if date is on a price line
                        if (isOnPriceLine(text, position, priceLines)) {
                            weight += DateWeight.NEAR_PRICE
                            Log.d(TAG, "Date $dateStr is near price info, reducing weight")
                        }

                        // Check if date is near an expiry marker
                        val isNearExpiry = isNearExpiryMarker(text, position)
                        if (isNearExpiry) {
                            weight += DateWeight.NEAR_EXPIRY_MARKER
                        }

                        // Check if date is directly associated with an expiry marker
                        val prevText = text.substring(maxOf(0, position - 30), position).lowercase(Locale.getDefault())
                        if (hasExpiryMarker(prevText)) {
                            weight += DateWeight.EXPIRY_MARKER
                        }

                        // Check if the date is associated with a packaging marker (not expiry)
                        if (hasPackagingMarker(prevText)) {
                            weight += DateWeight.PACKAGING_MARKER
                        }

                        // Check if date appears to be highlighted (surrounded by special chars)
                        if (isHighlightedOrCircled(text, position, matcher.end())) {
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
                            Log.d(TAG, "Parsed date: ${sdf.format(parsedDate)}, Original: $dateStr, Weight: $weight")

                            if (parsedDate.after(currentDate)) {
                                // Future date gets higher weight (more likely an expiry date)
                                weight += DateWeight.FUTURE_DATE
                            } else if (parsedDate.before(currentDate)) {
                                // Past date gets lower weight (less likely an expiry date)
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
            val analysisLines = text.split("\n")
            for (line in analysisLines) {
                // Skip price lines and product code lines for this analysis
                if (isPriceLine(line) || isProductCodeLine(line)) {
                    continue
                }

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
                        var weight = DateWeight.NEAR_EXPIRY_MARKER * 1.5
                        // Special case for "Sell By" on meat labels - very high confidence
                        if (lowerLine.contains("sell by")) {
                            weight += DateWeight.SELL_BY_MARKER
                        }
                        val parsedDate = safeParseDate(dateCandidate)
                        dateCandidates.add(DateCandidate(dateCandidate, weight, parsedDate))
                    }
                }

                // If no expiry markers, still extract dates for consideration
                if (!dateCandidates.any { it.dateString in line }) {
                    val dateCandidate = extractDateFromLine(line)
                    if (dateCandidate.isNotEmpty() &&
                        !isPriceFormat(dateCandidate, line, line.indexOf(dateCandidate)) &&
                        !isProductCode(dateCandidate, line, line.indexOf(dateCandidate))) {

                        var weight = DateWeight.STANDARD_DATE_FORMAT
                        // Lower weight if it's near numerical data like pricing
                        if (line.contains("$") || line.contains("kg") || line.contains("lb") ||
                            line.contains("price") || line.contains("weight") || Regex("\\d+\\.\\d{2}").find(line) != null) {
                            weight += DateWeight.NEAR_PRICE
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
                val highConfidenceDates = dateCandidates.filter {
                    it.weight >= (DateWeight.STANDARD_DATE_FORMAT + DateWeight.NEAR_EXPIRY_MARKER) &&
                            it.weight > 0 // Make sure we don't include negative weights (like prices)
                }

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
                    it.parsedDate != null &&
                            it.parsedDate.after(Calendar.getInstance().time) &&
                            it.weight > 0 // Ensure positive weight to avoid prices
                }

                if (futureDates.isNotEmpty()) {
                    // Select the latest future date (most likely the expiry date)
                    val latestFutureDate = futureDates.maxByOrNull { it.parsedDate!! }
                    Log.d(TAG, "Selected latest future date: ${latestFutureDate?.dateString}")
                    return latestFutureDate?.dateString ?: futureDates.first().dateString
                }

                // If we get here, no clear expiry date was found - use the highest weighted date
                // But avoid negative weights which are likely price-related
                val positiveWeightDates = dateCandidates.filter { it.weight > 0 }
                if (positiveWeightDates.isNotEmpty()) {
                    val highestWeighted = positiveWeightDates.maxByOrNull { it.weight }
                    val selectedDate = highestWeighted?.dateString ?: positiveWeightDates.first().dateString
                    Log.d(TAG, "No clear expiry found, selected highest-weighted positive date: $selectedDate")
                    updateDateConfidenceCache(selectedDate)
                    return selectedDate
                }

                // If even that fails, take the overall highest weighted
                val highestWeighted = dateCandidates.maxByOrNull { it.weight }
                val selectedDate = highestWeighted?.dateString ?: dateCandidates.first().dateString
                Log.d(TAG, "No positive weight dates, selected overall highest-weighted date: $selectedDate")
                updateDateConfidenceCache(selectedDate)
                return selectedDate
            }

            // Last resort fallback: try to find any date-like pattern
            for (line in analysisLines) {
                // Skip price lines and product code lines
                if (isPriceLine(line) || isProductCodeLine(line)) {
                    continue
                }

                val dateCandidate = extractDateFromLine(line)
                if (dateCandidate.isNotEmpty() &&
                    !isPriceFormat(dateCandidate, line, line.indexOf(dateCandidate)) &&
                    !isProductCode(dateCandidate, line, line.indexOf(dateCandidate))) {

                    Log.d(TAG, "Fallback: found date-like pattern: $dateCandidate")
                    updateDateConfidenceCache(dateCandidate)
                    return dateCandidate
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting expiry date", e)
        }

        // If we couldn't extract a date, try using the cached date as fallback
        val fallbackDate = getFallbackDate()
        if (fallbackDate.isNotBlank()) {
            Log.d(TAG, "Using fallback date from cache: $fallbackDate")
            return fallbackDate
        }

        return ""
    }

    /**
     * Checks if a date string appears to be a product code
     */
    private fun isProductCode(dateStr: String, context: String, position: Int): Boolean {
        try {
            // Check common product code formats (XXX-YY)
            if (dateStr.matches(Regex("\\d+-\\d+")) ||
                dateStr.matches(Regex("\\d+\\s+\\d+"))) {
                return true
            }

            // Check if it's near product code indicators
            val windowSize = 30
            val startWindow = maxOf(0, position - windowSize)
            val endWindow = minOf(context.length, position + dateStr.length + windowSize)

            val windowText = context.substring(startWindow, endWindow).lowercase(Locale.getDefault())

            for (term in productCodeTerms) {
                if (windowText.contains(term.lowercase(Locale.getDefault()))) {
                    return true
                }
            }

            // Check if it's formatted like a common product code
            // For example, "296-01" format
            if (dateStr.matches(Regex("\\d{3}-\\d{2}"))) {
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if date is product code", e)
        }

        return false
    }

    /**
     * Identifies product code related lines in the text
     */
    private fun identifyProductCodeLines(text: String): List<String> {
        val codeLines = mutableListOf<String>()

        try {
            // Split text into lines
            val lines = text.split("\n")

            // Identify lines with product code indicators
            for (line in lines) {
                if (isProductCodeLine(line)) {
                    codeLines.add(line)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error identifying product code lines", e)
        }

        return codeLines
    }

    /**
     * Checks if a line contains product code information
     */
    private fun isProductCodeLine(line: String): Boolean {
        val lowerLine = line.lowercase(Locale.getDefault())

        // Check for product code indicators
        if (productCodeTerms.any { lowerLine.contains(it.lowercase(Locale.getDefault())) }) {
            return true
        }

        // Check for specific patterns like 3-digit followed by hyphen and 2-digit (common PLU format)
        // e.g., "296-01"
        if (Regex("\\d{3}-\\d{2}").find(lowerLine) != null) {
            return true
        }

        // Check for patterns like "PLU 1234"
        if (Regex("plu\\s*\\d+").find(lowerLine) != null) {
            return true
        }

        return false
    }

    /**
     * Checks if a date is on or near a product code line
     */
    private fun isOnProductCodeLine(text: String, datePosition: Int, codeLines: List<String>): Boolean {
        try {
            // Get the line containing the date
            val lines = text.split("\n")
            var currentPos = 0
            for (line in lines) {
                val endPos = currentPos + line.length
                if (datePosition in currentPos..endPos) {
                    // Check if this line is in the code lines
                    if (codeLines.contains(line)) {
                        return true
                    }

                    // Also check adjacent lines
                    val lineIndex = lines.indexOf(line)
                    if (lineIndex > 0 && codeLines.contains(lines[lineIndex - 1])) {
                        return true
                    }
                    if (lineIndex < lines.size - 1 && codeLines.contains(lines[lineIndex + 1])) {
                        return true
                    }

                    break
                }
                currentPos = endPos + 1 // +1 for the newline character
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if date is on product code line", e)
        }

        return false
    }

    /**
     * Identifies price-related lines in the text
     */
    private fun identifyPriceLines(text: String): List<String> {
        val priceLines = mutableListOf<String>()

        try {
            // Split text into lines
            val lines = text.split("\n")

            // Identify lines with price indicators
            for (line in lines) {
                if (isPriceLine(line)) {
                    priceLines.add(line)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error identifying price lines", e)
        }

        return priceLines
    }

    /**
     * Checks if a line contains price information
     */
    private fun isPriceLine(line: String): Boolean {
        val lowerLine = line.lowercase(Locale.getDefault())

        // Check for price indicators
        if (priceTerms.any { lowerLine.contains(it.lowercase(Locale.getDefault())) }) {
            return true
        }

        // Check for price patterns
        for (pattern in avoidPatterns) {
            try {
                if (pattern.matcher(line).find()) {
                    return true
                }
            } catch (e: Exception) {
                continue
            }
        }

        // Check for common price patterns
        if (lowerLine.contains("$") && lowerLine.contains(".")) {
            return true
        }

        if (lowerLine.contains("price") || lowerLine.contains("cost") ||
            lowerLine.contains("total") || lowerLine.contains("save")) {
            return true
        }

        return false
    }

    /**
     * Checks if a date is on or near a price line
     */
    private fun isOnPriceLine(text: String, datePosition: Int, priceLines: List<String>): Boolean {
        try {
            // Get the line containing the date
            val lines = text.split("\n")
            var currentPos = 0
            for (line in lines) {
                val endPos = currentPos + line.length
                if (datePosition in currentPos..endPos) {
                    // Check if this line is in the price lines
                    if (priceLines.contains(line)) {
                        return true
                    }

                    // Also check adjacent lines
                    val lineIndex = lines.indexOf(line)
                    if (lineIndex > 0 && priceLines.contains(lines[lineIndex - 1])) {
                        return true
                    }
                    if (lineIndex < lines.size - 1 && priceLines.contains(lines[lineIndex + 1])) {
                        return true
                    }

                    break
                }
                currentPos = endPos + 1 // +1 for the newline character
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if date is on price line", e)
        }

        return false
    }

    /**
     * Checks if a date string is likely a price format
     */
    /**
     * Checks if a string is a potential weight value or on a weight-related line.
     * This helps avoid incorrectly identifying weights as dates.
     */
    private fun isWeightValue(dateStr: String, context: String, position: Int): Boolean {
        try {
            // First check if it's a simple decimal number with limited digits (typical of weights)
            if (dateStr.matches(Regex("\\d+\\.\\d{1,2}"))) { // Matches patterns like "3.99", "29.2", etc.
                // Look for weight indicators near this decimal number
                val windowSize = 50
                val startWindow = maxOf(0, position - windowSize)
                val endWindow = minOf(context.length, position + dateStr.length + windowSize)

                val windowText = context.substring(startWindow, endWindow).uppercase(Locale.getDefault())

                // Check if any weight terms are in the vicinity
                for (term in weightTerms) {
                    if (windowText.contains(term)) {
                        Log.d(TAG, "Identified $dateStr as weight value near term: $term")
                        return true
                    }
                }

                // Look for weight units explicitly attached to the number
                val afterText = context.substring(
                    position + dateStr.length,
                    minOf(context.length, position + dateStr.length + 10)
                ).uppercase(Locale.getDefault())

                // Check if the number is directly followed by a weight unit
                if (afterText.trim().startsWith("LB") ||
                    afterText.trim().startsWith("OZ") ||
                    afterText.trim().startsWith("G") ||
                    afterText.trim().startsWith("KG")) {
                    Log.d(TAG, "Identified $dateStr as weight value with unit: ${afterText.trim().take(5)}")
                    return true
                }

                // If this is just a plain decimal with no other context, it's more likely to be a weight/price
                // than a date when there are real date formats in the text
                val textLower = context.lowercase(Locale.getDefault())
                if (textLower.contains("sell thru") ||
                    textLower.contains("sell by") ||
                    textLower.contains("use by") ||
                    Regex("[a-z]{3}\\.\\s*\\d{1,2}\\.\\d{1,2}").find(textLower) != null) { // Check for month patterns like Apr. 29.25
                    Log.d(TAG, "Decimal $dateStr is likely not a date when real date formats exist in text")
                    return true
                }
            }

            // Check for context patterns that indicate this is a weight line
            val line = context.substring(
                maxOf(0, context.lastIndexOf('\n', position) + 1),
                minOf(context.length, context.indexOf('\n', position).let { if (it == -1) context.length else it })
            )

            val lineUpper = line.uppercase(Locale.getDefault())

            // Check for specific weight indicators in the line
            for (term in weightTerms) {
                if (lineUpper.contains(term)) {
                    // This line mentions weight, so the number is likely a weight
                    Log.d(TAG, "Number $dateStr is on a line with weight term: $term")
                    return true
                }
            }

            // Check if the line contains price indicators - decimal numbers on these lines are likely prices, not dates
            val lineLower = line.lowercase(Locale.getDefault())
            if (lineLower.contains("price") ||
                lineLower.contains("$") ||
                lineLower.contains("save") ||
                lineLower.contains("/lb")) {
                Log.d(TAG, "Number $dateStr is on a price-related line")
                return true
            }

            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if value is a weight", e)
            return false
        }
    }

    private fun isPriceFormat(dateStr: String, context: String, position: Int): Boolean {
        try {
            // First check if this is likely a weight value
            if (isWeightValue(dateStr, context, position)) {
                Log.d(TAG, "Identified $dateStr as weight value, not a date")
                return true // Exclude weights from date candidates (treated as "prices" for filtering)
            }

            // Check if the date string itself looks like a price
            if (dateStr.contains("$") || dateStr.contains("/lb") || dateStr.contains("/kg")) {
                return true
            }

            // Look for price indicators near the date
            val windowSize = 30
            val startWindow = maxOf(0, position - windowSize)
            val endWindow = minOf(context.length, position + dateStr.length + windowSize)

            val windowText = context.substring(startWindow, endWindow).lowercase(Locale.getDefault())

            // Check for $ signs and decimal points, typical of prices
            if (windowText.contains("$") && windowText.contains(".") &&
                (windowText.contains("price") || windowText.contains("/lb") || windowText.contains("total"))) {
                return true
            }

            // Check for numbers with currency symbols nearby
            val currencyPattern = Pattern.compile("\\$\\s*\\d+\\.\\d{2}")
            if (currencyPattern.matcher(windowText).find()) {
                return true
            }

            // Check if date is in a line that looks like a price line
            val lines = context.substring(startWindow, endWindow).split("\n")
            for (line in lines) {
                if (line.contains(dateStr) && isPriceLine(line)) {
                    return true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if date is price format", e)
        }

        return false
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

                    // Check if year is reasonable (between 1900 and 2100)
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
     * Convert a month name (full or abbreviated) to its numerical value (1-12)
     */
    private fun monthNameToNumber(month: String): Int {
        val normalizedMonth = month.lowercase(Locale.getDefault()).trim()

        return when(normalizedMonth) {
            "jan", "january" -> 1
            "feb", "february" -> 2
            "mar", "march" -> 3
            "apr", "april" -> 4
            "may" -> 5
            "jun", "june" -> 6
            "jul", "july" -> 7
            "aug", "august" -> 8
            "sep", "september" -> 9
            "oct", "october" -> 10
            "nov", "november" -> 11
            "dec", "december" -> 12
            else -> throw IllegalArgumentException("Unknown month name: $month")
        }
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
            // Skip if line appears to be product code related
            if (isProductCodeLine(line)) {
                return ""
            }

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
                // Validate if it looks like a real date and not a price or product code
                if (isValidDateFormat(potentialDate) &&
                    !isPriceFormat(potentialDate, line, matcher.start()) &&
                    !isProductCode(potentialDate, line, matcher.start())) {
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
                val potentialDate = matcher.group(0)
                // Validate it's not a product code or price
                if (!isPriceFormat(potentialDate, line, matcher.start()) &&
                    !isProductCode(potentialDate, line, matcher.start())) {
                    return potentialDate
                }
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
                // For additional validation, we could check if month is 1-12, day is 1-31, etc.
                if (parts.size == 3) {
                    // If it's MM/DD/YY or DD/MM/YY format
                    val monthOrDay1 = parts[0].toIntOrNull() ?: return false
                    val monthOrDay2 = parts[1].toIntOrNull() ?: return false

                    // Rough validation - check if potential months are valid (1-12)
                    if (monthOrDay1 > 12 && monthOrDay2 > 12) {
                        return false // Both can't be months
                    }

                    // Check if potential days are valid (1-31)
                    if (monthOrDay1 > 31 || monthOrDay2 > 31) {
                        return false
                    }
                }

                // Reject product code formats like "296-01"
                if (dateStr.matches(Regex("\\d{3}-\\d{2}"))) {
                    return false
                }

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
     * Updates the last successfully extracted date and timestamp
     */
    private fun updateLastSuccessfulExtraction(date: String) {
        if (date.isNotBlank()) {
            // Don't update if same as last extraction
            if (date == lastSuccessfulExtraction) {
                // Just update timestamp to keep it "fresh"
                lastExtractionTimestamp = System.currentTimeMillis()
                return
            }

            // Update confidence for this date
            val currentConfidence = confidenceHistory.getOrDefault(date, 0) + 1
            confidenceHistory[date] = minOf(currentConfidence, CONFIDENCE_MAX)

            // Only switch to a new date if it reaches the confidence threshold
            if (currentConfidence >= CONFIDENCE_THRESHOLD || lastSuccessfulExtraction.isBlank()) {
                Log.d(TAG, "Updating date extraction from '$lastSuccessfulExtraction' to '$date' (confidence: $currentConfidence)")
                lastSuccessfulExtraction = date
                lastExtractionTimestamp = System.currentTimeMillis()

                // Reduce confidence of other dates
                confidenceHistory.keys.forEach { otherDate ->
                    if (otherDate != date && confidenceHistory[otherDate]!! > 0) {
                        confidenceHistory[otherDate] = confidenceHistory[otherDate]!! - 1
                    }
                }
            } else {
                Log.d(TAG, "New date '$date' detected but keeping '$lastSuccessfulExtraction' (confidence: $currentConfidence/${CONFIDENCE_THRESHOLD})")
            }

            // Prune confidence history to prevent memory growth
            if (confidenceHistory.size > 10) {
                val keysToRemove = confidenceHistory.entries
                    .filter { it.value == 0 }
                    .map { it.key }
                    .toList()

                for (key in keysToRemove) {
                    confidenceHistory.remove(key)
                }
            }
        }
    }

    /**
     * Provides a fallback date if one was recently extracted (second implementation removed to fix conflict)
     */

    /**
     * Internal class to represent a date candidate with its weight and parsed date
     */
    private data class DateCandidate(
        val dateString: String,
        val weight: Double,
        val parsedDate: Date?
    )
}
