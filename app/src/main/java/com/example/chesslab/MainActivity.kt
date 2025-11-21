package com.example.chesslab

import androidx.core.app.ActivityOptionsCompat
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.chesslab.databinding.ActivityMainMenuBinding
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import com.example.chesslab.BuildConfig

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainMenuBinding
    private lateinit var userPrefs: UserPrefs
    private lateinit var db: AppDatabase
    private var hasSavedGame: Boolean = false

    private val settingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        recreate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userPrefs = UserPrefs(this)
        db = AppDatabase.getDatabase(this)
        setupToolbar()
        setupButtons()
        updateCounters()
    }

    override fun onResume() {
        super.onResume()
        updateCounters()
        updateUiForSession()
        checkForSavedGame()
    }

    override fun onStart() {
        super.onStart()
        maybeShowWelcome()
        updateUiForSession()
    }

    private fun checkForSavedGame() {
        lifecycleScope.launch {
            val savedGame = db.savedGameDao().getSavedGame(userPrefs.currentDisplayName())
            hasSavedGame = savedGame != null
            binding.btnContinue.visibility = if (hasSavedGame) View.VISIBLE else View.GONE
        }
    }

    private fun maybeShowWelcome() {
        if (userPrefs.sessionType() == "none") {
            showWelcomeDialog()
        }
    }

    private fun showWelcomeDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_welcome, null)

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(view)
            .setCancelable(false)
            .create()

        dialog.show()

        fun startAuthActivity(activityClass: Class<*>) {
            val intent = Intent(this, activityClass)
            val options = ActivityOptionsCompat.makeCustomAnimation(this, R.anim.slide_in_right, R.anim.slide_out_left)
            startActivity(intent, options.toBundle())
            dialog.dismiss()
            finish()
        }

        view.findViewById<MaterialButton>(R.id.btnLogin).setOnClickListener { startAuthActivity(LoginActivity::class.java) }
        view.findViewById<MaterialButton>(R.id.btnRegister).setOnClickListener { startAuthActivity(RegisterActivity::class.java) }
        view.findViewById<MaterialButton>(R.id.btnGuest).setOnClickListener {
            userPrefs.loginAsGuest()
            updateUiForSession()
            dialog.dismiss()
        }
    }

    private fun showAppDialog(
        title: String,
        message: String? = null,
        build: (MaterialAlertDialogBuilder.() -> Unit)? = null
    ) {
        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setPositiveButton("OK", null)
            .setCancelable(false)

        if (message != null) {
            builder.setMessage(message)
        }
        build?.invoke(builder)

        builder.create().show()
    }

    private fun updateUiForSession() {
        val name = userPrefs.currentDisplayName()
        val userNameTextView = binding.toolbar.findViewById<TextView>(R.id.toolbar_user_name)
        userNameTextView.text = if (userPrefs.isGuest()) "Гость" else name
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.toolbar.setNavigationIcon(R.drawable.ic_logout_24)
        binding.toolbar.setNavigationOnClickListener {
            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle(R.string.logout_dialog_title)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    userPrefs.logout()
                    updateUiForSession()
                    maybeShowWelcome()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun setupButtons() {
        binding.btnContinue.setOnClickListener {
            val intent = Intent(this, GameActivity::class.java)
            intent.putExtra(GameActivity.EXTRA_LOAD_SAVED_GAME, true)
            intent.putExtra(GameActivity.EXTRA_PLAYER_NAME, userPrefs.currentDisplayName())
            val options = ActivityOptionsCompat.makeCustomAnimation(this, R.anim.slide_in_right, R.anim.slide_out_left)
            startActivity(intent, options.toBundle())
        }
        binding.btnNewGame.setOnClickListener { 
            if (hasSavedGame) {
                showNewGameWarningDialog()
            } else {
                showNewGameDialog()
            }
        }
        binding.btnRating.setOnClickListener {
            val intent = Intent(this, RatingActivity::class.java)
            intent.putExtra(RatingActivity.EXTRA_CURRENT_USER, userPrefs.currentDisplayName())
            val options = ActivityOptionsCompat.makeCustomAnimation(this, R.anim.slide_in_right, R.anim.slide_out_left)
            startActivity(intent, options.toBundle())
        }
        binding.btnGameHistory.setOnClickListener {
            val intent = Intent(this, GameHistoryActivity::class.java)
            val options = ActivityOptionsCompat.makeCustomAnimation(this, R.anim.slide_in_right, R.anim.slide_out_left)
            startActivity(intent, options.toBundle())
        }
        binding.btnSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            val options = ActivityOptionsCompat.makeCustomAnimation(this, R.anim.slide_in_right, R.anim.slide_out_left)
            settingsLauncher.launch(intent, options)
        }
    }

    private fun showNewGameWarningDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.new_game_warning_title)
            .setMessage(R.string.new_game_warning_message)
            .setPositiveButton(R.string.yes) { _, _ -> showNewGameDialog() }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    private fun showNewGameDialog() {
        val gameModes = arrayOf(
            getString(R.string.main_mode_bot),
            getString(R.string.main_mode_friend),
            getString(R.string.main_mode_online)
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.start_game)
            .setItems(gameModes) { _, which ->
                when (which) {
                    0 -> startGame("bot")
                    1 -> startGame("friend")
                    2 -> showAppDialog(
                        getString(R.string.online_mode_dialog_title),
                        getString(R.string.online_mode_dialog_message)
                    )
                }
            }
            .show()
    }

    private fun startGame(mode: String) {
        val i = Intent(this, GameActivity::class.java)
        i.putExtra(GameActivity.EXTRA_MODE, mode)
        i.putExtra("player_name", userPrefs.currentDisplayName())
        val options = ActivityOptionsCompat.makeCustomAnimation(this, R.anim.slide_in_right, R.anim.slide_out_left)
        startActivity(i, options.toBundle())
    }

    private fun updateCounters() {
        val games = Stats.getGamesToday(this)
        binding.tvGamesToday.text = getString(R.string.text_stats, games)
        binding.tvOnlineNow.text = getString(R.string.text_stat, 1)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_rules -> {
                showAppDialog(
                    getString(R.string.dialog_rules_title),
                    getString(R.string.dialog_rules_body)
                )
                true
            }

            R.id.action_about -> {
                val body = getString(R.string.dialog_about_body, BuildConfig.VERSION_NAME)
                showAppDialog(getString(R.string.dialog_about_title), body)
                true
            }

            R.id.action_compose_demo -> {
                val intent = Intent(this, ComposeDemoActivity::class.java)
                intent.putExtra("current_user", userPrefs.currentDisplayName())
                val options = ActivityOptionsCompat.makeCustomAnimation(this, R.anim.slide_in_right, R.anim.slide_out_left)
                startActivity(intent, options.toBundle())
                true
            }

            R.id.action_contact_author -> {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:")
                    putExtra(Intent.EXTRA_EMAIL, arrayOf("author@example.com"))
                    putExtra(Intent.EXTRA_SUBJECT, getString(R.string.contact_author_subject))
                }
                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                } else {
                    Toast.makeText(this, R.string.no_email_app_found, Toast.LENGTH_SHORT).show()
                }
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }
}
