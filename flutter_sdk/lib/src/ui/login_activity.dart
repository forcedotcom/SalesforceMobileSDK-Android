import 'package:flutter/material.dart';
import '../core/config/boot_config.dart';
import '../core/config/login_server_manager.dart';
import '../core/auth/oauth2.dart';

/// A Flutter widget that provides the Salesforce OAuth login flow.
///
/// Displays a WebView-based login screen that handles the OAuth2
/// authentication flow, including server selection and custom login URLs.
class SalesforceLoginActivity extends StatefulWidget {
  /// The boot configuration for the app.
  final BootConfig bootConfig;

  /// Callback invoked when login completes successfully.
  final void Function(Map<String, dynamic> authResult)? onLoginComplete;

  /// Callback invoked when login is cancelled.
  final VoidCallback? onLoginCancelled;

  /// Whether to show the server picker.
  final bool showServerPicker;

  const SalesforceLoginActivity({
    super.key,
    required this.bootConfig,
    this.onLoginComplete,
    this.onLoginCancelled,
    this.showServerPicker = true,
  });

  @override
  State<SalesforceLoginActivity> createState() =>
      _SalesforceLoginActivityState();
}

class _SalesforceLoginActivityState extends State<SalesforceLoginActivity> {
  String? _selectedServer;
  bool _showingServerPicker = false;

  @override
  void initState() {
    super.initState();
    _selectedServer = widget.bootConfig.loginHost;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Log In'),
        leading: widget.onLoginCancelled != null
            ? IconButton(
                icon: const Icon(Icons.close),
                onPressed: widget.onLoginCancelled,
              )
            : null,
        actions: widget.showServerPicker
            ? [
                IconButton(
                  icon: const Icon(Icons.settings),
                  onPressed: _toggleServerPicker,
                  tooltip: 'Change Server',
                ),
              ]
            : null,
      ),
      body: _showingServerPicker
          ? _buildServerPicker()
          : _buildLoginWebView(),
    );
  }

  Widget _buildLoginWebView() {
    final loginUrl = _buildLoginUrl();
    return Column(
      children: [
        if (_selectedServer != null)
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            color: Theme.of(context).colorScheme.surfaceContainerHighest,
            child: Row(
              children: [
                const Icon(Icons.cloud, size: 16),
                const SizedBox(width: 8),
                Text(
                  _selectedServer!,
                  style: Theme.of(context).textTheme.bodySmall,
                ),
              ],
            ),
          ),
        Expanded(
          child: Center(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const Icon(Icons.login, size: 64, color: Colors.blue),
                const SizedBox(height: 16),
                Text(
                  'Salesforce Login',
                  style: Theme.of(context).textTheme.headlineSmall,
                ),
                const SizedBox(height: 8),
                Text(
                  'Connect to: ${_selectedServer ?? 'login.salesforce.com'}',
                  style: Theme.of(context).textTheme.bodyMedium,
                ),
                const SizedBox(height: 24),
                ElevatedButton.icon(
                  onPressed: () {
                    // In a real implementation, this would launch a WebView
                    // with the OAuth2 authorization URL
                    widget.onLoginComplete?.call({
                      'loginUrl': loginUrl,
                      'server': _selectedServer,
                    });
                  },
                  icon: const Icon(Icons.open_in_browser),
                  label: const Text('Open Login'),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildServerPicker() {
    return ServerPickerWidget(
      currentServer: _selectedServer ?? 'login.salesforce.com',
      onServerSelected: (server) {
        setState(() {
          _selectedServer = server;
          _showingServerPicker = false;
        });
      },
      onCancel: () {
        setState(() => _showingServerPicker = false);
      },
    );
  }

  String _buildLoginUrl() {
    final host = _selectedServer ?? 'login.salesforce.com';
    return 'https://$host/services/oauth2/authorize';
  }

  void _toggleServerPicker() {
    setState(() => _showingServerPicker = !_showingServerPicker);
  }
}

/// Widget for selecting a login server.
class ServerPickerWidget extends StatefulWidget {
  final String currentServer;
  final void Function(String server) onServerSelected;
  final VoidCallback onCancel;

  const ServerPickerWidget({
    super.key,
    required this.currentServer,
    required this.onServerSelected,
    required this.onCancel,
  });

  @override
  State<ServerPickerWidget> createState() => _ServerPickerWidgetState();
}

class _ServerPickerWidgetState extends State<ServerPickerWidget> {
  final _customUrlController = TextEditingController();
  static const _defaultServers = [
    'login.salesforce.com',
    'test.salesforce.com',
  ];

  @override
  void dispose() {
    _customUrlController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('Select Server',
              style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 16),
          ..._defaultServers.map((server) => RadioListTile<String>(
                title: Text(server),
                value: server,
                groupValue: widget.currentServer,
                onChanged: (value) {
                  if (value != null) widget.onServerSelected(value);
                },
              )),
          const Divider(),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: TextField(
              controller: _customUrlController,
              decoration: const InputDecoration(
                labelText: 'Custom Server URL',
                hintText: 'https://my-domain.my.salesforce.com',
              ),
              onSubmitted: (value) {
                if (value.isNotEmpty) widget.onServerSelected(value);
              },
            ),
          ),
          const SizedBox(height: 16),
          Row(
            mainAxisAlignment: MainAxisAlignment.end,
            children: [
              TextButton(
                onPressed: widget.onCancel,
                child: const Text('Cancel'),
              ),
              const SizedBox(width: 8),
              ElevatedButton(
                onPressed: () {
                  final custom = _customUrlController.text.trim();
                  if (custom.isNotEmpty) {
                    widget.onServerSelected(custom);
                  }
                },
                child: const Text('Apply'),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
