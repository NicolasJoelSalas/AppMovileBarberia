package ar.edu.unaj.am.myapplication.core.ui.theme

import androidx.compose.runtime.compositionLocalOf

enum class AppLanguage(val tag: String) {
    SYSTEM(""), SPANISH("es"), ENGLISH("en");

    companion object {
        fun fromTag(tag: String?) = entries.firstOrNull { it.tag == tag } ?: SYSTEM
    }
}

data class LanguagePreference(
    val selected: AppLanguage = AppLanguage.SYSTEM,
    val onSelect: (AppLanguage) -> Unit = {}
)

val LocalLanguagePreference = compositionLocalOf { LanguagePreference() }
