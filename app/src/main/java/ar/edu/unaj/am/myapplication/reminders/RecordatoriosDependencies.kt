package ar.edu.unaj.am.myapplication.reminders

import ar.edu.unaj.am.myapplication.core.domain.repository.RecordatoriosRepository
import ar.edu.unaj.am.myapplication.core.domain.repository.SesionActual
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface RecordatoriosDependencies {
    fun politicaReservas(): ar.edu.unaj.am.myapplication.core.domain.usecase.PoliticaReservas
    fun recordatorios(): RecordatoriosRepository
    fun sesion(): SesionActual
}
