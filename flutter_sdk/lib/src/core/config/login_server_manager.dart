import 'package:shared_preferences/shared_preferences.dart';

/// Represents a Salesforce login server.
class LoginServer {
  final String name;
  final Uri url;
  final bool isCustom;

  const LoginServer({
    required this.name,
    required this.url,
    this.isCustom = false,
  });

  Map<String, dynamic> toJson() => {
        'name': name,
        'url': url.toString(),
        'isCustom': isCustom,
      };

  factory LoginServer.fromJson(Map<String, dynamic> json) {
    return LoginServer(
      name: json['name'],
      url: Uri.parse(json['url']),
      isCustom: json['isCustom'] ?? false,
    );
  }
}

/// Manages available login servers (production, sandbox, custom).
class LoginServerManager {
  static const String _customServersKey = 'sf_custom_login_servers';
  static const String _selectedServerKey = 'sf_selected_login_server';

  /// Production login server.
  static final LoginServer production = LoginServer(
    name: 'Production',
    url: Uri.parse('https://login.salesforce.com'),
  );

  /// Sandbox login server.
  static final LoginServer sandbox = LoginServer(
    name: 'Sandbox',
    url: Uri.parse('https://test.salesforce.com'),
  );

  LoginServer _selectedServer = production;
  List<LoginServer> _customServers = [];

  /// Gets the currently selected login server.
  LoginServer get selectedServer => _selectedServer;

  /// Gets all available login servers.
  List<LoginServer> get allServers =>
      [production, sandbox, ..._customServers];

  /// Sets the selected login server.
  Future<void> setSelectedServer(LoginServer server) async {
    _selectedServer = server;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_selectedServerKey, server.url.toString());
  }

  /// Adds a custom login server.
  Future<void> addCustomServer(String name, Uri url) async {
    final server = LoginServer(name: name, url: url, isCustom: true);
    _customServers.add(server);
    await _saveCustomServers();
  }

  /// Removes a custom login server.
  Future<void> removeCustomServer(LoginServer server) async {
    _customServers.removeWhere((s) => s.url == server.url);
    await _saveCustomServers();
  }

  /// Loads persisted login server settings.
  Future<void> load() async {
    final prefs = await SharedPreferences.getInstance();
    final selectedUrl = prefs.getString(_selectedServerKey);
    if (selectedUrl != null) {
      final server = allServers.cast<LoginServer?>().firstWhere(
            (s) => s?.url.toString() == selectedUrl,
            orElse: () => production,
          );
      _selectedServer = server ?? production;
    }
  }

  Future<void> _saveCustomServers() async {
    final prefs = await SharedPreferences.getInstance();
    final json = _customServers.map((s) => s.toJson()).toList();
    await prefs.setString(_customServersKey, json.toString());
  }
}
