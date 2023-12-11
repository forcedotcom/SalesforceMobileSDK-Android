@file:Suppress("UnstableApiUsage")

rootProject.ext["PUBLISH_GROUP_ID"] = "com.salesforce.mobilesdk"
rootProject.ext["PUBLISH_VERSION"] = "11.1.0"
rootProject.ext["PUBLISH_ARTIFACT_ID"] = "SalesforceAnalytics"

plugins {
    `android-library`
    `kotlin-android`
    `publish-module`
}

dependencies {
    api("com.squareup:tape:1.2.3")
    api("io.github.pilgr:paperdb:2.7.2")
    implementation("androidx.core:core-ktx:1.12.0")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
}

android {
    namespace = "com.salesforce.androidsdk.analytics"
    testNamespace = "com.salesforce.androidsdk.analytics.tests"

    compileSdk = 34

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
            setRoot("../test/SalesforceAnalyticsTest")
            java.srcDirs(arrayOf("../test/SalesforceAnalyticsTest/src"))
            resources.srcDirs(arrayOf("../test/SalesforceAnalyticsTest/src"))
            res.srcDirs(arrayOf("../test/SalesforceAnalyticsTest/res"))
        }
    }

    packaging {
        resources {
            excludes += setOf("META-INF/LICENSE", "META-INF/LICENSE.txt", "META-INF/DEPENDENCIES", "META-INF/NOTICE")
        }
    }

    defaultConfig {
        testApplicationId = "com.salesforce.androidsdk.analytics.tests"
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
