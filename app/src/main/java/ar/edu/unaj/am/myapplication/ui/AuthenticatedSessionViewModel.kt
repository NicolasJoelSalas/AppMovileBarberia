package ar.edu.unaj.am.myapplication.ui

import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/** Conserva las pantallas al rotar, pero libera sus datos al cerrar sesión. */
class AuthenticatedSessionViewModel : ViewModel(), ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
    override lateinit var defaultViewModelProviderFactory: ViewModelProvider.Factory
    override var defaultViewModelCreationExtras: CreationExtras = CreationExtras.Empty
    override val viewModelStore = ViewModelStore()
    fun clearSession() = viewModelStore.clear()
    override fun onCleared() = clearSession()
}
