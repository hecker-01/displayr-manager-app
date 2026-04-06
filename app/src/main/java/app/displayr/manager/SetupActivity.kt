package app.displayr.manager

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.Formatter
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import app.displayr.manager.updater.UpdateChecker
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.noties.markwon.Markwon
import android.widget.ViewFlipper

class SetupActivity : AppCompatActivity() {

    private lateinit var viewFlipper: ViewFlipper
    private lateinit var updateSubtitle: TextView
    private lateinit var updateProgress: LinearProgressIndicator
    private lateinit var skipButton: MaterialButton
    private lateinit var updateButton: MaterialButton
    private lateinit var continueButton: MaterialButton
    private lateinit var urlInput: TextInputEditText
    private lateinit var urlLayout: TextInputLayout

    private val qrLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val url = result.data?.getStringExtra("url")
            if (!url.isNullOrBlank()) {
                urlInput.setText(url)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DynamicColors.applyToActivityIfAvailable(this)
        enableEdgeToEdge()
        setContentView(R.layout.activity_setup)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.setupRoot)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Handle deep link
        if (handleDeepLink(intent)) return

        viewFlipper = findViewById(R.id.viewFlipper)
        updateSubtitle = findViewById(R.id.setupUpdateSubtitle)
        updateProgress = findViewById(R.id.setupUpdateProgress)
        skipButton = findViewById(R.id.setupSkipButton)
        updateButton = findViewById(R.id.setupUpdateButton)
        continueButton = findViewById(R.id.setupContinueButton)
        urlInput = findViewById(R.id.setupUrlInput)
        urlLayout = findViewById(R.id.setupUrlLayout)

        // Step 1: Update check
        val updateListener: () -> Unit = {
            updateProgress.visibility = View.GONE
            if (UpdateChecker.updateAvailable) {
                updateSubtitle.text = getString(
                    R.string.update_available_format, UpdateChecker.latestVersion
                )
                updateButton.visibility = View.VISIBLE
                skipButton.visibility = View.VISIBLE
            } else if (UpdateChecker.lastCheckError != null) {
                updateSubtitle.text = getString(
                    R.string.update_check_failed_format, UpdateChecker.lastCheckError
                )
                continueButton.visibility = View.VISIBLE
            } else {
                updateSubtitle.text = getString(R.string.up_to_date)
                continueButton.visibility = View.VISIBLE
            }
        }
        UpdateChecker.addListener(updateListener)
        UpdateChecker.check(this)

        updateButton.setOnClickListener {
            showUpdateDialog()
        }

        skipButton.setOnClickListener {
            goToUrlStep()
        }

        continueButton.setOnClickListener {
            goToUrlStep()
        }

        // Step 2: URL configuration
        findViewById<MaterialButton>(R.id.setupScanQrButton).setOnClickListener {
            qrLauncher.launch(Intent(this, QrScannerActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.setupDoneButton).setOnClickListener {
            val url = urlInput.text?.toString()?.trim() ?: ""
            if (url.isEmpty()) {
                urlLayout.error = getString(R.string.setup_url_empty)
                return@setOnClickListener
            }
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                urlLayout.error = getString(R.string.setup_url_invalid)
                return@setOnClickListener
            }
            urlLayout.error = null
            saveUrlAndFinish(url)
        }

        urlInput.setOnFocusChangeListener { _, _ -> urlLayout.error = null }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent): Boolean {
        val data = intent.data ?: return false
        if (data.scheme == "displayr") {
            val url = data.getQueryParameter("url")
            if (!url.isNullOrBlank()) {
                saveUrlAndFinish(url)
                return true
            }
        }
        return false
    }

    private fun goToUrlStep() {
        viewFlipper.showNext()
    }

    private fun saveUrlAndFinish(url: String) {
        val prefs = getSharedPreferences("displayr_prefs", MODE_PRIVATE)
        prefs.edit().putString("app_url", url).apply()
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    private fun showUpdateDialog() {
        val version = UpdateChecker.latestVersion ?: return
        val body = UpdateChecker.releaseBody
        val sizeBytes = UpdateChecker.apkSizeBytes

        val dialogView = layoutInflater.inflate(R.layout.dialog_update, null)

        dialogView.findViewById<TextView>(R.id.update_size).text =
            getString(R.string.update_dialog_size_format, Formatter.formatFileSize(this, sizeBytes))

        val changelogView = dialogView.findViewById<TextView>(R.id.update_changelog)
        if (!body.isNullOrBlank()) {
            val markwon = Markwon.create(this)
            markwon.setMarkdown(changelogView, body)
        } else {
            changelogView.text = getString(R.string.no_changelog)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.update_dialog_title_format, version))
            .setView(dialogView)
            .setPositiveButton(R.string.update_button) { dialog, _ ->
                dialog.dismiss()
                // Open the APK download URL in the browser for setup flow
                val apkUrl = UpdateChecker.latestApkUrl
                if (apkUrl != null) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl)))
                }
            }
            .setNegativeButton(R.string.setup_skip) { dialog, _ ->
                dialog.dismiss()
                goToUrlStep()
            }
            .show()
    }
}
