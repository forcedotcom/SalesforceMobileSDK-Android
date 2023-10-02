include("libs:SalesforceAnalytics")
include("libs:SalesforceSDK")
include("libs:SmartStore")
include("libs:MobileSync")
include("libs:SalesforceHybrid")
include("libs:SalesforceReact")
include("native:NativeSampleApps:RestExplorer")
include("native:NativeSampleApps:MobileSyncExplorer")
include("native:NativeSampleApps:AppConfigurator")
include("native:NativeSampleApps:ConfiguredApp")
include("hybrid:HybridSampleApps:AccountEditor")
include("hybrid:HybridSampleApps:MobileSyncExplorerHybrid")

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        // All of React Native (JS, Objective-C sources, Android binaries) is installed from NPM.
        maven("${rootProject.projectDir}/libs/SalesforceReact/node_modules/react-native/android") // For stand-alone MSDK builds.
        maven("${rootProject.projectDir}/../../node_modules/react-native/android") // For template app builds.
        // Android JSC is installed from NPM.
        maven("${rootProject.projectDir}/libs/SalesforceReact/node_modules/jsc-android/dist") // For stand-alone MSDK builds.
        maven("${rootProject.projectDir}/../../node_modules/jsc-android/dist") // For template app builds.
        google()
        mavenCentral()
    }
}
