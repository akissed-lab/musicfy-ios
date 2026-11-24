package com.example.musicfy.settings

/**
 * iOS actuals for the settings bridges.
 *
 * TODO(WP3/WP5): replace with UIDocumentPickerViewController and WKWebView-backed
 * implementations. The current versions keep the shared code compiling and fail
 * safely until the native bridges are wired.
 */
actual object SettingsFilePicker {
    actual suspend fun pickFile(vararg extensions: String): String? = null
    actual suspend fun pickFolder(): String? = null
}

actual interface SettingsWebViewController {
    actual fun loadUrl(url: String)
    actual fun evaluateJavascript(script: String, callback: ((String?) -> Unit)?)
    actual fun close()
}

private class IosSettingsWebViewController(
    private val onPageFinished: (String) -> Unit,
) : SettingsWebViewController {
    override fun loadUrl(url: String) {
        // TODO: WKWebView.load(NSURLRequest)
    }

    override fun evaluateJavascript(script: String, callback: ((String?) -> Unit)?) {
        // TODO: WKWebView.evaluateJavaScript
        callback?.invoke(null)
    }

    override fun close() = Unit
}

actual fun createSettingsWebView(onPageFinished: (String) -> Unit): SettingsWebViewController =
    IosSettingsWebViewController(onPageFinished)
