plugins {
    android
    `kotlin-android`
}

dependencies {
    api(project(":libs:SalesforceHybrid"))
    implementation("androidx.core:core-ktx:1.18.0")
}

android { // TODO: This cannot be resolved until newDSL=true
    namespace = "com.salesforce.samples.accounteditor"

    //noinspection GradleDependency
    compileSdk = 36 // TODO: MSDK 14 will remain on 36.  The next increment will be in MSDK 15.

    defaultConfig {
        targetSdk = 37
        minSdk = 28
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("AndroidManifest.xml")
            java.directories.add("src")
            resources.directories.add("src")
            aidl.directories.add("src")
            res.directories.add("res")
            assets.directories.add("assets")
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
        aidl = true
        buildConfig = true
    }
}
