pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ComposeAcademy"
include(":app")

// Capa de presentación: un módulo por feature (una capacidad del usuario), con sus pantallas adentro.
// Se separa un detalle en su propio módulo solo si se abre desde varios lugares (ver docs/decisiones.md).
include(":features:todos")
include(":features:books")
include(":features:playground")


// Capas compartidas
include(":core:domain")
include(":core:data")
include(":core:network")
include(":core:database")
include(":core:ui")
include(":features:auth")
include(":core:auth")
include(":features:turnos")
include(":features:home")
