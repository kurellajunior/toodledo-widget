import java.util.Properties

plugins {
    id("com.android.application")
}

val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) load(file.inputStream())
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

android {
    namespace = "io.github.kurella.toodledo.widget"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.kurella.toodledo.widget"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "TOODLEDO_CLIENT_ID",
            "\"${localProps.getProperty("TOODLEDO_CLIENT_ID", "")}\"")
        buildConfigField("String", "TOODLEDO_CLIENT_SECRET",
            "\"${localProps.getProperty("TOODLEDO_CLIENT_SECRET", "")}\"")
        buildConfigField("String", "TOODLEDO_REDIRECT_URI",
            "\"toodledowidget://oauth/callback\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.all { it.useJUnitPlatform() }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.security:security-crypto:1.1.0")
    implementation("androidx.work:work-runtime-ktx:2.11.1")
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    implementation("org.json:json:20251224")

    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
