package com.example.chesslab

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.chesslab.databinding.ActivityRatingBinding

class RatingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRatingBinding

    companion object {
        const val EXTRA_CURRENT_USER = "currentUser"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRatingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.rating_title)

        val currentUser = intent.getStringExtra(EXTRA_CURRENT_USER) ?: "Player"

        if (savedInstanceState == null) {
            val fragment = RatingFragment().apply {
                arguments = Bundle().apply {
                    putString(EXTRA_CURRENT_USER, currentUser)
                }
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.rating_fragment_container, fragment)
                .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
