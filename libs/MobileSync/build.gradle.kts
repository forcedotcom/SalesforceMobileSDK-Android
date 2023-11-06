@file:Suppress("UnstableApiUsage")

rootProject.ext["PUBLISH_GROUP_ID"] = "com.salesforce.mobilesdk"
rootProject.ext["PUBLISH_VERSION"] = "11.1.0"
rootProject.ext["PUBLISH_ARTIFACT_ID"] = "MobileSync"

plugins {
    `android-library`
    `kotlin-android`
    `publish-module`
}

dependencies {
    api(project(":libs:SmartStore"))
    api("androidx.appcompat:appcompat:1.6.1")
    api("androidx.appcompat:appcompat-resources:1.6.1")
    implementation("androidx.core:core-ktx:1.9.0")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
}

android {
    namespace = "com.salesforce.androidsdk.mobilesync"
    testNamespace = "com.salesforce.androidsdk.mobilesync.tests"

    compileSdk = 33

    defaultConfig {
        minSdk = 24
    }

    buildTypes {
        debug {
            enableAndroidTestCoverage = true
        }
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

        getByName("androidTest") {
            setRoot("../test/MobileSyncTest")
            java.srcDirs(arrayOf("../test/MobileSyncTest/src"))
            resources.srcDirs(arrayOf("../test/MobileSyncTest/src"))
            res.srcDirs(arrayOf("../test/MobileSyncTest/res"))
        }
    }

    packagingOptions {
        resources {
            excludes += setOf("META-INF/LICENSE", "META-INF/LICENSE.txt", "META-INF/DEPENDENCIES", "META-INF/NOTICE")
        }
    }

    defaultConfig {
        testApplicationId = "com.salesforce.androidsdk.mobilesync.tests"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    lint {
        abortOnError = false
        xmlReport = true
    }

    buildFeatures {
        renderScript = true
        aidl = true
    }
}
