package ca.cgagnier.wlednativeandroid.ui.components

import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.compose.ui.graphics.Color
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CustomWebViewClientTest {

    private fun createClientWithLoadingState(): Pair<CustomWebViewClient, WebViewState> {
        val state = WebViewState(WebContent.NavigatorOnly).apply {
            loadingState = LoadingState.Loading(0.2f)
        }
        val client = CustomWebViewClient().apply {
            this.state = state
        }
        return client to state
    }

    @Test
    fun `onReceivedError marks loading finished and sets isError for main frame error`() {
        val (client, state) = createClientWithLoadingState()
        val request = mockk<WebResourceRequest>(relaxed = true) {
            every { isForMainFrame } returns true
        }
        val error = mockk<WebResourceError>(relaxed = true)

        client.onReceivedError(view = null, request = request, error = error)

        assertTrue(state.isError)
        assertEquals(LoadingState.Finished, state.loadingState)
    }

    @Test
    fun `onReceivedError preserves loading state for subframe resource error`() {
        val (client, state) = createClientWithLoadingState()
        val request = mockk<WebResourceRequest>(relaxed = true) {
            every { isForMainFrame } returns false
        }
        val error = mockk<WebResourceError>(relaxed = true)

        client.onReceivedError(view = null, request = request, error = error)

        assertFalse(state.isError)
        assertEquals(LoadingState.Loading(0.2f), state.loadingState)
    }

    @Test
    fun `onPageFinished injects page color and safe area scripts for device url`() {
        val (client, state) = createClientWithLoadingState()
        state.bottomInsetCssPx = 24
        val webView = mockk<WebView>(relaxed = true)

        client.onPageFinished(webView, "http://192.168.1.50/")

        assertEquals(LoadingState.Finished, state.loadingState)
        verify(exactly = 1) { webView.evaluateJavascript(PAGE_COLOR_SCRIPT, null) }
        verify(exactly = 1) { webView.evaluateJavascript(buildSafeAreaScript(24), null) }
    }

    @Test
    fun `buildSafeAreaScript configures viewport-fit cover, css var, and polyfill`() {
        val script = buildSafeAreaScript(24)

        assertTrue(script.contains("viewport-fit=cover"))
        assertTrue(script.contains("--app-safe-bottom"))
        assertTrue(script.contains("window.__ilApplySafeBottom(24);"))
        assertTrue(script.contains("__ilSafeAreaPolyfill"))
    }

    @Test
    fun `onPageFinished skips page color script for about blank`() {
        val (client, state) = createClientWithLoadingState()
        val webView = mockk<WebView>(relaxed = true)

        client.onPageFinished(webView, "about:blank")

        assertEquals(LoadingState.Loading(0.2f), state.loadingState)
        verify(exactly = 0) { webView.evaluateJavascript(any(), any()) }
    }

    @Test
    fun `onReceivedError clears page color for main frame error`() {
        val (client, state) = createClientWithLoadingState()
        var reportedColor: Color? = Color.Red
        state.onPageColorChanged = { reportedColor = it }
        val request = mockk<WebResourceRequest>(relaxed = true) {
            every { isForMainFrame } returns true
        }
        val error = mockk<WebResourceError>(relaxed = true)

        client.onReceivedError(view = null, request = request, error = error)

        assertNull(reportedColor)
    }

    @Test
    fun `onReceivedError preserves page color for subframe error`() {
        val (client, state) = createClientWithLoadingState()
        var callCount = 0
        state.onPageColorChanged = { callCount++ }
        val request = mockk<WebResourceRequest>(relaxed = true) {
            every { isForMainFrame } returns false
        }
        val error = mockk<WebResourceError>(relaxed = true)

        client.onReceivedError(view = null, request = request, error = error)

        assertEquals(0, callCount)
    }
}
