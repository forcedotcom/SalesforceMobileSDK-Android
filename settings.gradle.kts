val nodeModulesMsdk = File("${rootProject.projectDir}/libs/SalesforceReact/node_modules/react-native/android") // For stand-alone MSDK builds.
val nodeModulesTemplate = File("${rootProject.projectDir}/../../node_modules/react-native/android") // For template app builds.
val hasNodeModules = nodeModulesMsdk.exists() || nodeModulesTemplate.exists()

include("libs:SalesforceAnalytics")
include("libs:SalesforceSDK")
include("libs:SmartStore")
include("libs:MobileSync")
include("libs:SalesforceHybrid")
if (hasNodeModules) {
    include("libs:SalesforceReact")
} else {
    logger.warn("Skipping `SalesforceReact` module since local node repository is missing.  Has `yarn install` been run in the directory containing `package.json`?")
}
include("hybrid:HybridSampleApps:AccountEditor")
include("native:NativeSampleApps:AppConfigurator")
include("native:NativeSampleApps:ConfiguredApp")
include("native:NativeSampleApps:MobileSyncExplorer")
include("hybrid:HybridSampleApps:MobileSyncExplorerHybrid")
include("native:NativeSampleApps:RestExplorer")

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
