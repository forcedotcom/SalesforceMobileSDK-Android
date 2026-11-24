plugins { `kotlin-dsl` }

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    // See the root AGP compatibility note before upgrading for MSDK 15.0.
    implementation("com.android.tools.build:gradle:9.1.1")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.20")
}
