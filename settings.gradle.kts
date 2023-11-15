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
