package com.example.expirydetector.utils.test

import com.example.expirydetector.utils.WeightExtractor

/**
 * Simple test utility to verify the WeightExtractor class.
 * This can be used to test weight extraction from OCR results.
 */
object WeightExtractorTest {

    /**
     * Run tests for various weight formats
     */
    fun runTests() {
        // Sample text containing different weight formats from product labels
        val testCases = listOf(
            // Test case 1: Weight in pounds (standard format)
            TestCase(
                "BEEF RIBEYE STEAK\nBONELESS USDA PRIME\nBLADE TENDERIZED\nNET WT\n4.28 lb\nUNIT PRICE\n$15.99/lb\nTOTAL\n$68.44\nCOSTCO WHOLESALE #0144, HENDERSON,NV 89014",
                "4.28 lb"
            ),

            // Test case 2: Weight in pounds (alternative format)
            TestCase(
                "MARKET BASKET\n85% LEAN 15% FAT\nGROUND BEEF 1-2 LB\nPRODUCT OF U.S.A.\n08/17/22\nNET WT\n1.19 lb\nUNIT PRICE\n$3.79/lb\nTOTAL PRICE\n$4.51",
                "1.19 lb"
            ),

            // Test case 3: Weight in kilograms
            TestCase(
                "KIRKLAND Signature\nCostco Wholesale Australia Pty Ltd\nGROUND AUSTRALIAN BEEF\n*USE WITHIN 24 HOURS OR FREEZE *\nKEEP REFRIGERATED AT OR BELOW 5°C\nPACKED ON\n22.01.23\nBEST BEFORE\n23.01.23\nTOTAL PRICE\nkg\n3.688\n$/kg\n9.99\n$36.84",
                "3.688 kg"
            ),

            // Test case 4: Weight in grams
            TestCase(
                "ORGANIC STRAWBERRIES\nProduct of Mexico\nPacked for Costco Wholesale Corp.\nIssaquah, WA 98027\nNET WT. 454g (1 lb)\nUnit Price: $4.99",
                "454g"
            ),

            // Test case 5: Weight with NET WT prefix
            TestCase(
                "ORGANIC BLUEBERRIES\nProduct of Peru\nUSDA ORGANIC\nNET WT 18 OZ (510g)\nKeep Refrigerated\nBestBefore: 12/25/22",
                "18 OZ"
            ),

            // Test case 6: Alternative weight format with unit
            TestCase(
                "HyVee\nWest Des Moines, IA\n50266\nFor Information\ncall 1-800-772-4098\nBest if Used By or Freeze By\nNov 19, 13\nNet Wt/Ct\nUnit Price\nTotal Price\n2.96 lb\n$4.29/lb\n$12.70\nST. LOUIS SPARE RIBS\nFULL SLAB\nProduct of: U.S.A.",
                "2.96 lb"
            ),

            // Test case 7: Multiple weight mentions (should pick NET WEIGHT)
            TestCase(
                "FRESH ATLANTIC SALMON FILLETS\nFarm Raised, Color Added\nReady to Cook\nKeep Refrigerated\nNET WEIGHT:\n1.25 LBS\nSERVE WEIGHT: 1.0 LBS\nSell By: Jan 15, 2023",
                "1.25 LBS"
            )
        )

        // Run each test case
        testCases.forEachIndexed { index, testCase ->
            val extractedWeight = WeightExtractor.extractWeight(testCase.text)
            val testPassed = extractedWeight.equals(testCase.expectedWeight, ignoreCase = true) ||
                    extractedWeight.contains(testCase.expectedWeight, ignoreCase = true)

            println("Test ${index + 1}: ${if (testPassed) "PASSED" else "FAILED"}")
            println("  Input text sample: ${testCase.text.take(50)}...")
            println("  Expected: ${testCase.expectedWeight}")
            println("  Actual: $extractedWeight")
            println()
        }
    }

    /**
     * Simple test case data class
     */
    data class TestCase(
        val text: String,
        val expectedWeight: String
    )
}

// Run the tests
fun main() {
    println("Running WeightExtractor Tests")
    println("=============================")
    WeightExtractorTest.runTests()
}
