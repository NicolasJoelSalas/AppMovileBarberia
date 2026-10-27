package ar.edu.unaj.am.myapplication.core.domain.repository

interface DispositivoRepository {
    suspend fun registrar(instalacionId: String, token: String)
    suspend fun desvincular(instalacionId: String)
}
