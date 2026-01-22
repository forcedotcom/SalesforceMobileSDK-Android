plugins {
    android
    `kotlin-android`
}

dependencies {
    val composeVersion = "1.8.2" // Update requires Kotlin 2.

    implementation(project(":libs:SalesforceSDK"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation("androidx.compose.runtime:runtime-android:1.10.0")
    implementation("androidx.core:core-ktx:1.16.0") // Update requires API 36 compileSdk
    implementation("androidx.tracing:tracing:1.3.0")
    implementation("com.google.android.material:material:1.13.0")
    androidTestImplementation("androidx.test:runner:1.5.1") {
        exclude("com.android.support", "support-annotations")
    }

    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.appcompat:appcompat-resources:1.7.1")

    androidTestImplementation("androidx.test:rules:1.5.0") {
        exclude("com.android.support", "support-annotations")
    }
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.0") {
        exclude("com.android.support", "support-annotations")
    }
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")

    implementation("androidx.compose.material3:material3-android:1.3.2")
    implementation(platform("androidx.compose:compose-bom:2025.07.00")) // Update requires Kotlin 2.
    implementation("androidx.compose.foundation:foundation-android:$composeVersion")
    implementation("androidx.compose.runtime:runtime-livedata:$composeVersion")
    implementation("androidx.compose.ui:ui-tooling-preview-android:$composeVersion")
    implementation("androidx.compose.material:material:$composeVersion")
    implementation("androidx.activity:activity-compose:$composeVersion")

    debugImplementation("androidx.compose.ui:ui-tooling:$composeVersion")
    debugImplementation("androidx.compose.ui:ui-test-manifest:$composeVersion")
}

android {
    namespace = "com.salesforce.samples.authflowtester"

    compileSdk = 36

    defaultConfig {
        targetSdk = 36
        minSdk = 28
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        renderScript = true
        aidl = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
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
            assets.srcDirs("${rootDir}/shared/test")
        }
        getByName("androidTest") {
            java.srcDirs(
                "src/androidTest/java",
                "${rootDir}/external/SalesforceMobileSDK-UITests/Android/app/src/androidTest/java/PageObjects",
                "${rootDir}/external/SalesforceMobileSDK-UITests/Android/app/src/androidTest/java/TestUtility"
            )
        }
    }

}

repositories {
    google()
    mavenCentral()
}

kotlin {
    jvmToolchain(17)
}