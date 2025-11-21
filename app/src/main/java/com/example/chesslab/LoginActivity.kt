package com.example.chesslab

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.example.chesslab.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var userPrefs: UserPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userPrefs = UserPrefs(this)

        setupEmailAutocomplete()
        setupButtons()
    }

    private fun setupEmailAutocomplete() {
        val emailEt = (binding.tilEmail.editText as? AutoCompleteTextView) ?: return
        val domains = resources.getStringArray(R.array.email_domains)
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, domains)
        emailEt.setAdapter(adapter)
    }

    private fun setupButtons() {
        binding.btnGoToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            finish()
        }

        binding.btnLogin.setOnClickListener {
            val email = binding.tilEmail.editText?.text.toString()
            val password = binding.tilPassword.editText?.text.toString()

            if (email.isBlank() || password.isBlank()) {
                binding.tilPassword.error = getString(R.string.login_form_error)
                return@setOnClickListener
            }

            if (userPrefs.authenticate(email, password)) {
                val userName = email.substringBefore("@")
                PreferenceManager.getDefaultSharedPreferences(this).edit()
                    .putString("user_name", userName)
                    .apply()

                startActivity(Intent(this, MainActivity::class.java))
                finish()
            } else {
                binding.tilPassword.error = getString(R.string.login_auth_error)
            }
        }
    }
}