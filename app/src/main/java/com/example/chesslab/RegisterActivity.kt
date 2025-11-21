package com.example.chesslab

import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.util.Patterns
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import com.example.chesslab.databinding.ActivityRegisterBinding

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var userPrefs: UserPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userPrefs = UserPrefs(this)

        // --- Фильтры ввода ---
        val nameFilter = InputFilter { src, _, _, _, _, _ ->
            if (src.matches(Regex("[\\p{L}\\s-]+"))) src else ""
        }
        binding.tilUsername.editText?.filters = arrayOf(nameFilter, InputFilter.LengthFilter(30))

        val passAllowed = Regex("[A-Za-z0-9@#\$%^&+=!?*._-]+")
        val passFilter = InputFilter { src, _, _, _, _, _ ->
            if (src.isEmpty() || src.matches(passAllowed)) src else ""
        }
        binding.tilPassword.editText?.filters = arrayOf(passFilter, InputFilter.LengthFilter(32))
        binding.tilPasswordConfirm.editText?.filters = arrayOf(passFilter, InputFilter.LengthFilter(32))

        // --- Умный автокомплит e-mail ---
        val emailEt = binding.tilEmail.editText as? AutoCompleteTextView
        val emailAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, mutableListOf<String>())
        emailEt?.setAdapter(emailAdapter)
        val domains = resources.getStringArray(R.array.email_domains).toList()

        // --- Live-валидация + автокомплит ---
        fun updateBtn() {
            val name  = binding.tilUsername.editText?.text?.toString()?.trim().orEmpty()
            val email = binding.tilEmail.editText?.text?.toString()?.trim().orEmpty()
            val pass  = binding.tilPassword.editText?.text?.toString().orEmpty()
            val conf  = binding.tilPasswordConfirm.editText?.text?.toString().orEmpty()
            binding.btnRegister.isEnabled =
                name.isNotEmpty() &&
                        emailErrorOrNull(email) == null &&
                        passwordErrorOrNull(pass) == null &&
                        pass == conf
        }

        binding.tilEmail.editText?.doOnTextChanged { text, _, _, _ ->
            val raw = text?.toString()?.trim().orEmpty()
            binding.tilEmail.error = emailErrorOrNull(raw)

            // подсказки вида local@domain
            val parts = raw.split("@", limit = 2)
            val local = parts.getOrNull(0).orEmpty()
            val typedDomain = parts.getOrNull(1).orEmpty()
            val base = if (typedDomain.isBlank()) domains else domains.filter { it.startsWith(typedDomain, true) }
            val suggestions = if (local.isNotBlank()) base.take(6).map { "$local@$it" } else emptyList()

            emailAdapter.clear()
            emailAdapter.addAll(suggestions)
            if (suggestions.isNotEmpty() && emailEt?.isFocused == true) emailEt.showDropDown()

            updateBtn()
        }

        binding.tilPassword.editText?.doOnTextChanged { text, _, _, _ ->
            val pwd = text?.toString().orEmpty()
            binding.tilPassword.error = passwordErrorOrNull(pwd)
            // индикатор силы пароля (если есть в layout)
            try { binding.passStrength.progress = passwordStrengthPercent(pwd) } catch (_: Throwable) {}
            updateBtn()
        }

        binding.tilPasswordConfirm.editText?.doOnTextChanged { text, _, _, _ ->
            val pass = binding.tilPassword.editText?.text?.toString().orEmpty()
            val conf = text?.toString().orEmpty()
            binding.tilPasswordConfirm.error =
                if (conf.isNotEmpty() && conf != pass) getString(R.string.register_passwords_dont_match) else null
            updateBtn()
        }

        binding.tilUsername.editText?.doOnTextChanged { _, _, _, _ -> updateBtn() }
        updateBtn()

        // --- Кнопки ---
        binding.btnGoToLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        binding.btnRegister.setOnClickListener {
            val name  = binding.tilUsername.editText?.text?.toString()?.trim().orEmpty()
            val email = binding.tilEmail.editText?.text?.toString()?.trim().orEmpty()
            val pass  = binding.tilPassword.editText?.text?.toString().orEmpty()
            val conf  = binding.tilPasswordConfirm.editText?.text?.toString().orEmpty()

            var ok = true
            if (name.isEmpty()) { binding.tilUsername.error = getString(R.string.error_enter_name); ok = false }
            emailErrorOrNull(email)?.let { binding.tilEmail.error = it; ok = false }
            passwordErrorOrNull(pass)?.let { binding.tilPassword.error = it; ok = false }
            if (conf != pass) { binding.tilPasswordConfirm.error = getString(R.string.register_passwords_dont_match); ok = false }
            if (!ok) return@setOnClickListener

            // регистрируем и логиним
            userPrefs.register(name, email, pass)
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    // --- Хелперы ---

    private fun emailErrorOrNull(email: String): String? =
        when {
            email.isEmpty() -> getString(R.string.error_enter_email)
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> getString(R.string.error_bad_email)
            else -> null
        }

    // Политика пароля: ≥8, строчная, ПРОПИСНАЯ, цифра, спецсимвол
    private fun passwordErrorOrNull(pwd: String): String? {
        val specials = "@#\$%^&+=!?*._-"
        return when {
            pwd.length < 8 -> getString(R.string.error_pass_len)
            !pwd.any { it.isLowerCase() } -> getString(R.string.error_pass_lower)
            !pwd.any { it.isUpperCase() } -> getString(R.string.error_pass_upper)
            !pwd.any { it.isDigit() }     -> getString(R.string.error_pass_digit)
            !pwd.any { it in specials }   -> getString(R.string.error_pass_special, specials)
            else -> null
        }
    }

    private fun passwordStrengthPercent(pwd: String): Int {
        var s = 0
        if (pwd.length >= 8) s += 20
        if (pwd.any { it.isLowerCase() }) s += 20
        if (pwd.any { it.isUpperCase() }) s += 20
        if (pwd.any { it.isDigit() })     s += 20
        if (pwd.any { it in "@#\$%^&+=!?*._-" }) s += 20
        return s
    }
}
