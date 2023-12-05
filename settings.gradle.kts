import org.gradle.api.initialization.resolve.RepositoriesMode.FAIL_ON_PROJECT_REPOS

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(FAIL_ON_PROJECT_REPOS)
    repositories {
        // Android JSC is installed from NPM.
        maven("${rootProject.projectDir}/libs/SalesforceReact/node_modules/jsc-android/dist") // For stand-alone MSDK builds.
        maven("${rootProject.projectDir}/../../node_modules/jsc-android/dist") // For template app builds.
        google()
        mavenCentral()
    }
}

rootProject.name = "SalesforceMobileSDK-Android"

include("libs:SalesforceAnalytics")
include("libs:SalesforceSDK")
include("libs:SmartStore")
include("libs:MobileSync")
include("libs:SalesforceHybrid")
include("libs:SalesforceReact")
include("hybrid:HybridSampleApps:AccountEditor")
include("native:NativeSampleApps:AppConfigurator")
include("native:NativeSampleApps:ConfiguredApp")
include("hybrid:HybridSampleApps:MobileSyncExplorerHybrid")
include("native:NativeSampleApps:RestExplorer")
