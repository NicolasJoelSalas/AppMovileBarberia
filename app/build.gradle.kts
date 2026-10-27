import java.util.Properties

plugins {
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)

    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    // Genera el código de serialización de las rutas de navegación type-safe (@Serializable)
    alias(libs.plugins.kotlin.serialization)
    // Add the Google services Gradle plugin
    id("com.google.gms.google-services")
}

// Solo datos públicos. Nunca incluir el API secret en la aplicación.
val cloudinaryConfig = Properties().apply {
    val config = rootProject.file("local.properties")
    if (config.exists()) config.inputStream().use { load(it) }
}
fun cloudinaryValue(key: String): String = cloudinaryConfig.getProperty(key, "")
    .takeIf { it.matches(Regex("[A-Za-z0-9_-]+")) }.orEmpty()

val rutasBaseUrl = cloudinaryConfig.getProperty("rutas.baseUrl", "https://router.project-osrm.org/")
    .also { require(it.matches(Regex("https://[A-Za-z0-9.-]+(?::[0-9]+)?/[A-Za-z0-9/_-]*")) && it.endsWith('/')) {
        "rutas.baseUrl debe ser una URL HTTPS que termine en /."
    } }

android {
    namespace = "ar.edu.unaj.am.myapplication"
    compileSdk = 37

    defaultConfig {
        applicationId = "ar.edu.unaj.am.myapplication"
        minSdk = 26
        targetSdk = 37
        buildConfigField("String", "CLOUDINARY_CLOUD_NAME", "\"${cloudinaryValue("cloudinary.cloudName")}\"")
        buildConfigField("String", "CLOUDINARY_UPLOAD_PRESET", "\"${cloudinaryValue("cloudinary.uploadPreset")}\"")
        versionCode = 1
        buildConfigField("String", "RUTAS_BASE_URL", "\"$rutasBaseUrl\"")
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true // BuildConfig.DEBUG: logs de red solo en debug
    }
}

dependencies {
    implementation(libs.hilt.viewmodel.compose)
    implementation("com.google.firebase:firebase-firestore")
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(project(":features:notifications"))
    implementation(libs.androidx.sqlite.bundled)
    // :app agrega los módulos de Hilt de todas las capas y configura las variantes de build.
    implementation(project(":features:auth"))
    implementation(project(":features:turnos"))
    implementation(project(":features:home"))
    implementation(project(":features:cuenta"))
    implementation(project(":core:ui"))
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))
    implementation(project(":core:auth"))


    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    // Navigation 3: el back stack es una lista que manejamos nosotros
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    // Un ViewModelStore por pantalla del back stack (cada entrada tiene su propio ViewModel)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.kotlinx.serialization.core)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform("com.google.firebase:firebase-bom:35.0.0"))
    androidTestImplementation("com.google.firebase:firebase-firestore")

    implementation(platform("com.google.firebase:firebase-bom:35.0.0"))

    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")

}
