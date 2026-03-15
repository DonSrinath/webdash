package com.webdash.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.*
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var pendingExportJson: String? = null

    // ── FILE CHOOSER (import) ──────────────────────────────
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                // Handle import file pick
                try {
                    val json = contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                    if (json != null) {
                        webView.post {
                            webView.evaluateJavascript(
                                "window.onImportConfig && window.onImportConfig(${escapeJsonForJs(json)})", null
                            )
                        }
                    }
                } catch (e: Exception) {
                    webView.post {
                        webView.evaluateJavascript("window.onImportConfig && window.onImportConfig(null)", null)
                    }
                }
            }
            // Resolve file chooser callback (needed for WebView file input)
            fileChooserCallback?.onReceiveValue(
                if (uri != null) arrayOf(uri) else arrayOf()
            )
        } else {
            fileChooserCallback?.onReceiveValue(arrayOf())
        }
        fileChooserCallback = null
    }

    // ── FILE SAVER (export) ────────────────────────────────
    private val fileSaverLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val success = result.resultCode == Activity.RESULT_OK
        if (success && result.data?.data != null) {
            try {
                val uri = result.data!!.data!!
                contentResolver.openOutputStream(uri)?.use { it.write(pendingExportJson!!.toByteArray()) }
                webView.post { webView.evaluateJavascript("window.onExportDone && window.onExportDone(true)", null) }
            } catch (e: Exception) {
                webView.post { webView.evaluateJavascript("window.onExportDone && window.onExportDone(false)", null) }
            }
        } else {
            webView.post { webView.evaluateJavascript("window.onExportDone && window.onExportDone(false)", null) }
        }
        pendingExportJson = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Apply fullscreen before setContentView
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )

        val root = FrameLayout(this)
        root.setBackgroundColor(0xFF0A0A0F.toInt())

        webView = WebView(this)
        webView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )

        configureWebView()

        webView.addJavascriptInterface(WebDashBridge(), "AndroidBridge")

        root.addView(webView)
        setContentView(root)

        webView.loadUrl("file:///android_asset/index.html")
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) makeFullscreen()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        val settings = webView.settings

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.setRenderPriority(WebSettings.RenderPriority.HIGH)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true)
        }
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.mediaPlaybackRequiresUserGesture = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        webView.webChromeClient = object : WebChromeClient() {
            private var customView: View? = null
            private var customViewCallback: CustomViewCallback? = null

            // ── FILE CHOOSER for <input type="file"> ──────
            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                fileChooserCallback?.onReceiveValue(arrayOf())
                fileChooserCallback = callback
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                }
                filePickerLauncher.launch(intent)
                return true
            }

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                customView = view
                customViewCallback = callback
                val decorView = window.decorView as FrameLayout
                decorView.addView(view, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
                ))
                makeFullscreen()
            }

            override fun onHideCustomView() {
                val decorView = window.decorView as FrameLayout
                customView?.let { decorView.removeView(it) }
                customView = null
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
                makeFullscreen()
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = false
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {}
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            if (webView.canGoBack()) { webView.goBack(); return true }
            val js = "document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}));"
            webView.evaluateJavascript(js, null)
            return true
        }
        val arrowKey = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP    -> "ArrowUp"
            KeyEvent.KEYCODE_DPAD_DOWN  -> "ArrowDown"
            KeyEvent.KEYCODE_DPAD_LEFT  -> "ArrowLeft"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "ArrowRight"
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> "Enter"
            else -> null
        }
        if (arrowKey != null) {
            val js = "document.activeElement && document.activeElement !== document.body " +
                "? document.activeElement.dispatchEvent(new KeyboardEvent('keydown',{key:'$arrowKey',bubbles:true})) " +
                ": document.dispatchEvent(new KeyboardEvent('keydown',{key:'$arrowKey',bubbles:true}));"
            webView.evaluateJavascript(js, null)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun makeFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
        }
    }

    private fun escapeJsonForJs(json: String): String {
        return "'" + json.replace("\\", "\\\\").replace("'", "\\'")
            .replace("\n", "\\n").replace("\r", "\\r") + "'"
    }

    override fun onResume() { super.onResume(); webView.onResume() }
    override fun onPause()  { super.onPause();  webView.onPause() }
    override fun onDestroy(){ webView.destroy(); super.onDestroy() }

    // ── JS BRIDGE ─────────────────────────────────────────
    inner class WebDashBridge {

        @JavascriptInterface
        fun exitApp() {
            runOnUiThread { finishAndRemoveTask() }
        }

        @JavascriptInterface
        fun exportConfig(json: String) {
            runOnUiThread {
                pendingExportJson = json
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                    putExtra(Intent.EXTRA_TITLE, "webdash-config.json")
                }
                fileSaverLauncher.launch(intent)
            }
        }

        @JavascriptInterface
        fun goBack() {
            runOnUiThread { if (webView.canGoBack()) webView.goBack() }
        }
    }
}
