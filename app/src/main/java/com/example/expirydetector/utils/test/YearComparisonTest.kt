package com.example.expirydetector.utils.test

import com.example.expirydetector.utils.DateExtractor
import java.text.SimpleDateFormat
import java.util.*

/**
 * Test class for verifying year comparison logic in date extraction.
 * This ensures that the latest date is selected based on the full date,
 * including the year component.
 */
object YearComparisonTest {

    /**
     * Run tests for comparing dates with different years
     */
    fun runTests() {
        val testCases = listOf(
            // Basic year comparison tests with numerical formats
            TestCase(
                "Best Before: 01/15/2023\nUse By: 01/15/2024",
                "01/15/2024", // This is the later date (year 2024 vs 2023)
                listOf("01/15/2023", "01/15/2024")
            ),

            // Different formats but same dates
            TestCase(
                "EXP: 12.31.22\nSELL BY: 12/31/2023",
                "12/31/2023", // This is the later date (year 2023 vs 2022)
                listOf("12.31.22", "12/31/2023")
            ),

            // Text month with year comparison
            TestCase(
                "Best Before: JAN 10, 2023\nExpires: JAN 10, 2024",
                "JAN 10, 2024", // This is the later date (year 2024 vs 2023)
                listOf("JAN 10, 2023", "JAN 10, 2024")
            ),

            // European format with year comparison
            TestCase(
                "Packaged: 10/DE/22\nExpires: 10/DE/23",
                "10/DE/23", // This is the later date (year 2023 vs 2022)
                listOf("10/DE/22", "10/DE/23")
            ),

            // Multiple years with different month/day combinations
            TestCase(
                "Product Information\nManufactured: 06/01/2022\nExpires: 12/31/2022\nSell By: 05/15/2023",
                "05/15/2023", // This is the latest date (2023 > 2022)
                listOf("06/01/2022", "12/31/2022", "05/15/2023")
            ),

            // Month/day no year, with year in other dates
            TestCase(
                "FREEZE BY: 11/20\nEXP: 03/15/2023\nBEST BY: 12/31/2022",
                "03/15/2023", // This is the latest date with explicit year
                listOf("11/20", "03/15/2023", "12/31/2022")
            ),

            // Julian date format with year
            TestCase(
                "Production: 22134\nExpiration: 23134",
                "23134", // Julian date - year 2023 day 134
                listOf("22134", "23134")
            )
        )

        // Run tests
        testCases.forEachIndexed { index, testCase ->
            val extractedDate = DateExtractor.extractExpiryDate(testCase.text)

            // Check if extracted date matches expected
            val testPassed = extractedDate.equals(testCase.expectedLatestDate, ignoreCase = true) ||
                    extractedDate.contains(testCase.expectedLatestDate, ignoreCase = true)

            // Print results
            println("Year Comparison Test ${index + 1}: ${if (testPassed) "PASSED" else "FAILED"}")
            println("  Text contains dates: ${testCase.allDates.joinToString(", ")}")
            println("  Expected latest date: ${testCase.expectedLatestDate}")
            println("  Actual extracted date: $extractedDate")
            println()
        }
    }

    /**
     * Data class for test cases
     */
    data class TestCase(
        val text: String,
        val expectedLatestDate: String,
        val allDates: List<String>
    )
}

// Run the test
fun main() {
    println("Running Year Comparison Test")
    println("============================")
    YearComparisonTest.runTests()
}
