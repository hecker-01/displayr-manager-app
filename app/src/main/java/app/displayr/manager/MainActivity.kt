package app.displayr.manager

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.view.View
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebChromeClient
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import app.displayr.manager.updater.UpdateChecker
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var errorPageContainer: View
    private lateinit var errorTitle: TextView
    private lateinit var errorMessage: TextView
    private lateinit var errorDetails: TextView
    private lateinit var retryButton: Button
    private lateinit var changeUrlButton: Button
    private lateinit var settingsFab: FloatingActionButton
    private lateinit var updateBadge: View
    private var lastFailedUrl: String? = null
    private var currentAppUrl: String? = null
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val urlChanged = result.data?.getBooleanExtra("url_changed", false) ?: false
            if (urlChanged) {
                val newUrl = result.data?.getStringExtra("new_url")
                if (newUrl != null && newUrl != currentAppUrl) {
                    currentAppUrl = newUrl
                    loadAppUrl()
                }
            }
        }
    }

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        deliverFileChooserResult(
            WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DynamicColors.applyToActivityIfAvailable(this)

        // Check if app has completed setup
        val prefs = getSharedPreferences("displayr_prefs", MODE_PRIVATE)
        val isSetupComplete = prefs.getBoolean("is_setup_complete", false)
        currentAppUrl = prefs.getString("app_url", null)

        // Handle deep link on cold launch
        val deepLinkUrl = extractDeepLinkUrl(intent)
        if (deepLinkUrl != null && isSetupComplete) {
            prefs.edit().putString("app_url", deepLinkUrl).apply()
            currentAppUrl = deepLinkUrl
        }

        if (!isSetupComplete) {
            // Pass deep link intent through to SetupActivity
            val setupIntent = Intent(this, SetupActivity::class.java)
            if (intent.data != null) setupIntent.data = intent.data
            startActivity(setupIntent)
            finish()
            return
        }

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize error page views
        errorPageContainer = findViewById(R.id.errorPageContainer)
        errorTitle = findViewById(R.id.errorTitle)
        errorMessage = findViewById(R.id.errorMessage)
        errorDetails = findViewById(R.id.errorDetails)
        retryButton = findViewById(R.id.retryButton)
        changeUrlButton = findViewById(R.id.changeUrlButton)
        settingsFab = findViewById(R.id.settingsFab)
        updateBadge = findViewById(R.id.updateBadge)

        retryButton.setOnClickListener {
            lastFailedUrl?.let { url ->
                hideErrorPage()
                webView.loadUrl(url)
            }
        }

        changeUrlButton.setOnClickListener {
            openSettings()
        }

        settingsFab.setOnClickListener {
            openSettings()
        }

        // Initialize WebView
        webView = findViewById(R.id.webView)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                hideErrorPage()
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                lastFailedUrl = view?.url
                handler?.cancel()
                showSslErrorPage(error)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    lastFailedUrl = request.url.toString()
                    showErrorPage(error)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                if (filePathCallback == null) {
                    return false
                }
                if (this@MainActivity.filePathCallback != null) {
                    return false
                }
                this@MainActivity.filePathCallback = filePathCallback

                val chooserIntent = fileChooserParams?.createIntent()
                    ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    }

                return try {
                    fileChooserLauncher.launch(chooserIntent)
                    true
                } catch (_: ActivityNotFoundException) {
                    deliverFileChooserResult(null)
                    false
                }
            }
        }

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = true
        webView.settings.builtInZoomControls = true
        webView.settings.displayZoomControls = false
        webView.settings.setSupportZoom(true)
        webView.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        webView.settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
        webView.settings.javaScriptCanOpenWindowsAutomatically = true

        webView.settings.userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finishAffinity()
                }
            }
        })

        loadAppUrl()

        // Fire-and-forget update check
        UpdateChecker.check(this)

        // Show red dot on FAB when an update is available
        val showBadge: () -> Unit = {
            updateBadge.visibility = if (UpdateChecker.updateAvailable) View.VISIBLE else View.GONE
        }
        UpdateChecker.addListener(showBadge)
        showBadge()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val newUrl = extractDeepLinkUrl(intent)
        if (newUrl != null) {
            val prefs = getSharedPreferences("displayr_prefs", MODE_PRIVATE)
            prefs.edit().putString("app_url", newUrl).apply()
            currentAppUrl = newUrl
            val dialogView = layoutInflater.inflate(R.layout.dialog_url_changed, null)
            dialogView.findViewById<android.widget.TextView>(R.id.urlChangedUrl).text = newUrl

            MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setPositiveButton(R.string.ok) { dialog, _ ->
                    dialog.dismiss()
                    loadAppUrl()
                }
                .setCancelable(false)
                .show()
        }
    }

    private fun extractDeepLinkUrl(intent: Intent): String? {
        val data = intent.data ?: return null
        if (data.scheme == "displayr") {
            val url = data.getQueryParameter("url")
            if (!url.isNullOrBlank()) return url
        }
        return null
    }

    private fun openSettings() {
        settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
    }

    private fun loadAppUrl() {
        val url = currentAppUrl ?: return
        val headers = mapOf(
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
            "Accept-Language" to "en-US,en;q=0.9",
            "Accept-Encoding" to "gzip, deflate, br",
            "DNT" to "1",
            "Connection" to "keep-alive",
            "Upgrade-Insecure-Requests" to "1"
        )
        webView.loadUrl(url, headers)
    }

    private fun deliverFileChooserResult(selection: Array<Uri>?) {
        val callback = filePathCallback
        filePathCallback = null
        callback?.onReceiveValue(selection)
    }
    
    private fun showErrorPage(error: WebResourceError?) {
        errorPageContainer.visibility = View.VISIBLE
        webView.visibility = View.GONE
        
        val errorCode = error?.errorCode ?: -1
        val description = error?.description?.toString() ?: "Unknown error"
        
        when (errorCode) {
            WebViewClient.ERROR_HOST_LOOKUP -> {
                errorTitle.text = "No Internet Connection"
                errorMessage.text = "Unable to connect to the server"
                errorDetails.text = "Please check your internet connection and try again"
            }
            WebViewClient.ERROR_CONNECT -> {
                errorTitle.text = "Connection Failed"
                errorMessage.text = "Unable to establish connection"
                errorDetails.text = "The server might be down or unreachable"
            }
            WebViewClient.ERROR_TIMEOUT -> {
                errorTitle.text = "Connection Timeout"
                errorMessage.text = "The server took too long to respond"
                errorDetails.text = "Please try again later"
            }
            WebViewClient.ERROR_FILE_NOT_FOUND -> {
                errorTitle.text = "Page Not Found"
                errorMessage.text = "The requested page doesn't exist"
                errorDetails.text = "Error 404"
            }
            else -> {
                errorTitle.text = "Error Loading Page"
                errorMessage.text = description
                errorDetails.text = "Error code: $errorCode"
            }
        }
    }
    
    private fun showSslErrorPage(error: SslError?) {
        errorPageContainer.visibility = View.VISIBLE
        webView.visibility = View.GONE
        
        errorTitle.text = "Security Error"
        errorMessage.text = "SSL Certificate Error"
        
        val errorDetails = when (error?.primaryError) {
            SslError.SSL_EXPIRED -> "The certificate has expired"
            SslError.SSL_IDMISMATCH -> "The certificate hostname does not match"
            SslError.SSL_NOTYETVALID -> "The certificate is not yet valid"
            SslError.SSL_UNTRUSTED -> "The certificate authority is not trusted"
            SslError.SSL_DATE_INVALID -> "The certificate date is invalid"
            SslError.SSL_INVALID -> "The certificate is invalid"
            else -> "There is a problem with the website's security certificate"
        }
        
        this.errorDetails.text = errorDetails
    }
    
    private fun hideErrorPage() {
        errorPageContainer.visibility = View.GONE
        webView.visibility = View.VISIBLE
    }
    

}
