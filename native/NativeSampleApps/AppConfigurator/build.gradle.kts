plugins {
    android
    `kotlin-android`
}

dependencies {
    api(project(":libs:SalesforceSDK"))
    implementation("androidx.core:core-ktx:1.16.0")
}

android {
    namespace = "com.salesforce.samples.appconfigurator"

    compileSdk = 36

    defaultConfig {
        targetSdk = 36
        minSdk = 28
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("AndroidManifest.xml")
            java.srcDir("src")
            resources.srcDir("src")
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
