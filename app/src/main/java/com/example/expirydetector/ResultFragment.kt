package com.example.expirydetector

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.expirydetector.databinding.FragmentResultBinding

class ResultFragment : Fragment() {

    private var _binding: FragmentResultBinding? = null
    private val binding get() = _binding!!
    
    private val args: ResultFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Display detected OCR text
        binding.detectedTextView.text = args.detectedText
        
        // Display expiry date
        if (args.expiryDate.isNotEmpty()) {
            binding.expiryDateText.text = args.expiryDate
            binding.expiryDateText.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.green)
            )
        } else {
            binding.expiryDateText.text = getString(R.string.no_expiry_found)
            binding.expiryDateText.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.red)
            )
        }
        
        // Set up scan again button
        binding.scanAgainButton.setOnClickListener {
            findNavController().navigate(
                ResultFragmentDirections.actionResultFragmentToCameraFragment()
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
