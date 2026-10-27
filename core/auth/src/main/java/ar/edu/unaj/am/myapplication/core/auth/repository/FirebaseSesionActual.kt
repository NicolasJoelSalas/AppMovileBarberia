package ar.edu.unaj.am.myapplication.core.auth.repository

import ar.edu.unaj.am.myapplication.core.domain.repository.SesionActual
import com.google.firebase.auth.FirebaseAuth
import javax.inject.Inject

class FirebaseSesionActual @Inject constructor(private val auth: FirebaseAuth) : SesionActual {
    override fun usuarioId(): String? = auth.currentUser?.uid
}
