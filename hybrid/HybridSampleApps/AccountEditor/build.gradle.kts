plugins {
    android
    `kotlin-android`
}

dependencies {
    api(project(":libs:SalesforceHybrid"))
    implementation("androidx.core:core-ktx:1.16.0") // Update requires API 36 compileSdk
}

android {
    namespace = "com.salesforce.samples.accounteditor"

    compileSdk = 36

    defaultConfig {
        targetSdk = 36
        minSdk = 28
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("AndroidManifest.xml")
            java.srcDirs("src")
            resources.srcDirs("src")
            aidl.srcDirs("src")
            renderscript.srcDirs("src")
            res.srcDirs("res")
            assets.srcDirs("assets")
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
