@file:Suppress("UnstableApiUsage")

rootProject.ext["PUBLISH_GROUP_ID"] = "com.salesforce.mobilesdk"
rootProject.ext["PUBLISH_VERSION"] = "12.2.0"
rootProject.ext["PUBLISH_ARTIFACT_ID"] = "SalesforceSDK"

plugins {
    `android-library`
    `kotlin-android`
    `publish-module`
    jacoco
}

dependencies {
    api(project(":libs:SalesforceAnalytics"))
    api("com.squareup.okhttp3:okhttp:4.10.0")
    api("com.google.firebase:firebase-messaging:24.0.0")
    api("androidx.core:core:1.13.1")
    api("androidx.browser:browser:1.8.0")
    api("androidx.work:work-runtime-ktx:2.9.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.biometric:biometric:1.2.0-alpha05")
    implementation("androidx.lifecycle:lifecycle-extensions:2.2.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.2")
    implementation("androidx.window:window:1.3.0")
    implementation("androidx.window:window-core:1.3.0")
    androidTestImplementation("androidx.test:runner:1.6.0")
    androidTestImplementation("androidx.test:rules:1.6.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.0")
}

android {
    namespace = "com.salesforce.androidsdk"
    testNamespace = "com.salesforce.androidsdk.tests"

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
            setRoot("../test/SalesforceSDKTest")
            java.srcDirs(arrayOf("../test/SalesforceSDKTest/src"))
            resources.srcDirs(arrayOf("../test/SalesforceSDKTest/src"))
            res.srcDirs(arrayOf("../test/SalesforceSDKTest/res"))
        }
    }

    packaging {
        resources {
            excludes += setOf("META-INF/LICENSE", "META-INF/LICENSE.txt", "META-INF/DEPENDENCIES", "META-INF/NOTICE")
        }
    }

    defaultConfig {
        testApplicationId = "com.salesforce.androidsdk.tests"
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

    val convertCodeCoverage: TaskProvider<JacocoReport> = tasks.register<JacocoReport>("convertedCodeCoverage") {
        group = "Coverage"
        description = "Convert coverage.ec from Firebase Test Lab to XML that is usable by CodeCov."
    }

    convertCodeCoverage {
        reports {
            xml.required = true
            html.required = true
        }

        sourceDirectories.setFrom("${project.projectDir}/src/main/java")
        val fileFilter = arrayListOf("**/R.class", "**/R\$*.class", "**/BuildConfig.*", "**/Manifest*.*", "**/*Test*.*", "android/**/*.*")
        val javaTree = fileTree("${project.projectDir}/build/intermediates/javac/debug") { setExcludes(fileFilter) }
        val kotlinTree = fileTree("${project.projectDir}/build/tmp/kotlin-classes/debug") { setExcludes(fileFilter) }
        classDirectories.setFrom(javaTree, kotlinTree)
        executionData.setFrom(fileTree("$rootDir/firebase/artifacts/sdcard") { setIncludes(arrayListOf("*.ec")) })
    }
}

kotlin {
    jvmToolchain(17)
}
