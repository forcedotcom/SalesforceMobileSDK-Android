plugins {
    android
    `kotlin-android`
    kotlin("plugin.serialization") version "2.2.10"
    kotlin("plugin.compose")
}

dependencies {
    val composeVersion = "1.13.0"

    implementation(project(":libs:SalesforceSDK"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("androidx.compose.runtime:runtime-android:1.11.0")
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.tracing:tracing:1.3.0")
    implementation("com.google.android.material:material:1.13.0")
    androidTestImplementation("androidx.test:runner:1.7.0") {
        exclude("com.android.support", "support-annotations")
    }

    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.appcompat:appcompat-resources:1.7.1")

    androidTestImplementation("androidx.test:rules:1.7.0") {
        exclude("com.android.support", "support-annotations")
    }
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-web:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:$composeVersion")
    androidTestImplementation("androidx.compose.ui:ui-test:1.11.0")
    androidTestUtil("androidx.test:orchestrator:1.6.1")

    implementation("androidx.compose.material3:material3-android:1.4.0")
    implementation(platform("androidx.compose:compose-bom:2026.04.01"))
    implementation("androidx.compose.foundation:foundation-android:$composeVersion")
    implementation("androidx.compose.runtime:runtime-livedata:$composeVersion")
    implementation("androidx.compose.ui:ui-tooling-preview-android:$composeVersion")
    implementation("androidx.compose.material:material:$composeVersion")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:$composeVersion")

    debugImplementation("androidx.compose.ui:ui-tooling:$composeVersion")
    debugImplementation("androidx.compose.ui:ui-test-manifest:$composeVersion")
}

android {
    namespace = "com.salesforce.samples.authflowtester"

    //noinspection GradleDependency
    compileSdk = 36 // TODO: MSDK 14 will remain on 36.  The next increment will be in MSDK 15.

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
        renderScript = true
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
        force("org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.3")
        force("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.6.3")
        force("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
        force("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.6.3")
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