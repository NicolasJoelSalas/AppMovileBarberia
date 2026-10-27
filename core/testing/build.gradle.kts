import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Herramientas compartidas de pruebas. Se publican desde main para que otros
// módulos puedan usarlas mediante testImplementation, sin incluirlas en el APK.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    api(project(":core:domain"))
    api(libs.junit)
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.coroutines.test)
}
