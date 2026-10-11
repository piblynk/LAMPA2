import org.gradle.internal.impldep.com.esotericsoftware.minlog.Log
import java.util.Properties

plugins {
    id("com.android.application")
    id("com.google.devtools.ksp")
}

fun getVersionCode(project: Project): Int {
    return try {
        val output = project.providers.exec {
            commandLine("git", "rev-list", "--count", "origin/main")
        }.standardOutput.asText.get().trim()
        output.toIntOrNull() ?: 1
    } catch (ignored: Exception) {
        1
    }
}

fun getVersionName(project: Project): String {
    return try {
        val output = project.providers.exec {
            commandLine("git", "describe", "--tags", "--dirty")
        }.standardOutput.asText.get().trim()
        if (output.isEmpty()) "0.0.0" else output.replaceFirst("^v", "")
    } catch (ignored: Exception) {
        "0.0.0"
    }
}

fun getRepoId(project: Project): String {
    return try {
        val url = project.providers.exec {
            commandLine("git", "remote", "get-url", "origin")
        }.standardOutput.asText.get().trim()
        val matcher = Regex("""github\.com[:/]([^/]+/[^/.]+)""").find(url)
        matcher?.groupValues?.get(1) ?: "piblynk/LAMPA2"
    } catch (ignored: Exception) {
        "piblynk/LAMPA2"
    }
}

android {
    compileSdk = 37
    namespace = "top.rootu.lampa"

    defaultConfig {
        applicationId = "com.piblynk.lampa2"
        minSdk = 28
        targetSdk = 37
        versionCode = getVersionCode(project)
        versionName = getVersionName(project)

        buildConfigField("String", "REPO_ID", "\"${getRepoId(project)}\"")

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        getByName("debug") {
            val localDebugKeystore = file("keystore/debug.keystore")
            if (localDebugKeystore.exists()) {
                storeFile = localDebugKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }

        create("release") {
            val keystoreProperties = Properties()
            val keystorePropsFile = file("keystore/keystore_config")

            if (keystorePropsFile.exists()) {
                keystorePropsFile.inputStream().use { keystoreProperties.load(it) }
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            } else {
                val keystoreFile = System.getenv("KEYSTORE_FILE")
                if (keystoreFile != null) {
                    storeFile = file(keystoreFile)
                    storePassword = System.getenv("KEYSTORE_PASSWORD")
                    keyAlias = System.getenv("RELEASE_SIGN_KEY_ALIAS")
                    keyPassword = System.getenv("RELEASE_SIGN_KEY_PASSWORD")
                }
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null) {
                signingConfig = releaseSigning
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "DebugProbesKt.bin"
        }
    }

    flavorDimensions += "default"

    productFlavors {
        create("lite") {
            dimension = "default"
            buildConfigField("boolean", "enableUpdate", "true")
            buildConfigField("String", "defaultAppUrl", "\"\"")
        }
        create("full") {
            dimension = "default"
            buildConfigField("boolean", "enableUpdate", "false")
            buildConfigField("String", "defaultAppUrl", "\"\"")
        }
        create("ruStore") {
            dimension = "default"
            versionNameSuffix = "-RuStore"
            buildConfigField("boolean", "enableUpdate", "false")
            buildConfigField("String", "defaultAppUrl", "\"http://lampa.mx\"")
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.2")
    implementation("androidx.core:core-ktx:1.19.0")
    val lifecycleVersion = "2.11.0"
    implementation("androidx.lifecycle:lifecycle-process:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVersion")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.recommendation:recommendation:1.0.0")
    implementation("androidx.tvprovider:tvprovider:1.1.0")
    implementation("androidx.vectordrawable:vectordrawable:1.2.0")
    implementation("androidx.webkit:webkit:1.17.0")

    val glideVersion = "5.0.9"
    implementation("com.github.bumptech.glide:glide:$glideVersion")
    implementation("com.github.bumptech.glide:okhttp3-integration:$glideVersion") {
        exclude(group = "glide-parent")
        isTransitive = false
    }
    ksp("com.github.bumptech.glide:ksp:$glideVersion")
    implementation("com.github.bumptech.glide:annotations:$glideVersion")

    implementation("com.google.android.material:material:1.14.0")
    val okhttpVersion = "5.5.0"
    implementation("com.squareup.okhttp3:okhttp:$okhttpVersion")
    implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:$okhttpVersion")
    implementation("org.brotli:dec:0.1.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("com.google.code.gson:gson:2.14.0")
    implementation("com.airbnb.android:lottie:6.7.1")
    implementation("info.guardianproject.netcipher:netcipher:2.1.0")
    implementation("junit:junit:4.13.2")
    implementation("org.conscrypt:conscrypt-android:2.7.0")
    implementation("org.mozilla:rhino:1.9.1")
}
