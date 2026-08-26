plugins {
    android
    `kotlin-android`
}

dependencies {
    api(project(":libs:SalesforceHybrid"))
    implementation(libs.androidx.core.ktx)
}

android { // TODO: This cannot be resolved until newDSL=true
    namespace = "com.salesforce.samples.accounteditor"

    compileSdk = 37

    defaultConfig {
        targetSdk = 37
        minSdk = 31
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
