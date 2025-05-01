package com.example.expirydetector

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.expirydetector.databinding.FragmentResultBinding
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

class ResultFragment : Fragment() {

    private var _binding: FragmentResultBinding? = null
    private val binding get() = _binding!!

    private val args: ResultFragmentArgs by navArgs()
    private val TAG = "ResultFragment"

    // Date formatter for formatting dates in the UI
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        try {
            // Display detected OCR text
            binding.detectedTextView.text = args.detectedText

            // Determine if we have key data detected
            val hasExpiryDate = args.expiryDate.isNotEmpty()
            val hasWeight = args.weight.isNotEmpty()

            // Set up result title based on detections
            setupResultTitle(hasExpiryDate, hasWeight)

            // Display expiry date
            if (hasExpiryDate) {
                binding.expiryDateText.text = args.expiryDate
                binding.expiryDateText.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.green)
                )

                // Add freshness information if possible
                addFreshnessInfo(args.expiryDate)
            } else {
                binding.expiryDateText.text = getString(R.string.no_expiry_found)
                binding.expiryDateText.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.red)
                )
                binding.freshnessInfo.visibility = View.GONE
            }

            // Display weight information
            if (hasWeight) {
                binding.weightText.text = args.weight
                binding.weightText.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.blue)
                )
            } else {
                binding.weightText.text = getString(R.string.no_weight_found)
                binding.weightText.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.gray)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up result view", e)

            // Handle the error by showing basic info
            binding.resultTitleText.text = "Scan Complete"
            binding.expiryDateText.text = args.expiryDate.ifEmpty { getString(R.string.no_expiry_found) }
            binding.weightText.text = args.weight.ifEmpty { getString(R.string.no_weight_found) }
            binding.freshnessInfo.visibility = View.GONE
        }

        // Set up scan again button
        binding.scanAgainButton.setOnClickListener {
            try {
                findNavController().navigate(
                    ResultFragmentDirections.actionResultFragmentToCameraFragment()
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error navigating back to camera", e)

                // Fallback if navigation fails
                activity?.onBackPressed()
            }
        }
    }

    private fun addFreshnessInfo(dateStr: String) {
        try {
            val expiryDate = parseDate(dateStr)
            if (expiryDate != null) {
                // Log the parsed date for debugging
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                Log.d(TAG, "Expiry date parsed as: ${sdf.format(expiryDate)}")

                val today = Calendar.getInstance().time

                binding.freshnessInfo.visibility = View.VISIBLE

                when {
                    expiryDate.before(today) -> {
                        // Product is expired
                        val daysBetween = getDaysBetween(today, expiryDate)
                        binding.freshnessInfo.text = "Product expired $daysBetween days ago"
                        binding.freshnessInfo.setTextColor(ContextCompat.getColor(requireContext(), R.color.red))
                    }
                    else -> {
                        // Product is not expired
                        val daysBetween = getDaysBetween(expiryDate, today)
                        if (daysBetween <= 3) {
                            // Soon to expire
                            binding.freshnessInfo.text = "Expires in $daysBetween days (soon)"
                            binding.freshnessInfo.setTextColor(ContextCompat.getColor(requireContext(), R.color.orange))
                        } else {
                            // Fresh
                            binding.freshnessInfo.text = "Expires in $daysBetween days"
                            binding.freshnessInfo.setTextColor(ContextCompat.getColor(requireContext(), R.color.green))
                        }
                    }
                }
            } else {
                binding.freshnessInfo.visibility = View.GONE
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating freshness info", e)
            binding.freshnessInfo.visibility = View.GONE
        }
    }

    private fun getDaysBetween(date1: Date, date2: Date): Long {
        // Normalize both dates to start of day to get accurate day count
        val cal1 = Calendar.getInstance()
        cal1.time = date1
        cal1.set(Calendar.HOUR_OF_DAY, 0)
        cal1.set(Calendar.MINUTE, 0)
        cal1.set(Calendar.SECOND, 0)
        cal1.set(Calendar.MILLISECOND, 0)

        val cal2 = Calendar.getInstance()
        cal2.time = date2
        cal2.set(Calendar.HOUR_OF_DAY, 0)
        cal2.set(Calendar.MINUTE, 0)
        cal2.set(Calendar.SECOND, 0)
        cal2.set(Calendar.MILLISECOND, 0)

        val diff = cal1.timeInMillis - cal2.timeInMillis
        return Math.abs(diff) / (24 * 60 * 60 * 1000)
    }

    private fun parseDate(dateStr: String): Date? {
        val formats = listOf(
            "MM/dd/yy", "MM/dd/yyyy", "dd/MM/yy", "dd/MM/yyyy", "yyyy/MM/dd",
            "MM-dd-yy", "MM-dd-yyyy", "dd-MM-yy", "dd-MM-yyyy", "yyyy-MM-dd",
            "MM.dd.yy", "MM.dd.yyyy", "dd.MM.yy", "dd.MM.yyyy", "yyyy.MM.dd",
            "MMM dd, yy", "MMM dd, yyyy", "dd MMM yy", "dd MMM yyyy", "yyyy MMM dd",
            "MMMM dd, yy", "MMMM dd, yyyy", "dd MMMM yy", "dd MMMM yyyy", "yyyy MMMM dd",
            "MM/yy", "MM/yyyy", "MMM yy", "MMM yyyy"
        )

        val normalizedDate = dateStr.trim().lowercase(Locale.getDefault())
        Log.d(TAG, "Attempting to parse date: $normalizedDate")

        // Try each date format
        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.US)
                sdf.isLenient = true
                val parsedDate = sdf.parse(normalizedDate)
                if (parsedDate != null) {
                    // Verify year is reasonable
                    val calendar = Calendar.getInstance()
                    calendar.time = parsedDate
                    val year = calendar.get(Calendar.YEAR)

                    if (year in 1900..2100) {
                        Log.d(TAG, "Successfully parsed with format $format: ${sdf.format(parsedDate)}")
                        return parsedDate
                    }
                }
            } catch (e: ParseException) {
                // Continue to next format
                continue
            }
        }

        // Special handling for European style dates with text month (22/DE/21)
        if (normalizedDate.matches(Regex("\\d{1,2}/\\w{2}/\\d{2}"))) {
            try {
                val parts = normalizedDate.split("/")
                if (parts.size == 3) {
                    val day = parts[0].toIntOrNull() ?: return null
                    val month = getMonthNumber(parts[1])
                    var year = parts[2].toIntOrNull() ?: return null

                    // Adjust 2-digit year (assume 20xx for years < 50, 19xx for years ≥ 50)
                    if (year < 100) {
                        year += if (year < 50) 2000 else 1900
                    }

                    val calendar = Calendar.getInstance()
                    calendar.set(year, month - 1, day)

                    val result = calendar.time
                    Log.d(TAG, "Parsed European format date: $normalizedDate as ${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(result)}")
                    return result
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing European date", e)
                // Fallback to null
            }
        }

        // Handle dates with just month and day by adding current year
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

    private fun getMonthNumber(monthStr: String): Int {
        val monthMap = mapOf(
            "ja" to 1, "jan" to 1, "january" to 1,
            "fe" to 2, "feb" to 2, "february" to 2,
            "mr" to 3, "mar" to 3, "march" to 3,
            "ap" to 4, "apr" to 4, "april" to 4,
            "my" to 5, "may" to 5,
            "jn" to 6, "jun" to 6, "june" to 6,
            "jl" to 7, "jul" to 7, "july" to 7,
            "au" to 8, "aug" to 8, "august" to 8,
            "se" to 9, "sep" to 9, "september" to 9,
            "oc" to 10, "oct" to 10, "october" to 10,
            "no" to 11, "nov" to 11, "november" to 11,
            "de" to 12, "dec" to 12, "december" to 12
        )

        return monthMap[monthStr.lowercase()] ?: 1
    }

    private fun setupResultTitle(hasExpiryDate: Boolean, hasWeight: Boolean) {
        when {
            hasExpiryDate && hasWeight -> {
                binding.resultTitleText.text = "Scan Successful!"
                binding.resultTitleText.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.green)
                )
            }
            hasExpiryDate -> {
                binding.resultTitleText.text = "Expiration Date Found!"
                binding.resultTitleText.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.green)
                )
            }
            hasWeight -> {
                binding.resultTitleText.text = "Weight Information Found!"
                binding.resultTitleText.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.blue)
                )
            }
            else -> {
                binding.resultTitleText.text = "No Information Found"
                binding.resultTitleText.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.red)
                )
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
