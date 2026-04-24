plugins { `kotlin-dsl` }

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    // TODO: AGP 9.2.0 causes libs:MobileSync:lintAnalyzeDebug to hang.  Review with future versions. ECJ20260423
    implementation("com.android.tools.build:gradle:9.1.1")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.20")
}
