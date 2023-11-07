plugins {
    `android-library`
    `maven-publish`
    signing
    kotlin("android")
}

if (rootProject.name == "SalesforceMobileSDK-Android") {
    android {
        publishing {
            singleVariant("release") {
                withSourcesJar()
            }
        }
    }

    val sourcesJar by tasks.creating(Jar::class) {
        from(android.sourceSets.getByName("main").java.srcDirs)
    }

    artifacts {
        archives(sourcesJar)
    }

    afterEvaluate {
        publishing {
            publications {
                create<MavenPublication>("release") {
                    artifactId = rootProject.ext["PUBLISH_ARTIFACT_ID"] as? String
                    groupId = rootProject.ext["PUBLISH_GROUP_ID"] as? String
                    version = rootProject.ext["PUBLISH_VERSION"] as? String
                    from(components["release"])
                    artifact(sourcesJar)
                    pom {
                        name.set(rootProject.ext["PUBLISH_ARTIFACT_ID"] as? String)
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
}
