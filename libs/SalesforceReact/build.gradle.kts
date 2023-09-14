import org.apache.tools.ant.taskdefs.condition.Os

/**
 * Use international variant JavaScriptCore
 * International variant includes ICU i18n library and necessary data allowing to use
 * e.g. Date.toLocaleString and String.localeCompare that give correct results
 * when using with locales other than en-US.
 * Note that this variant is about 6MiB larger per architecture than default.
 */
val useIntlJsc = false

plugins {
    `android-library`
    `kotlin-android`
    `maven-publish`
    signing
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
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

val sourcesJar by tasks.creating(Jar::class) {
    archiveClassifier.set("sources")
    from(android.sourceSets.getByName("main").java.srcDirs)
}

artifacts {
    archives(sourcesJar)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                artifactId = "SalesforceReact"
                groupId = "com.salesforce.mobilesdk"
                version = "11.1.0"
                from(components["release"])
                artifact(sourcesJar)
                pom {
                    name.set("SalesforceReact")
                    description.set("Official Salesforce Android SDK")
                    url.set("https://github.com/forcedotcom/SalesforceMobileSDK-Android")
                    licenses {
                        license {
                            name.set("The Apache Software License, Version 2.0")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    developers {
                        developer {
                            id.set("bpage")
                            name.set("Brandon Page")
                            email.set("bpage@salesforce.com")
                        }
                        developer {
                            id.set("wmathurin")
                            name.set("Wolfgang Mathurin")
                            email.set("wmathurin@salesforce.com ")
                        }
                        developer {
                            id.set("brianna.birman")
                            name.set("Brianna Birman")
                            email.set("brianna.birman@salesforce.com")
                        }
                        developer {
                            id.set("JohnsonEricAtSalesforce")
                            name.set("Eric C. Johnson")
                            email.set("Johnson.Eric@Salesforce.com")
                        }
                        scm {
                            connection.set("https://github.com/forcedotcom/SalesforceMobileSDK-Android.git")
                            developerConnection.set("https://github.com/forcedotcom/SalesforceMobileSDK-Android.git")
                            url.set("https://github.com/forcedotcom/SalesforceMobileSDK-Android")
                        }
                    }
                }
            }
        }

        signing {
            useInMemoryPgpKeys(
                rootProject.ext["signing.keyId"] as? String,
                rootProject.ext["signing.key"] as? String,
                rootProject.ext["signing.password"] as? String
            )
            sign(publishing.publications)
        }
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
