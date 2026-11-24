apply(plugin = "io.github.gradle-nexus.publish-plugin")
apply(from = "${rootDir}/publish/publish-root.gradle")

buildscript {
    repositories {
        maven("https://plugins.gradle.org/m2/")
        google()
        mavenCentral()
    }

    dependencies {
        /*
         * Keep AGP 9.1.1 for MSDK 14.x; defer AGP 9.2+ until MSDK 15.0.
         * Biometric 1.4.0-alpha07 declares minCompileSdk=36 and
         * minCompileMinorSdk=1. AGP 9.1.1 ignores the minor field, so 36.0
         * passes its metadata check. AGP 9.2 enforces it, making
         * :libs:MobileSync:checkReleaseAarMetadata fail at 36.0. Using 36.1+
         * resolves the error but raises consumer build requirements.
         *
         * A MobileSync lintAnalyzeDebug stall was reported with AGP 9.2.0 and
         * reproduced with 9.2.1. The verified workaround is setting
         * android.experimental.lint.version=9.3.3 in gradle.properties.
         * Bundled lint passes on AGP 9.1.1 without that override. For MSDK
         * 15.0, retest :libs:MobileSync:lintDebug before adding a lint pin.
         */
        classpath("com.android.tools.build:gradle:9.1.1")
        classpath("io.github.gradle-nexus:publish-plugin:2.0.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.20")
        classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.3.21")
        classpath("org.jacoco:org.jacoco.core:0.8.14")
        classpath("org.jetbrains.dokka:dokka-gradle-plugin:2.0.0")
    }
}

allprojects {
    group = "com.salesforce.mobilesdk"
    version = "14.1.0"

    // Ensure that we do not use newer language features that would make the SDK incompatible with
    // apps that do not target the latest version of Kotlin.
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
        compilerOptions {
            freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
            apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
            languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
        }
    }
}

apply(plugin = "org.jetbrains.dokka")

extensions.configure<org.jetbrains.dokka.gradle.DokkaExtension> {
    dokkaPublications.named("html") {
        outputDirectory.set(rootDir.resolve("doc"))
    }
}

dependencies {
    add("dokka", project(":libs:SalesforceAnalytics"))
    add("dokka", project(":libs:SalesforceSDK"))
    add("dokka", project(":libs:SmartStore"))
    add("dokka", project(":libs:MobileSync"))
    add("dokka", project(":libs:SalesforceHybrid"))
}

tasks.register<Jar>("javadocJar") {
    from(tasks.named("dokkaGeneratePublicationHtml"))
    archiveClassifier.set("javadoc")
}
