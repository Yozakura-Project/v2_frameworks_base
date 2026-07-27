package com.android.axion.compose.host

import android.content.Context
import android.util.AttributeSet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.AbstractComposeView

/**
 * Yozakura DynamicBar: subclassable ComposeView-equivalent host for the ported keyguard chip
 * section. androidx ComposeView is final, so this mirrors its minimal implementation against the
 * open [AbstractComposeView]. LineageOS 23.2 keyguard root installs the ViewTree owners, so no
 * extra window bootstrapping is required (cf. AodPromotedNotificationSection, a plain ComposeView).
 */
class AxComposeView
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AbstractComposeView(context, attrs, defStyleAttr) {

    // Held as a MutableState val (not a delegated var) so the generated accessor does not clash
    // with the explicit setContent() JVM signature. Mirrors androidx ComposeView.
    private val content = mutableStateOf<(@Composable () -> Unit)?>(null)

    override var shouldCreateCompositionOnAttachedToWindow: Boolean = false
        private set

    @Composable
    override fun Content() {
        content.value?.invoke()
    }

    fun setContent(content: @Composable () -> Unit) {
        shouldCreateCompositionOnAttachedToWindow = true
        this.content.value = content
        if (isAttachedToWindow) {
            createComposition()
        }
    }
}
