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
rootProject.ext["PUBLISH_VERSION"] = "13.0.0"
rootProject.ext["PUBLISH_ARTIFACT_ID"] = "SalesforceReact"

plugins {
    `android-library`
    `kotlin-android`
    `publish-module`
    jacoco
}

dependencies {
    api(project(":libs:MobileSync"))
    api("com.facebook.react:react-android:0.74.5")
    implementation("androidx.core:core-ktx:1.15.0")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")

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

    compileSdk = 35

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

    packaging {
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
    if (!reactTestsBundleFile.exists()) {
        assetsFolder.mkdirs()
        dependsOn("buildReactTestBundle")
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
