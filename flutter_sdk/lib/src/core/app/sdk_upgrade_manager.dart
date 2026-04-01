import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Manages SDK version upgrades and data migrations.
///
/// When the SDK version changes, this manager runs the appropriate
/// upgrade steps to migrate data from the old format to the new format.
class SdkUpgradeManager {
  static const String _versionKey = 'sdk_version';
  static const String _prefsName = 'version_info';

  static SdkUpgradeManager? _instance;

  final FlutterSecureStorage _storage;
  final List<UpgradeStep> _upgradeSteps = [];

  SdkUpgradeManager._({FlutterSecureStorage? storage})
      : _storage = storage ?? const FlutterSecureStorage();

  /// Returns the singleton instance.
  static SdkUpgradeManager get instance {
    _instance ??= SdkUpgradeManager._();
    return _instance!;
  }

  /// Registers an upgrade step to be run when upgrading from a version
  /// less than [targetVersion].
  void registerUpgradeStep(UpgradeStep step) {
    _upgradeSteps.add(step);
    _upgradeSteps.sort((a, b) => a.targetVersion.compareTo(b.targetVersion));
  }

  /// Runs all necessary upgrade steps based on the installed version.
  ///
  /// [currentVersion] The current SDK version string (e.g., "2.0.0").
  Future<void> upgrade(String currentVersion) async {
    final installedVersion = await getInstalledVersion();
    if (installedVersion == null || installedVersion.isEmpty) {
      // Fresh install - just record the version
      await _writeVersion(currentVersion);
      return;
    }

    if (installedVersion == currentVersion) return;

    final installed = SdkVersion.parse(installedVersion);
    if (installed == null) {
      await _writeVersion(currentVersion);
      return;
    }

    for (final step in _upgradeSteps) {
      final target = SdkVersion.parse(step.targetVersion);
      if (target != null && installed.isLessThan(target)) {
        await step.execute();
      }
    }

    await _writeVersion(currentVersion);
  }

  /// Returns the currently installed SDK version.
  Future<String?> getInstalledVersion() async {
    return _storage.read(key: '${_prefsName}_$_versionKey');
  }

  Future<void> _writeVersion(String version) async {
    await _storage.write(key: '${_prefsName}_$_versionKey', value: version);
  }
}

/// Represents a single upgrade step that runs when upgrading
/// from a version less than [targetVersion].
abstract class UpgradeStep {
  /// The version that triggers this upgrade step.
  /// If the installed version is less than this, the step runs.
  String get targetVersion;

  /// Executes the upgrade migration.
  Future<void> execute();
}

/// Parsed SDK version for comparison.
class SdkVersion implements Comparable<SdkVersion> {
  final int major;
  final int minor;
  final int patch;

  SdkVersion(this.major, this.minor, this.patch);

  /// Parses a version string like "2.0.0" or "13.2.0".
  static SdkVersion? parse(String version) {
    final parts = version.split('.');
    if (parts.length < 2) return null;
    final major = int.tryParse(parts[0]);
    final minor = int.tryParse(parts[1]);
    final patch = parts.length > 2 ? int.tryParse(parts[2]) ?? 0 : 0;
    if (major == null || minor == null) return null;
    return SdkVersion(major, minor, patch);
  }

  bool isLessThan(SdkVersion other) => compareTo(other) < 0;

  bool isGreaterThanOrEqualTo(SdkVersion other) => compareTo(other) >= 0;

  @override
  int compareTo(SdkVersion other) {
    if (major != other.major) return major.compareTo(other.major);
    if (minor != other.minor) return minor.compareTo(other.minor);
    return patch.compareTo(other.patch);
  }

  @override
  String toString() => '$major.$minor.$patch';
}
