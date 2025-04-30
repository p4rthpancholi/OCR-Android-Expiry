package com.example.expirydetector.utils.test

import com.example.expirydetector.utils.DateExtractor
import java.text.SimpleDateFormat
import java.util.*

/**
 * Test class specifically for testing the multiple date selection logic.
 * This verifies that when multiple dates are present, the latest date is chosen
 * as the expiration date.
 */
object MultiDateTest {

    /**
     * Run tests for multiple dates in one text sample
     */
    fun runTests() {
        val testCases = listOf(
            // Packed date and expiry date
            TestCase(
                "KIRKLAND Signature\nCostco Wholesale Australia Pty Ltd\n17-21 Parramatta Road\nLIDCOMBE NSW 2141 AUSTRALIA# 013090\nGROUND AUSTRALIAN BEEF\n*USE WITHIN 24 HOURS OR FREEZE *\nMADE IN A FACILITY THAT HANDLES:CRUSTACEAN, FISH, MOLLUSC.\nKEEP REFRIGERATED AT OR BELOW 5°C\nPACKED ON\n22.01.23\nBEST BEFORE\n23.01.23\nTOTAL PRICE\nkg\n3.688\n$/kg\n9.99\n$36.84",
                "23.01.23", // This is the later date and is marked as BEST BEFORE
                listOf("22.01.23", "23.01.23")
            ),

            // Pack date and sell by date
            TestCase(
                "BEEF RIBEYE STEAK\nBONELESS USDA PRIME\nBLADE TENDERIZED\nFOR YOUR SAFETY, USDA RECOMMENDS GRILLING\nTO A MINIMUM INTERNAL TEMPERATURE\nOF 145 DEGREES AS MEASURED BY A FOOD\nTHERMOMETER WITH A 3 MINUTE REST TIME.\nKEEP REFRIGERATED OR FROZEN\n11466A\nPACK DATE:\nSELL BY:\n08/17/22\n08/20/22\nNET WT\n4.28 lb\nUNIT PRICE\n$15.99/lb\nTOTAL\n$68.44\nCOSTCO WHOLESALE #0144, HENDERSON,NV 89014",
                "08/20/22", // This is the later date and is marked as SELL BY
                listOf("08/17/22", "08/20/22")
            ),

            // Multiple dates with different formats
            TestCase(
                "ORGANIC STRAWBERRIES\nProduct of USA\nPacked for Organic Farms\nPacked on: 01/15/2023\nBest Before: Jan 20, 2023\nLot: 12345\nKeep Refrigerated\nNet Weight: 1 lb (454g)",
                "Jan 20, 2023", // This is the later date and is marked as BEST BEFORE
                listOf("01/15/2023", "Jan 20, 2023")
            ),

            // Different date markers
            TestCase(
                "FRESH ATLANTIC SALMON FILLETS\nFarm Raised, Color Added\nReady to Cook\nKeep Refrigerated\nPACKAGED: JAN 10, 2023\nUSE BY: JAN 15, 2023\nSELL BY: JAN 13, 2023\nNET WEIGHT: 1.25 LBS",
                "JAN 15, 2023", // This is the latest date and is marked as USE BY
                listOf("JAN 10, 2023", "JAN 15, 2023", "JAN 13, 2023")
            ),

            // Mixed marker with multiple dates on same line
            TestCase(
                "ORGANIC APPLES\nProduct of Washington, USA\nPacked: 12/01/22 Best By: 12/15/22\nKeep Refrigerated\nNet Wt. 3 lbs\nPrice: $5.99",
                "12/15/22", // This is the later date and is marked as BEST BY
                listOf("12/01/22", "12/15/22")
            ),

            // Test with Julian dates
            TestCase(
                "ORGANIC EGGS\nGrade A Large\nLot: 22356\nExp: 23030\nPackaged: 22356\nCage Free\n1 Dozen",
                "23030", // This is the later date (Julian) and is marked as EXP
                listOf("22356", "23030")
            ),

            // Test with European format and text month
            TestCase(
                "TOTAL PRICE/PRIX DE VENTE KIRKLAND\n$62.62\nRIB OVEN ROAST BONELESS\nROSBIF DE COTES DESOSS\nKEEP REFRIGERATED | GARDER AU FROID\nMECHANICALLY TENDERIZED\nCOOK TO A MINIMUM INTERNAL TEMPERATURE\nOF 63C/145F\nPACKAGED ON\nEMPAQUETE LE\nBEST BEFORE\nMEILLEUR AVANT\n22/DE/18\n22/DE/21\n7:06\n$39.99/kg\nNET WEIGHT/POIDS NET\n1.566 kg",
                "22/DE/21", // This is the later date and is marked as BEST BEFORE
                listOf("22/DE/18", "22/DE/21")
            )
        )

        // Run tests
        testCases.forEachIndexed { index, testCase ->
            val extractedDate = DateExtractor.extractExpiryDate(testCase.text)

            // Check if extracted date matches expected
            val testPassed = extractedDate.equals(testCase.expectedLatestDate, ignoreCase = true) ||
                    extractedDate.contains(testCase.expectedLatestDate, ignoreCase = true)

            // Print results
            println("Test ${index + 1}: ${if (testPassed) "PASSED" else "FAILED"}")
            println("  Text contains dates: ${testCase.allDates.joinToString(", ")}")
            println("  Expected latest date: ${testCase.expectedLatestDate}")
            println("  Actual extracted date: $extractedDate")
            println()
        }
    }

    /**
     * Data class for multiple date test cases
     */
    data class TestCase(
        val text: String,
        val expectedLatestDate: String,
        val allDates: List<String>
    )
}

// Run the test
fun main() {
    println("Running Multiple Dates Selection Test")
    println("====================================")
    MultiDateTest.runTests()
}
