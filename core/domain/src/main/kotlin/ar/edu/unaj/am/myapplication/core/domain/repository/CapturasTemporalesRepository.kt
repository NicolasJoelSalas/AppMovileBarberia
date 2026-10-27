package ar.edu.unaj.am.myapplication.core.domain.repository

interface CapturasTemporalesRepository {
    suspend fun crear(): String
    suspend fun descartar(uri: String)
}
