package ar.edu.unaj.am.myapplication

import android.app.Application
import ar.edu.unaj.am.myapplication.di.AppContainer

class ComposeAcademyApp : Application() {

    /** Una única instancia para toda la app: así la lista y el detalle comparten el mismo repositorio. */
    val container: AppContainer by lazy { AppContainer(this) }
}
