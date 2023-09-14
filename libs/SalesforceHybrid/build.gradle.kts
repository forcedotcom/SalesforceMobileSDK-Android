plugins {
    `android-library`
    `kotlin-android`
    `maven-publish`
    signing
}

dependencies {
    api(project(":libs:MobileSync"))
    api("org.apache.cordova:framework:11.0.0")
    api("androidx.appcompat:appcompat:1.6.1")
    api("androidx.appcompat:appcompat-resources:1.6.1")
    api("androidx.webkit:webkit:1.6.0")
    api("androidx.core:core-splashscreen:1.0.0")
    implementation("androidx.core:core-ktx:1.9.0")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
}

android {
    namespace = "com.salesforce.androidsdk.hybrid"
    testNamespace = "com.salesforce.androidsdk.phonegap"

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
            setRoot("../test/SalesforceHybridTest")
            java.srcDirs(arrayOf("../test/SalesforceHybridTest/src"))
            resources.srcDirs(arrayOf("../test/SalesforceHybridTest/src"))
            res.srcDirs(arrayOf("../test/SalesforceHybridTest/res"))
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
                artifactId = "SalesforceHybrid"
                groupId = "com.salesforce.mobilesdk"
                version = "11.1.0"
                from(components["release"])
                artifact(sourcesJar)
                pom {
                    name.set("SalesforceHybrid")
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
