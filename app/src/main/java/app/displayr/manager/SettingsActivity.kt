package app.displayr.manager

import android.content.Intent
import android.os.Bundle
import android.text.format.Formatter
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import app.displayr.manager.updater.AppUpdater
import app.displayr.manager.updater.UpdateChecker
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.noties.markwon.Markwon

class SettingsActivity : AppCompatActivity() {

    private lateinit var appUpdater: AppUpdater
    private lateinit var urlValue: TextView
    private lateinit var updateSubtitle: TextView
    private lateinit var updateProgress: LinearProgressIndicator
    private lateinit var updateCard: MaterialCardView
    private lateinit var versionValue: TextView

    private var urlChanged = false
    private var originalUrl: String? = null

    private val qrLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val url = result.data?.getStringExtra("url")
            if (!url.isNullOrBlank()) {
                saveUrl(url)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DynamicColors.applyToActivityIfAvailable(this)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)

        appUpdater = AppUpdater(this)

        val root = findViewById<android.view.View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.settingsToolbar)
        toolbar.setNavigationOnClickListener { finishWithResult() }

        urlValue = findViewById(R.id.urlValue)
        updateSubtitle = findViewById(R.id.updateSubtitle)
        updateProgress = findViewById(R.id.updateProgress)
        updateCard = findViewById(R.id.updateCard)
        versionValue = findViewById(R.id.versionValue)

        val prefs = getSharedPreferences("displayr_prefs", MODE_PRIVATE)
        originalUrl = prefs.getString("app_url", null)
        urlValue.text = originalUrl ?: getString(R.string.settings_app_url_not_set)

        // Version info
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            versionValue.text = getString(R.string.app_name) + " v${pInfo.versionName}"
        } catch (_: Exception) {
            versionValue.text = getString(R.string.app_name)
        }

        // URL card
        findViewById<MaterialCardView>(R.id.urlCard).setOnClickListener {
            showUrlEditDialog()
        }

        // Update card wiring
        appUpdater.onStateChanged = fun(state: AppUpdater.State, message: String) {
            updateSubtitle.text = message
            when (state) {
                AppUpdater.State.DOWNLOADING, AppUpdater.State.INSTALLING -> {
                    updateProgress.isVisible = true
                    updateCard.isClickable = false
                    updateCard.isFocusable = false
                }
                else -> {
                    updateProgress.isVisible = false
                    updateCard.isClickable = true
                    updateCard.isFocusable = true
                }
            }
        }

        appUpdater.onDownloadProgress = fun(progress: Int) {
            if (progress < 0) {
                updateProgress.isIndeterminate = true
            } else {
                updateProgress.isIndeterminate = false
                updateProgress.setProgressCompat(progress, true)
            }
        }

        appUpdater.syncFromChecker()

        updateCard.setOnClickListener {
            if (appUpdater.onUpdateTapped(this)) {
                showUpdateDialog()
            }
        }

        // Clear cache card
        findViewById<MaterialCardView>(R.id.clearCacheCard).setOnClickListener {
            WebView(this).clearCache(true)
            WebStorage.getInstance().deleteAllData()
            Toast.makeText(this, R.string.settings_cache_cleared, Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Use onBackPressedDispatcher")
    override fun onBackPressed() {
        finishWithResult()
    }

    private fun finishWithResult() {
        val resultIntent = Intent().apply {
            putExtra("url_changed", urlChanged)
            val prefs = getSharedPreferences("displayr_prefs", MODE_PRIVATE)
            putExtra("new_url", prefs.getString("app_url", null))
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    private fun showUrlEditDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_url_edit, null)
        val inputLayout = dialogView.findViewById<TextInputLayout>(R.id.urlEditLayout)
        val input = dialogView.findViewById<TextInputEditText>(R.id.urlEditInput)
        val scanButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.urlEditScanButton)

        val prefs = getSharedPreferences("displayr_prefs", MODE_PRIVATE)
        input.setText(prefs.getString("app_url", ""))

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_url_dialog_title)
            .setView(dialogView)
            .setPositiveButton(R.string.setup_continue) { dlg, _ ->
                val url = input.text?.toString()?.trim() ?: ""
                if (url.isEmpty()) {
                    inputLayout.error = getString(R.string.setup_url_empty)
                    return@setPositiveButton
                }
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    inputLayout.error = getString(R.string.setup_url_invalid)
                    return@setPositiveButton
                }
                dlg.dismiss()
                saveUrl(url)
            }
            .setNegativeButton(R.string.cancel_button, null)
            .create()

        scanButton.setOnClickListener {
            dialog.dismiss()
            qrLauncher.launch(Intent(this, QrScannerActivity::class.java))
        }

        dialog.show()
    }

    private fun saveUrl(url: String) {
        val prefs = getSharedPreferences("displayr_prefs", MODE_PRIVATE)
        prefs.edit().putString("app_url", url).apply()
        urlValue.text = url
        urlChanged = true
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
                appUpdater.startDownload(this)
            }
            .setNegativeButton(R.string.cancel_button, null)
            .show()
    }

    override fun onDestroy() {
        appUpdater.cleanup()
        super.onDestroy()
    }
}
