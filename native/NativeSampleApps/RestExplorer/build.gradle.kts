plugins {
    android
    `kotlin-android`
}

dependencies {
    implementation(project(":libs:SalesforceSDK"))
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
    androidTestImplementation(libs.androidx.test.espresso.core) {
        exclude("com.android.support", "support-annotations")
    }
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.uiautomator)
}

android { // TODO: This cannot be resolved until newDSL=true
    namespace = "com.salesforce.samples.restexplorer"
    testNamespace = "com.salesforce.samples.restexplorer.tests"

    compileSdk = 37

    defaultConfig {
        targetSdk = 37
        minSdk = 31
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
            setRoot("../test/RestExplorerTest")
            java.directories.add("../test/RestExplorerTest/src")
            resources.directories.add("../test/RestExplorerTest/src")
            res.directories.add("../test/RestExplorerTest/res")
        }
    }

    packaging {
        resources {
            excludes += setOf("META-INF/LICENSE", "META-INF/LICENSE.txt", "META-INF/DEPENDENCIES", "META-INF/NOTICE")
            pickFirsts += setOf("protobuf.meta")
        }
    }

    defaultConfig {
        testApplicationId = "com.salesforce.samples.restexplorer.tests"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    lint {
        abortOnError = false
        xmlReport = true
    }

    buildFeatures {
        aidl = true
        buildConfig = true
    }

    kotlin {
        jvmToolchain(17)
    }
}
