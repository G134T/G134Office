package org.g134.office

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

/**
 * Первый экран мобильной версии.
 * Пока показывает веб-лайт, чтобы APK сразу умел писать без входа.
 * Настоящий редактор должен заменить WebView, не ломая форматы из docs/MOBILE_HANDOFF.md.
 */
class MainActivity : AppCompatActivity() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val web = WebView(this)
        web.webViewClient = WebViewClient()
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.loadUrl("https://g134t.github.io/G134Office/")
        setContentView(web)
    }
}
