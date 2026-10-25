package ar.edu.unaj.am.myapplication.core.ui.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** Resolve messages in the UI so a retained ViewModel does not retain the old language. */
data class UiMessage(@param:StringRes val resource: Int, val arguments: List<Any> = emptyList()) {
    @Composable
    fun asString(): String = stringResource(resource, *arguments.toTypedArray())
}
