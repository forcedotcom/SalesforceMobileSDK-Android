@file:Suppress("UnstableApiUsage")

rootProject.ext["PUBLISH_GROUP_ID"] = "com.salesforce.mobilesdk"
rootProject.ext["PUBLISH_VERSION"] = "12.2.0"
rootProject.ext["PUBLISH_ARTIFACT_ID"] = "SalesforceAnalytics"

plugins {
    `android-library`
    `kotlin-android`
    `publish-module`
    jacoco
}

dependencies {
    api("com.squareup:tape:1.2.3")
    api("io.github.pilgr:paperdb:2.7.2")
    implementation("androidx.core:core-ktx:1.13.1")
    androidTestImplementation("androidx.test:runner:1.6.0")
    androidTestImplementation("androidx.test:rules:1.6.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.0")
}

android {
    namespace = "com.salesforce.androidsdk.analytics"
    testNamespace = "com.salesforce.androidsdk.analytics.tests"

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
            setRoot("../test/SalesforceAnalyticsTest")
            java.srcDirs(arrayOf("../test/SalesforceAnalyticsTest/src"))
            resources.srcDirs(arrayOf("../test/SalesforceAnalyticsTest/src"))
            res.srcDirs(arrayOf("../test/SalesforceAnalyticsTest/res"))
        }
    }

    packaging {
        resources {
            excludes += setOf("META-INF/LICENSE", "META-INF/LICENSE.txt", "META-INF/DEPENDENCIES", "META-INF/NOTICE")
        }
    }

    defaultConfig {
        testApplicationId = "com.salesforce.androidsdk.analytics.tests"
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