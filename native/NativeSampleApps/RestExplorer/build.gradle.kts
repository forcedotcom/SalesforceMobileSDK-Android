plugins {
    android
    `kotlin-android`
}

dependencies {
    implementation(project(":libs:SalesforceSDK"))
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
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0") {
        exclude("com.android.support", "support-annotations")
    }
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}

android { // TODO: This cannot be resolved until newDSL=true
    namespace = "com.salesforce.samples.restexplorer"
    testNamespace = "com.salesforce.samples.restexplorer.tests"

    //noinspection GradleDependency
    compileSdk = 36 // TODO: MSDK 14 will remain on 36.  The next increment will be in MSDK 15.

    defaultConfig {
        targetSdk = 37
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
