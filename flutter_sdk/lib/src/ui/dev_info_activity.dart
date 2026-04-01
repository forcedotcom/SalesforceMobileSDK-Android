import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../core/accounts/user_account.dart';

/// A Flutter widget that displays developer support information.
///
/// Shows detailed information about the current SDK configuration,
/// user accounts, and app state for debugging purposes.
class DevInfoActivity extends StatelessWidget {
  /// The current user account.
  final UserAccount? currentAccount;

  /// The SDK version string.
  final String sdkVersion;

  /// The app name.
  final String appName;

  /// The app version.
  final String appVersion;

  /// Additional info entries to display.
  final Map<String, String> additionalInfo;

  const DevInfoActivity({
    super.key,
    this.currentAccount,
    this.sdkVersion = '2.0.0',
    this.appName = '',
    this.appVersion = '',
    this.additionalInfo = const {},
  });

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Developer Info'),
        actions: [
          IconButton(
            icon: const Icon(Icons.copy),
            tooltip: 'Copy All',
            onPressed: () => _copyAll(context),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          _buildSection('SDK Info', {
            'SDK Version': sdkVersion,
            'Platform': 'Flutter',
          }),
          _buildSection('App Info', {
            'App Name': appName,
            'App Version': appVersion,
          }),
          if (currentAccount != null)
            _buildSection('User Info', {
              'Username': currentAccount!.username ?? 'N/A',
              'User ID': currentAccount!.userId,
              'Org ID': currentAccount!.orgId,
              'Instance URL': currentAccount!.instanceUrl,
              'Community URL': currentAccount!.communityUrl ?? 'N/A',
            }),
          if (additionalInfo.isNotEmpty)
            _buildSection('Additional Info', additionalInfo),
        ],
      ),
    );
  }

  Widget _buildSection(String title, Map<String, String> entries) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.symmetric(vertical: 8),
          child: Text(
            title,
            style: const TextStyle(
              fontSize: 18,
              fontWeight: FontWeight.bold,
            ),
          ),
        ),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(12),
            child: Column(
              children: entries.entries.map((entry) {
                return Padding(
                  padding: const EdgeInsets.symmetric(vertical: 4),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      SizedBox(
                        width: 120,
                        child: Text(
                          entry.key,
                          style: const TextStyle(
                            fontWeight: FontWeight.w500,
                            color: Colors.grey,
                          ),
                        ),
                      ),
                      Expanded(
                        child: SelectableText(
                          entry.value,
                          style: const TextStyle(fontFamily: 'monospace'),
                        ),
                      ),
                    ],
                  ),
                );
              }).toList(),
            ),
          ),
        ),
        const SizedBox(height: 8),
      ],
    );
  }

  void _copyAll(BuildContext context) {
    final buffer = StringBuffer();
    buffer.writeln('=== SDK Info ===');
    buffer.writeln('SDK Version: $sdkVersion');
    buffer.writeln('Platform: Flutter');
    buffer.writeln('\n=== App Info ===');
    buffer.writeln('App Name: $appName');
    buffer.writeln('App Version: $appVersion');

    if (currentAccount != null) {
      buffer.writeln('\n=== User Info ===');
      buffer.writeln('Username: ${currentAccount!.username ?? 'N/A'}');
      buffer.writeln('User ID: ${currentAccount!.userId}');
      buffer.writeln('Org ID: ${currentAccount!.orgId}');
      buffer.writeln('Instance URL: ${currentAccount!.instanceUrl}');
    }

    for (final entry in additionalInfo.entries) {
      buffer.writeln('${entry.key}: ${entry.value}');
    }

    Clipboard.setData(ClipboardData(text: buffer.toString()));
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('Developer info copied to clipboard')),
    );
  }
}

/// A widget that shows manage space options for clearing SDK data.
class ManageSpaceActivity extends StatelessWidget {
  /// Callback to clear SmartStore data.
  final VoidCallback? onClearSmartStore;

  /// Callback to clear user data.
  final VoidCallback? onClearUserData;

  /// Callback to clear all SDK data and log out.
  final VoidCallback? onClearAll;

