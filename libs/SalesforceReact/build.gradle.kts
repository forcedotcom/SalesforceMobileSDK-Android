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
rootProject.ext["PUBLISH_VERSION"] = "14.0.0"
rootProject.ext["PUBLISH_ARTIFACT_ID"] = "SalesforceReact"

plugins {
    `android-library`
    `kotlin-android`
    `publish-module`
    jacoco
    id("org.jetbrains.dokka")
}

dependencies {
    api(project(":libs:MobileSync"))
    api(libs.react.android) // TODO: This update should happen in a dedicated work item. ECJ20260423
    implementation(libs.androidx.core.ktx)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.uiautomator)

    // JSC from node_modules
    if (useIntlJsc) {
        androidTestImplementation(libs.android.jsc.intl)
    } else {
        androidTestImplementation(libs.android.jsc)
    }

}

android { // TODO: This cannot be resolved until newDSL=true
    namespace = "com.salesforce.androidsdk.reactnative"
    testNamespace = "com.salesforce.androidsdk.reactnative.tests"

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
            res.directories.add("res")
            assets.directories.add("assets")
        }

        getByName("androidTest") {
            setRoot("../test/SalesforceReactTest")
            java.directories.add("../test/SalesforceReactTest/src")
            resources.directories.add("../test/SalesforceReactTest/src")
            res.directories.add("../test/SalesforceReactTest/res")
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

        sourceDirectories.setFrom(files("${project.projectDir}/src/main/java"))
        val fileFilter = listOf("**/R.class", "**/R\$*.class", "**/BuildConfig.*", "**/Manifest*.*", "**/*Test*.*", "android/**/*.*")
        val javaTree = fileTree("${project.projectDir}/build/intermediates/javac/debug") { setExcludes(fileFilter) }
        val kotlinTree = fileTree("${project.projectDir}/build/tmp/kotlin-classes/debug") { setExcludes(fileFilter) }
        classDirectories.setFrom(javaTree, kotlinTree)
        executionData.setFrom(fileTree("$rootDir/firebase") { setIncludes(listOf("**/coverage.ec")) })
    }
}

val assetsFolder = File("libs/test/SalesforceReactTest/assets")
val reactTestsBundleFile = File(assetsFolder, "index.android.bundle")

tasks.register<Exec>("buildReactTestBundle") {
    if (Os.isFamily(Os.FAMILY_WINDOWS)) {
        commandLine(
            "cmd",
            "/c",
            "node",
            "node_modules/react-native/cli.js",
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
            "node",
            "node_modules/react-native/cli.js",
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

tasks.register("buildReactTestBundleIfNotExists") {
    dependsOn("buildReactTestBundle")
    onlyIf { !reactTestsBundleFile.exists() }
    doFirst {
        assetsFolder.mkdirs()
    }
}

afterEvaluate {
    try {

        // Generate react tests bundle first.
        tasks.getByName("preDebugAndroidTestBuild").dependsOn(
            tasks.getByName("buildReactTestBundleIfNotExists")
        )
    } catch (_: Throwable) {
        println("The preDebugAndroidTestBuild task was not found.")
    }
}
