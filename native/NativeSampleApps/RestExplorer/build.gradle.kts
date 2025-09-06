plugins {
    android
    `kotlin-android`
}

dependencies {
    implementation(project(":libs:SalesforceSDK"))
    implementation("androidx.core:core-ktx:1.17.0")
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
}

android {
    namespace = "com.salesforce.samples.restexplorer"
    testNamespace = "com.salesforce.samples.restexplorer.tests"

    compileSdk = 36

    defaultConfig {
        targetSdk = 36
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
            setRoot("../test/RestExplorerTest")
            java.srcDirs(arrayOf("../test/RestExplorerTest/src"))
            resources.srcDirs(arrayOf("../test/RestExplorerTest/src"))
            res.srcDirs(arrayOf("../test/RestExplorerTest/res"))
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
        renderScript = true
        aidl = true
        buildConfig = true
    }

    kotlin {
        jvmToolchain(17)
    }
}