  const ManageSpaceActivity({
    super.key,
    this.onClearSmartStore,
    this.onClearUserData,
    this.onClearAll,
  });

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Manage Space')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          const Text(
            'Clear cached data to free up storage. This may require you '
            'to re-sync data from Salesforce.',
            style: TextStyle(color: Colors.grey),
          ),
          const SizedBox(height: 24),
          if (onClearSmartStore != null)
            _buildAction(
              context,
              icon: Icons.storage,
              title: 'Clear SmartStore',
              description: 'Remove all locally cached records',
              onTap: () => _confirmAction(
                context,
                'Clear SmartStore?',
                'This will remove all locally cached records. '
                    'Data will need to be re-synced.',
                onClearSmartStore!,
              ),
            ),
          if (onClearUserData != null)
            _buildAction(
              context,
              icon: Icons.person_off,
              title: 'Clear User Data',
              description: 'Remove user-specific cached data',
              onTap: () => _confirmAction(
                context,
                'Clear User Data?',
                'This will remove user-specific cached data.',
                onClearUserData!,
              ),
            ),
          if (onClearAll != null)
            _buildAction(
              context,
              icon: Icons.delete_forever,
              title: 'Clear All Data & Log Out',
              description: 'Remove all data and sign out',
              isDestructive: true,
              onTap: () => _confirmAction(
                context,
                'Clear All Data?',
                'This will remove all data and log you out. '
                    'This action cannot be undone.',
                onClearAll!,
              ),
            ),
        ],
      ),
    );
  }

  Widget _buildAction(
    BuildContext context, {
    required IconData icon,
    required String title,
    required String description,
    required VoidCallback onTap,
    bool isDestructive = false,
  }) {
    return Card(
      child: ListTile(
        leading: Icon(
          icon,
          color: isDestructive ? Colors.red : null,
        ),
        title: Text(
          title,
          style: TextStyle(color: isDestructive ? Colors.red : null),
        ),
        subtitle: Text(description),
        trailing: const Icon(Icons.chevron_right),
        onTap: onTap,
      ),
    );
  }

  Future<void> _confirmAction(
    BuildContext context,
    String title,
    String message,
    VoidCallback onConfirm,
  ) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(title),
        content: Text(message),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            style: TextButton.styleFrom(foregroundColor: Colors.red),
            child: const Text('Confirm'),
          ),
        ],
      ),
    );
    if (confirmed == true) onConfirm();
  }
}

/// A widget for token migration UI.
///
/// Displayed when the SDK needs to migrate authentication tokens
/// from an old format to a new one during an upgrade.
class TokenMigrationActivity extends StatefulWidget {
  /// Callback to perform the migration.
  final Future<bool> Function()? onMigrate;

  /// Callback when migration completes.
  final VoidCallback? onComplete;

  const TokenMigrationActivity({
    super.key,
    this.onMigrate,
    this.onComplete,
  });

  @override
  State<TokenMigrationActivity> createState() =>
      _TokenMigrationActivityState();
}

class _TokenMigrationActivityState extends State<TokenMigrationActivity> {
  bool _isMigrating = false;
  String _status = 'Ready to migrate';

  @override
  void initState() {
    super.initState();
    _startMigration();
  }

  Future<void> _startMigration() async {
    if (widget.onMigrate == null) {
      widget.onComplete?.call();
      return;
    }

    setState(() {
      _isMigrating = true;
      _status = 'Migrating...';
    });

    final success = await widget.onMigrate!();

    if (mounted) {
      setState(() {
        _isMigrating = false;
        _status = success ? 'Migration complete' : 'Migration failed';
      });

      if (success) {
        await Future.delayed(const Duration(seconds: 1));
        widget.onComplete?.call();
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            if (_isMigrating) const CircularProgressIndicator(),
            if (!_isMigrating)
              Icon(
                _status.contains('complete')
                    ? Icons.check_circle
                    : Icons.error,
                size: 64,
                color:
                    _status.contains('complete') ? Colors.green : Colors.red,
              ),
            const SizedBox(height: 24),
            Text(
              _status,
              style: Theme.of(context).textTheme.titleMedium,
            ),
          ],
        ),
      ),
    );
  }
}
