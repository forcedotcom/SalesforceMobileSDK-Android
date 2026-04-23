@file:Suppress("UnstableApiUsage")

rootProject.ext["PUBLISH_GROUP_ID"] = "com.salesforce.mobilesdk"
rootProject.ext["PUBLISH_VERSION"] = "14.0.0"
rootProject.ext["PUBLISH_ARTIFACT_ID"] = "SalesforceHybrid"

plugins {
    `android-library`
    `kotlin-android`
    `publish-module`
    jacoco
}

dependencies {
    api(project(":libs:MobileSync"))
    api("org.apache.cordova:framework:14.0.1") // TODO: This update should happen in a dedicated work effort. ECJ20260423
    api("androidx.appcompat:appcompat:1.7.1")
    api("androidx.appcompat:appcompat-resources:1.7.1")
    api("androidx.webkit:webkit:1.15.0")
    api("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.core:core-ktx:1.18.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}

android {
    namespace = "com.salesforce.androidsdk.hybrid"
    testNamespace = "com.salesforce.androidsdk.phonegap"

    //noinspection GradleDependency
    compileSdk = 36 // TODO: MSDK 14 will remain on 36.  The next increment will be in MSDK 15.

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
            java.directories.add("src")
            resources.directories.add("src")
            aidl.directories.add("src")
            renderscript.directories.add("src")
            res.directories.add("res")
            assets.directories.add("assets")
        }

        getByName("androidTest") {
            setRoot("../test/SalesforceHybridTest")
            java.directories.add("../test/SalesforceHybridTest/src")
            resources.directories.add("../test/SalesforceHybridTest/src")
            res.directories.add("../test/SalesforceHybridTest/res")
        }
    }

    packaging {
        resources {
            excludes += setOf("META-INF/LICENSE", "META-INF/LICENSE.txt", "META-INF/DEPENDENCIES", "META-INF/NOTICE")
        }
    }

    defaultConfig {
        testApplicationId = "com.salesforce.androidsdk.salesforcehybrid.tests"
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
