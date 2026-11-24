// SettingsBridges.kt
//
// Minimal expect-only contracts for the platform facilities settings screens
// need. The integrator supplies the iosMain actuals; nothing in common code
// depends on Android or UIKit types here.

package com.example.musicfy.settings

/** Picks a document or folder and returns a platform URI/path, or null if cancelled. */
expect object SettingsFilePicker {
    suspend fun pickFile(vararg extensions: String): String?
    suspend fun pickFolder(): String?
}

/** Minimal WebView surface used by sign-in and Turnstile challenge settings. */
expect interface SettingsWebViewController {
    fun loadUrl(url: String)
    fun evaluateJavascript(script: String, callback: ((String?) -> Unit)? = null)
    fun close()
}

/** Creates a controller; [onPageFinished] receives each committed URL. */
expect fun createSettingsWebView(onPageFinished: (String) -> Unit): SettingsWebViewController
