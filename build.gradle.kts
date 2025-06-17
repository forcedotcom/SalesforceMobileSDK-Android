import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

apply(plugin = "io.github.gradle-nexus.publish-plugin")
apply(from = "${rootDir}/publish/publish-root.gradle")

buildscript {
    repositories {
        maven("https://plugins.gradle.org/m2/")
        google()
        mavenCentral()
    }

    dependencies {
        classpath("com.android.tools.build:gradle:8.10.1")
        classpath("io.github.gradle-nexus:publish-plugin:1.1.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.20")
        classpath("org.jacoco:org.jacoco.core:0.8.12")
    }
}

allprojects {
    group = "com.salesforce.mobilesdk"
    version = "13.1.0"

    // Ensure that we do not use newer language features that would make the SDK incompatible with
    // apps that do not target the latest version of Kotlin.
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
        compilerOptions {
            freeCompilerArgs.add("-Xopt-in=kotlin.RequiresOptIn")
            apiVersion.set(KotlinVersion.KOTLIN_2_0)
            languageVersion.set(KotlinVersion.KOTLIN_2_0)
        }
    }
}
