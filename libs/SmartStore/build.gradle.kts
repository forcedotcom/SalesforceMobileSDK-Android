@file:Suppress("UnstableApiUsage")

rootProject.ext["PUBLISH_GROUP_ID"] = "com.salesforce.mobilesdk"
rootProject.ext["PUBLISH_VERSION"] = "12.1.0"
rootProject.ext["PUBLISH_ARTIFACT_ID"] = "SmartStore"

plugins {
    `android-library`
    `kotlin-android`
    `publish-module`
}

dependencies {
    api(project(":libs:SalesforceSDK"))
    //noinspection GradleDependency -  Needs to line up with supported SQLCipher version.
    api("androidx.sqlite:sqlite:2.2.0")
    api("net.zetetic:sqlcipher-android:4.5.6")
    implementation("androidx.core:core-ktx:1.12.0")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    implementation("com.google.android.material:material:1.10.0")
}

android {
    namespace = "com.salesforce.androidsdk.smartstore"
    testNamespace = "com.salesforce.androidsdk.smartstore.tests"

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
            jniLibs.srcDir("libs")
        }

        getByName("androidTest") {
            setRoot("../test/SmartStoreTest")
            java.srcDirs(arrayOf("../test/SmartStoreTest/src"))
            resources.srcDirs(arrayOf("../test/SmartStoreTest/src"))
            res.srcDirs(arrayOf("../test/SmartStoreTest/res"))
        }
    }

    packaging {
        resources {
            excludes += setOf("META-INF/LICENSE", "META-INF/LICENSE.txt", "META-INF/DEPENDENCIES", "META-INF/NOTICE")
            pickFirsts += setOf("protobuf.meta")
        }
    }

    defaultConfig {
        testApplicationId = "com.salesforce.androidsdk.smartstore.tests"
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
}
