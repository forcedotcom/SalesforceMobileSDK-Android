plugins {
    `android-library`
    `kotlin-android`
    `maven-publish`
    signing
}

dependencies {
    api(project(":libs:SalesforceAnalytics"))
    api("com.squareup.okhttp3:okhttp:4.10.0")
    api("com.google.firebase:firebase-messaging:20.1.0")  // Must remain 20.1.0 until Mobile SDK 12.0
    api("androidx.core:core:1.9.0")
    api("androidx.browser:browser:1.4.0")
    api("androidx.work:work-runtime-ktx:2.8.1")
    implementation("com.google.android.material:material:1.8.0")
    implementation("androidx.biometric:biometric:1.2.0-alpha05")
    implementation("androidx.lifecycle:lifecycle-extensions:2.2.0")
    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.5.1")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
}

android {
    namespace = "com.salesforce.androidsdk"
    testNamespace = "com.salesforce.androidsdk.tests"

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
            setRoot("../test/SalesforceSDKTest")
            java.srcDirs(arrayOf("../test/SalesforceSDKTest/src"))
            resources.srcDirs(arrayOf("../test/SalesforceSDKTest/src"))
            res.srcDirs(arrayOf("../test/SalesforceSDKTest/res"))
        }
    }

    packaging {
        resources {
            excludes += setOf("META-INF/LICENSE", "META-INF/LICENSE.txt", "META-INF/DEPENDENCIES", "META-INF/NOTICE")
        }
    }

    defaultConfig {
        testApplicationId = "com.salesforce.androidsdk.tests"
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
                artifactId = "SalesforceSDK"
                groupId = "com.salesforce.mobilesdk"
                version = "11.1.0"
                from(components["release"])
                artifact(sourcesJar)
                pom {
                    name.set("SalesforceSDK")
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
