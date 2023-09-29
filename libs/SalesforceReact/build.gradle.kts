@file:Suppress("UnstableApiUsage")

import org.apache.tools.ant.taskdefs.condition.Os

/**
 * Use international variant JavaScriptCore
 * International variant includes ICU i18n library and necessary data allowing to use
 * e.g. Date.toLocaleString and String.localeCompare that give correct results
 * when using with locales other than en-US.
 * Note that this variant is about 6MiB larger per architecture than default.
 */
val useIntlJsc = false

rootProject.ext["PUBLISH_GROUP_ID"] = "com.salesforce.mobilesdk"
rootProject.ext["PUBLISH_VERSION"] = "11.1.0"
rootProject.ext["PUBLISH_ARTIFACT_ID"] = "SalesforceReact"

plugins {
    `android-library`
    `kotlin-android`
    `publish-module`
}

dependencies {
    api(project(":libs:MobileSync"))
    api("com.facebook.react:react-native:0.70.6")
    implementation("androidx.core:core-ktx:1.9.0")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")

    // JSC from node_modules
    if (useIntlJsc) {
        androidTestImplementation("org.webkit:android-jsc-intl:+")
    } else {
        androidTestImplementation("org.webkit:android-jsc:+")
    }

}

android {
    namespace = "com.salesforce.androidsdk.reactnative"
    testNamespace = "com.salesforce.androidsdk.reactnative.tests"

    compileSdk = 33

    defaultConfig {
        minSdk = 24
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
            setRoot("../test/SalesforceReactTest")
            java.srcDirs(arrayOf("../test/SalesforceReactTest/src"))
            resources.srcDirs(arrayOf("../test/SalesforceReactTest/src"))
            res.srcDirs(arrayOf("../test/SalesforceReactTest/res"))
        }
    }

    packagingOptions {
        resources {
            excludes += setOf("META-INF/LICENSE", "META-INF/LICENSE.txt", "META-INF/DEPENDENCIES", "META-INF/NOTICE")
        }
    }

    defaultConfig {
        testApplicationId = "com.salesforce.androidsdk.salesforcereact.tests"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    lint {
        abortOnError = false
        xmlReport = true
    }

    buildFeatures {
        renderScript = true
        aidl = true
    }
}

val assetsFolder = File("libs/test/SalesforceReactTest/assets")
val reactTestsBundleFile = File(assetsFolder, "index.android.bundle")

task<Exec>("buildReactTestBundle") {
    if (Os.isFamily(Os.FAMILY_WINDOWS)) {
        commandLine(
            "cmd",
            "/c",
            "node",
            "node_modules/react-native/local-cli/cli.js",
            "bundle",
            "--platform",
            "android",
            "--dev",
            "true",
            "--entry-file",
            "node_modules/react-native-force/test/alltests.js",
            "--bundle-output",
            reactTestsBundleFile.absolutePath,
            "--assets-dest",
            assetsFolder.absolutePath
        )
    } else {
        commandLine(
            "/usr/local/bin/node",
            "node_modules/react-native/local-cli/cli.js",
            "bundle",
            "--platform",
            "android",
            "--dev",
            "true",
            "--entry-file",
            "node_modules/react-native-force/test/alltests.js",
            "--bundle-output",
            reactTestsBundleFile.absolutePath,
            "--assets-dest",
            assetsFolder.absolutePath
        )
    }
}

task("buildReactTestBundleIfNotExists") {
    doLast {
        if (!reactTestsBundleFile.exists()) {
            assetsFolder.mkdirs()

            dependsOn("buildReactTestBundle")
        }
    }
}

afterEvaluate {
    try {

        // Generate react tests bundle first.
        tasks.getByName("preDebugAndroidTestBuild").dependsOn(
            tasks.getByName("buildReactTestBundleIfNotExists")
        )
    } catch (ignored: Throwable) {
        println("The preDebugAndroidTestBuild task was not found.")
    }
}
