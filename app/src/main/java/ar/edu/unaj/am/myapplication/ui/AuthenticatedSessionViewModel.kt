package ar.edu.unaj.am.myapplication.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/** Conserva las pantallas al rotar, pero libera sus datos al cerrar sesión. */
class AuthenticatedSessionViewModel : ViewModel(), ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
    fun clearSession() = viewModelStore.clear()
    override fun onCleared() = clearSession()
}
