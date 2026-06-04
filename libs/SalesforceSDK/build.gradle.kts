rootProject.ext["PUBLISH_GROUP_ID"] = "com.salesforce.mobilesdk"
rootProject.ext["PUBLISH_VERSION"] = "14.0.0"
rootProject.ext["PUBLISH_ARTIFACT_ID"] = "SalesforceSDK"

plugins {
    `android-library`
    `kotlin-android`
    `publish-module`
    jacoco
    kotlin("plugin.serialization") version "2.3.20"
    kotlin("plugin.parcelize")
    kotlin("plugin.compose")
    id("org.jetbrains.dokka")
}

dependencies {
    api(project(":libs:SalesforceAnalytics"))
    api(libs.okhttp)
    api(libs.firebase.messaging)
    api(libs.androidx.core)
    api(libs.androidx.browser)
    api(libs.androidx.work.runtime.ktx)

    implementation(libs.play.integrity)
    implementation(libs.accompanist.drawablepainter)
    implementation(libs.material)  // remove this when all XML is gone
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.window)
    implementation(libs.androidx.window.core)
    implementation(libs.androidx.compose.material3)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.runtime.livedata)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.material.icons.extended)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.arch.core.testing)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.mockk.android)
}

android { // TODO: This cannot be resolved until newDSL=true
    namespace = "com.salesforce.androidsdk"
    testNamespace = "com.salesforce.androidsdk.tests"

    //noinspection GradleDependency
    compileSdk = 36 // TODO: MSDK 14 will remain on 36.  The next increment will be in MSDK 15.

    defaultConfig {
        minSdk = 31
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        debug {
            enableAndroidTestCoverage = true
        }
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

        getByName("androidTest") {
            setRoot("../test/SalesforceSDKTest")
            java.directories.add("../test/SalesforceSDKTest/src")
            resources.directories.add("../test/SalesforceSDKTest/src")
            res.directories.add("../test/SalesforceSDKTest/res")
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
        aidl = true
        buildConfig = true
        compose = true
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

        sourceDirectories.setFrom(files("${project.projectDir}/src/main/java"))
        val fileFilter = listOf("**/R.class", "**/R$*.class", "**/BuildConfig.*", "**/Manifest*.*", "**/*Test*.*", "android/**/*.*")
        val javaTree = fileTree("${project.projectDir}/build/intermediates/javac/debug") { setExcludes(fileFilter) }
        val kotlinTree = fileTree("${project.projectDir}/build/tmp/kotlin-classes/debug") { setExcludes(fileFilter) }
        classDirectories.setFrom(javaTree, kotlinTree)
        executionData.setFrom(fileTree("$rootDir/firebase") { setIncludes(listOf("**/coverage.ec")) })
    }
}

kotlin {
    jvmToolchain(17)
}
