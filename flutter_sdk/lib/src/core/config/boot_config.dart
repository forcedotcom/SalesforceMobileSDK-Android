import 'dart:convert';

/// Boot configuration for the Salesforce SDK.
///
/// Defines the connected app settings and initial configuration
/// needed to initialize the SDK.
class BootConfig {
  /// The connected app's consumer key.
  final String clientId;

  /// The OAuth callback URL.
  final String callbackUrl;

  /// OAuth scopes.
  final List<String> scopes;

  /// The login server URL.
  final Uri loginServer;

  /// Whether to use web server authentication (with PKCE).
  final bool useWebServerAuthentication;

  /// Whether to push notifications.
  final bool pushNotificationsEnabled;

  const BootConfig({
    required this.clientId,
    required this.callbackUrl,
    required this.scopes,
    Uri? loginServer,
    this.useWebServerAuthentication = true,
    this.pushNotificationsEnabled = false,
  }) : loginServer = loginServer ?? const _DefaultLoginServer();

  factory BootConfig.fromJson(Map<String, dynamic> json) {
    return BootConfig(
      clientId: json['remoteAccessConsumerKey'] ?? json['clientId'],
      callbackUrl: json['oauthRedirectURI'] ?? json['callbackUrl'],
      scopes: (json['oauthScopes'] ?? json['scopes'] as List<dynamic>?)
              ?.cast<String>() ??
          ['api', 'web', 'refresh_token'],
      loginServer: json['loginServer'] != null
          ? Uri.parse(json['loginServer'])
          : null,
      useWebServerAuthentication:
          json['useWebServerAuthentication'] ?? true,
      pushNotificationsEnabled:
          json['pushNotificationEnabled'] ?? false,
    );
  }

  Map<String, dynamic> toJson() => {
        'remoteAccessConsumerKey': clientId,
        'oauthRedirectURI': callbackUrl,
        'oauthScopes': scopes,
        'loginServer': loginServer.toString(),
        'useWebServerAuthentication': useWebServerAuthentication,
        'pushNotificationEnabled': pushNotificationsEnabled,
      };
}

/// Default login server that resolves to production Salesforce.
class _DefaultLoginServer implements Uri {
  const _DefaultLoginServer();

  static final Uri _uri = Uri.parse('https://login.salesforce.com');

  @override
  dynamic noSuchMethod(Invocation invocation) =>
      Function.apply(
          _uri.noSuchMethod as Function, [invocation]);

  @override
  String toString() => _uri.toString();

  @override
  String get authority => _uri.authority;
  @override
  String get fragment => _uri.fragment;
  @override
  bool get hasAbsolutePath => _uri.hasAbsolutePath;
  @override
  bool get hasAuthority => _uri.hasAuthority;
  @override
  bool get hasEmptyPath => _uri.hasEmptyPath;
  @override
  bool get hasFragment => _uri.hasFragment;
  @override
  bool get hasPort => _uri.hasPort;
  @override
  bool get hasQuery => _uri.hasQuery;
  @override
  bool get hasScheme => _uri.hasScheme;
  @override
  String get host => _uri.host;
  @override
  bool get isAbsolute => _uri.isAbsolute;
  @override
  Uri normalizePath() => _uri.normalizePath();
  @override
  String get origin => _uri.origin;
  @override
  String get path => _uri.path;
  @override
  List<String> get pathSegments => _uri.pathSegments;
  @override
  int get port => _uri.port;
  @override
  String get query => _uri.query;
  @override
  Map<String, String> get queryParameters => _uri.queryParameters;
  @override
  Map<String, List<String>> get queryParametersAll => _uri.queryParametersAll;
  @override
  Uri removeFragment() => _uri.removeFragment();
  @override
  Uri replace({
    String? scheme,
    String? userInfo,
    String? host,
    int? port,
    String? path,
    Iterable<String>? pathSegments,
    String? query,
    Map<String, dynamic>? queryParameters,
    String? fragment,
  }) =>
      _uri.replace(
        scheme: scheme,
        userInfo: userInfo,
        host: host,
        port: port,
        path: path,
        pathSegments: pathSegments,
        query: query,
        queryParameters: queryParameters,
        fragment: fragment,
      );
  @override
  Uri resolve(String reference) => _uri.resolve(reference);
  @override
  Uri resolveUri(Uri reference) => _uri.resolveUri(reference);
  @override
  String get scheme => _uri.scheme;
  @override
  String get userInfo => _uri.userInfo;
  @override
  UriData? get data => _uri.data;
}
