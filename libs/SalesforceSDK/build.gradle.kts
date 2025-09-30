rootProject.ext["PUBLISH_GROUP_ID"] = "com.salesforce.mobilesdk"
rootProject.ext["PUBLISH_VERSION"] = "13.2.0"
rootProject.ext["PUBLISH_ARTIFACT_ID"] = "SalesforceSDK"

plugins {
    `android-library`
    `kotlin-android`
    `publish-module`
    jacoco
    kotlin("plugin.serialization") version "2.0.21"
}

dependencies {
    val composeVersion = "1.8.2" // Update requires Kotlin 2.
    val livecycleVersion = "2.8.7" // Update requires Kotlin 2.
    val androidXActivityVersion = "1.10.1"

    api(project(":libs:SalesforceAnalytics"))
    api("com.squareup.okhttp3:okhttp:4.12.0")
    api("com.google.firebase:firebase-messaging:25.0.0")
    api("androidx.core:core:1.16.0") // Update requires API 36 compileSdk
    api("androidx.browser:browser:1.8.0") // Update requires API 36 compileSdk
    api("androidx.work:work-runtime-ktx:2.10.3")

    implementation("com.google.android.material:material:1.13.0")  // remove this when all xml is gone
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.biometric:biometric:1.2.0-alpha05")
    implementation("androidx.lifecycle:lifecycle-extensions:2.2.0")
    implementation("androidx.core:core-ktx:1.16.0") // Update requires API 36 compileSdk
    implementation("androidx.activity:activity-ktx:$androidXActivityVersion")
    implementation("androidx.activity:activity-compose:$androidXActivityVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$livecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:$livecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:$livecycleVersion")
    implementation("androidx.lifecycle:lifecycle-service:$livecycleVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3") // Update requires Kotlin 2.
    implementation("androidx.window:window:1.4.0")
    implementation("androidx.window:window-core:1.4.0")
    implementation("androidx.compose.material3:material3-android:1.3.2")
    implementation(platform("androidx.compose:compose-bom:2025.07.00")) // Update requires Kotlin 2.
    implementation("androidx.compose.foundation:foundation-android:$composeVersion")
    implementation("androidx.compose.runtime:runtime-livedata:$composeVersion")
    implementation("androidx.compose.ui:ui-tooling-preview-android:$composeVersion")
    implementation("androidx.compose.material:material:$composeVersion")

    debugImplementation("androidx.compose.ui:ui-tooling:$composeVersion")
    debugImplementation("androidx.compose.ui:ui-test-manifest:$composeVersion")

    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.arch.core:core-testing:2.2.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:$composeVersion")
    androidTestImplementation("io.mockk:mockk-android:1.14.0") // Update requires Kotlin 2
}

android {
    namespace = "com.salesforce.androidsdk"
    testNamespace = "com.salesforce.androidsdk.tests"

    //noinspection GradleDependency - Will be upgraded to 36 in Mobile SDK 14.0
    compileSdk = 35

    defaultConfig {
        minSdk = 28
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
            @Suppress("UnstableApiUsage")
            assets.directories.add("../../shared/test")
        }
    }

    packaging {
        resources {
            excludes += setOf("META-INF/LICENSE*", "META-INF/LICENSE.txt", "META-INF/DEPENDENCIES", "META-INF/NOTICE")
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
        compose = true
    }

    @Suppress("UnstableApiUsage")
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
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
        val fileFilter = arrayListOf("**/R.class", "**/R$*.class", "**/BuildConfig.*", "**/Manifest*.*", "**/*Test*.*", "android/**/*.*")
        val javaTree = fileTree("${project.projectDir}/build/intermediates/javac/debug") { setExcludes(fileFilter) }
        val kotlinTree = fileTree("${project.projectDir}/build/tmp/kotlin-classes/debug") { setExcludes(fileFilter) }
        classDirectories.setFrom(javaTree, kotlinTree)
        executionData.setFrom(fileTree("$rootDir/firebase/artifacts/sdcard") { setIncludes(arrayListOf("*.ec")) })
    }
}

kotlin {
    jvmToolchain(17)
}
