package com.example.expirydetector.utils.test

import com.example.expirydetector.utils.DateExtractor

/**
 * Simple test utility to verify the DateExtractorV2 class.
 * This can be used to test date extraction from the OCR results.
 */
object DateExtractorTest {

    /**
     * Run tests for various date formats
     */
    fun runTests() {
        // Sample text containing different date formats from product labels
        val testCases = listOf(
            // Test case 1: European format with text month (22/DE/21)
            TestCase(
                "TOTAL PRICE/PRIX DE VENTE KIRKLAND\n$62.62\nRIB OVEN ROAST BONELESS\nROSBIF DE COTES DESOSS\nKEEP REFRIGERATED | GARDER AU FROID\nMECHANICALLY TENDERIZED\nCOOK TO A MINIMUM INTERNAL TEMPERATURE\nOF 63C/145F\nPACKAGED ON\nEMPAQUETE LE\nBEST BEFORE\nMEILLEUR AVANT\n22/DE/18\n22/DE/21\n7:06\n$39.99/kg\nNET WEIGHT/POIDS NET\n1.566 kg",
                "22/DE/21"
            ),

            // Test case 2: Circled sell-by date in MM/DD/YY format
            TestCase(
                "BEEF RIBEYE STEAK\nBONELESS USDA PRIME\nBLADE TENDERIZED\nFOR YOUR SAFETY, USDA RECOMMENDS GRILLING\nTO A MINIMUM INTERNAL TEMPERATURE\nOF 145 DEGREES AS MEASURED BY A FOOD\nTHERMOMETER WITH A 3 MINUTE REST TIME.\nKEEP REFRIGERATED OR FROZEN\n11466A\nPACK DATE:\nSELL BY:\n08/17/22\n08/20/22\nNET WT\n4.28 lb\nUNIT PRICE\n$15.99/lb\nTOTAL\n$68.44\nCOSTCO WHOLESALE #0144, HENDERSON,NV 89014",
                "08/20/22"
            ),

            // Test case 3: "Best if Used By or Freeze By" format
            TestCase(
                "NuVal 17\nHyVee\nWest Des Moines, IA\n50266\nFor Information\ncall 1-800-772-4098\nBest if Used By or Freeze By\nNov 19, 13\nNet Wt/Ct\nUnit Price\nTotal Price\n2.96 lb\n$4.29/lb\n$12.70\nST. LOUIS SPARE RIBS\nFULL SLAB\nProduct of: U.S.A.",
                "Nov 19, 13"
            ),

            // Test case 4: Simple "sell by" date
            TestCase(
                "MARKET BASKET\n85% LEAN 15% FAT\nGROUND BEEF 1-2 LB\nPRODUCT OF U.S.A.\n08/17/22\nNET WT\n1.19 lb\nUNIT PRICE\n$3.79/lb\nTOTAL PRICE\n$4.51\nNutrition Facts\nServing Size 4 oz (112g) raw/uncooked\nAmount Per Serving\nCalories 240\nTotal Fat 17g\nSat. Fat 6.5g\nTrans Fat 1g\nCholest. 70mg\nSodium 75mg\nTotal Carb. 0g\nProtein 21g\nVit. D 0mcg\nCalcium 0mg\nIron 2mg\nPotas. 270mg",
                "08/17/22"
            ),

            // Test case 5: Far future "Sell By" date
            TestCase(
                "SAFE HANDLING INSTRUCTIONS\nTHIS PRODUCT WAS PREPARED FROM INSPECTED AND PASSED MEAT AND/OR POULTRY SOME FOOD PRODUCTS\nMAY CONTAIN BACTERIA THAT COULD CAUSE ILLNESS IF MISHANDLED OR COOKED IMPROPERLY. FOR\nYOUR PROTECTION FOLLOW THESE SAFE HANDLING INSTRUCTIONS.\nKEEP REFRIGERATED OR FROZEN. THAW IN REFRIGERATOR OR MICROWAVE.\nKEEP RAW MEAT AND POULTRY SEPARATE FROM OTHER FOODS.\nWASH WORKING SURFACES (INCLUDING CUTTING BOARDS), UTENSILS, AND\nHANDS AFTER TOUCHING RAW MEAT OR POULTRY.\nCOOK THOROUGHLY.\nKEEP HOT FOODS HOT. REFRIGERATE LEFTOVERS\nIMMEDIATELY OR DISCARD.\nSell By\n07/12/24\n$7.49/lb 1.11 lb\n$8.31\n296-01\nPLU 1619\n0.04\n10/18/21 10:03AM\nNew Price\n$5.19 /lb\nYou Save\n$2.55\nSale Price\n$5.76",
                "07/12/24"
            ),

            // Test case 6: Simple "Use By" date
            TestCase(
                "Feta, Pasteurized\nIngredients: Organic pasteurized milk, salt, cheese culture, vegetarian enzymes.\nDistributed by Organic Valley La Farge, WI 54639\nOregon Tilth Certified Organic\n1012160624-10\nUse By 01/10/17",
                "01/10/17"
            ),

            // Test case 7: Plain "SELL BY" text on egg carton
            TestCase(
                "SELL BY DEC 21\n8DOP326MN6",
                "DEC 21"
            ),

            // Test case 8: "Sell By" with time
            TestCase(
                "COOKING INSTRUCTIONS PAN\nFRY HEAT 3-4 TABLESPOONS OF OIL IN A\nFRY PAN\nSHORTENING IN A SKILLET ON MEDIUM HIGH\nHEAT. DIP MEAT IN BEATEN EGG, DREDGE\nIN FLOUR SEASONED WITH SALT AND\nPEPPER. PLACE IN SKILLET, COOK 3-4\nMINUTES UNTIL BROWN, TURN AND COOK\nAND.\nPRODUCT OF US\nSAFE HANDLING INSTRUCTIONS\nTHIS PRODUCT WAS PREPARED FROM INSPECTED AND PASSED MEAT\nAND/OR POULTRY. LIGHT FOOD PRODUCTS MAY CONTAIN BACTERIA THAT\nCOULD CAUSE ILLNESS IF THE PRODUCT IS MISHANDLED OR COOKING\nINSTRUCTIONS.\nKEEP REFRIGERATED OR FROZEN. THAW IN REFRIGERATOR\nKEEP RAW MEAT AND POULTRY SEPARATE FROM OTHER FOODS\nWASH WORKING SURFACES (INCLUDING CUTTING\nBOARDS), UTENSILS, AND HANDS AFTER TOUCHING RAW\nMEAT OR POULTRY.\nTHOROUGHLY COOK\nKEEP HOT FOODS NOT REFRIGERATE\nIMMEDIATELY OR\nPacked On\n02:23 PM\nSell By\nDEC 06 16\nNET WT/CT\n3.87 lb\nUNIT PRICE\n$1.99/lb\nTotal Price\n$7.70\nCUB FOODS BAXTER MN",
                "DEC 06 16"
            ),

            // Test case 9: Australian format with BEST BEFORE circled
            TestCase(
                "KIRKLAND Signature\nCostco Wholesale Australia Pty Ltd\n17-21 Parramatta Road\nLIDCOMBE NSW 2141 AUSTRALIA# 013090\nGROUND AUSTRALIAN BEEF\n*USE WITHIN 24 HOURS OR FREEZE *\nMADE IN A FACILITY THAT HANDLES:CRUSTACEAN, FISH, MOLLUSC.\nKEEP REFRIGERATED AT OR BELOW 5°C\nPACKED ON\n22.01.23\nBEST BEFORE\n23.01.23\nTOTAL PRICE\nkg\n3.688\n$/kg\n9.99\n$36.84",
                "23.01.23"
            )
        )

        // Run each test case
        testCases.forEachIndexed { index, testCase ->
            val extractedDate = DateExtractor.extractExpiryDate(testCase.text)
            val testPassed = extractedDate.equals(testCase.expectedDate, ignoreCase = true) ||
                    extractedDate.contains(testCase.expectedDate, ignoreCase = true)

            println("Test ${index + 1}: ${if (testPassed) "PASSED" else "FAILED"}")
            println("  Input text sample: ${testCase.text.take(50)}...")
            println("  Expected: ${testCase.expectedDate}")
            println("  Actual: $extractedDate")
            println()
        }
    }

    /**
     * Simple test case data class
     */
    data class TestCase(
        val text: String,
        val expectedDate: String
    )
}

// Run the tests to verify functionality
fun main() {
    println("Running DateExtractor Tests")
    println("===========================")
    DateExtractorTest.runTests()
}
