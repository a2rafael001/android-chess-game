package com.example.chesslab

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.chesslab.databinding.FragmentRatingBinding

class RatingFragment : Fragment() {

    private var _binding: FragmentRatingBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRatingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val currentUser = arguments?.getString("currentUser") ?: "Player"


        val players = listOf(
            Player("Magnus Carlsen", 2830, 150, 20),
            Player("Fabiano Caruana", 2805, 140, 25),
            Player(currentUser, 1200, 10, 5), // Current user
            Player("Ian Nepomniachtchi", 2795, 135, 30),
            Player("Hikaru Nakamura", 2780, 145, 22),
            Player("Alireza Firouzja", 2760, 130, 35)
        ).sortedByDescending { it.rating }

        val adapter = RatingAdapter(players, currentUser)
        binding.rvRating.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRating.adapter = adapter
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
