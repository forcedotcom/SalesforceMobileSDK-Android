@file:Suppress("UnstableApiUsage")

rootProject.ext["PUBLISH_GROUP_ID"] = "com.salesforce.mobilesdk"
rootProject.ext["PUBLISH_VERSION"] = "12.0.1"
rootProject.ext["PUBLISH_ARTIFACT_ID"] = "SalesforceHybrid"

plugins {
    `android-library`
    `kotlin-android`
    `publish-module`
}

dependencies {
    api(project(":libs:MobileSync"))
    api("org.apache.cordova:framework:12.0.1")
    api("androidx.appcompat:appcompat:1.6.1")
    api("androidx.appcompat:appcompat-resources:1.6.1")
    api("androidx.webkit:webkit:1.9.0")
    api("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.core:core-ktx:1.12.0")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
}

android {
    namespace = "com.salesforce.androidsdk.hybrid"
    testNamespace = "com.salesforce.androidsdk.phonegap"

    compileSdk = 34

    defaultConfig {
        minSdk = 26
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
            setRoot("../test/SalesforceHybridTest")
            java.srcDirs(arrayOf("../test/SalesforceHybridTest/src"))
            resources.srcDirs(arrayOf("../test/SalesforceHybridTest/src"))
            res.srcDirs(arrayOf("../test/SalesforceHybridTest/res"))
        }
    }

    packaging {
        resources {
            excludes += setOf("META-INF/LICENSE", "META-INF/LICENSE.txt", "META-INF/DEPENDENCIES", "META-INF/NOTICE")
        }
    }

    defaultConfig {
        testApplicationId = "com.salesforce.androidsdk.salesforcehybrid.tests"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    kotlin {
        jvmToolchain(17)
    }
}
