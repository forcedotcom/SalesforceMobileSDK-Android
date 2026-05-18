plugins {
    android
    `kotlin-android`
    kotlin("plugin.serialization") version "2.3.20"
    kotlin("plugin.compose")
}

dependencies {
    implementation(project(":libs:SalesforceSDK"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.compose.runtime.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.tracing)
    implementation(libs.material)
    androidTestImplementation(libs.androidx.test.runner) {
        exclude("com.android.support", "support-annotations")
    }

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.appcompat.resources)

    androidTestImplementation(libs.androidx.test.rules) {
        exclude("com.android.support", "support-annotations")
    }
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.espresso.web)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.compose.ui.test)
    androidTestUtil(libs.androidx.test.orchestrator)

    implementation(libs.androidx.compose.material3)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.runtime.livedata)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

android { // TODO: This cannot be resolved until newDSL=true
    namespace = "com.salesforce.samples.authflowtester"

    compileSdk = 37

    defaultConfig {
        targetSdk = 37
        minSdk = 28
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
    }

    testOptions {
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }

    buildFeatures {
        compose = true
        aidl = true
        buildConfig = true
    }

    buildTypes {
        debug {
            enableAndroidTestCoverage = true
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/DEPENDENCIES",
                "META-INF/NOTICE"
            )
        }
    }

    sourceSets {
        getByName("main") {
            assets.directories.add("${rootDir}/shared/test")
        }
        getByName("androidTest") {
            java.directories.add("src/androidTest/java")
        }
    }

}

configurations.all {
    resolutionStrategy {
        force("androidx.test:runner:1.7.0")
        force("androidx.test:rules:1.6.1")
        force("androidx.test.espresso:espresso-core:3.7.0")
        force("androidx.test.espresso:espresso-web:3.7.0")
    }
}

repositories {
    google()
    mavenCentral()
}

kotlin {
    jvmToolchain(17)
}
