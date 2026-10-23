import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Módulo Kotlin puro: no conoce Android. Se compila y testea en la JVM.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ksp)
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
    implementation(project(":core:domain"))

    api(libs.androidx.room3.runtime)
    implementation(libs.androidx.sqlite.bundled)
    implementation(libs.kotlinx.coroutines.core)

    ksp(libs.androidx.room3.compiler)
    add("kspTest", libs.androidx.room3.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
