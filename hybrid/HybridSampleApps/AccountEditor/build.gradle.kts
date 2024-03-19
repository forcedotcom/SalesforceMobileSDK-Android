@file:Suppress("UnstableApiUsage")

plugins {
    android
    `kotlin-android`
}

dependencies {
    api(project(":libs:SalesforceHybrid"))
    implementation("androidx.core:core-ktx:1.12.0")
}

android {
    namespace = "com.salesforce.samples.accounteditor"

    compileSdk = 34

    defaultConfig {
        compileSdk = 34
        minSdk = 24
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("AndroidManifest.xml")
            java.srcDirs(arrayOf("src"))
            resources.srcDirs(arrayOf("src"))
            aidl.srcDirs(arrayOf("src"))
            renderscript.srcDirs(arrayOf("src"))
            res.srcDirs(arrayOf("res"))
            assets.srcDirs(arrayOf("assets"))
        }
    }

    packaging {
        resources {
            excludes += setOf("META-INF/LICENSE", "META-INF/LICENSE.txt", "META-INF/DEPENDENCIES", "META-INF/NOTICE")
        }
    }

    lint {
        abortOnError = false
        xmlReport = true
    }

    buildFeatures {
        renderScript = true
        aidl = true
        buildConfig = true
    }
}
