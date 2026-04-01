import 'dart:async';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:webview_flutter/webview_flutter.dart';
import '../rest/http_client_factory.dart';
import 'oauth2.dart';

/// Configuration for OAuth authentication.
class OAuthConfig {
  /// The Salesforce login server URL.
  final Uri loginServer;

  /// The connected app's consumer key.
  final String clientId;

  /// The OAuth callback URL.
  final String callbackUrl;

  /// Requested OAuth scopes.
  final List<String> scopes;

  /// Use web server authentication flow (with PKCE).
  final bool useWebServerAuthentication;

  /// Additional OAuth parameters.
  final Map<String, String>? additionalParams;

  /// Additional allowed domains for WebView navigation.
  final List<String> additionalAllowedDomains;

  const OAuthConfig({
    required this.loginServer,
    required this.clientId,
    required this.callbackUrl,
    required this.scopes,
    this.useWebServerAuthentication = true,
    this.additionalParams,
    this.additionalAllowedDomains = const [],
  });

  /// Production login server.
  static final Uri productionLoginServer =
      Uri.parse('https://login.salesforce.com');

  /// Sandbox login server.
  static final Uri sandboxLoginServer =
      Uri.parse('https://test.salesforce.com');
}

/// Manages the complete OAuth login flow using WebView.
///
/// Handles PKCE code generation, authorization URL building,
/// WebView-based login with domain allowlisting, and code exchange for tokens.
class LoginManager {
  final OAuthConfig config;
  String? _codeVerifier;

  LoginManager({required this.config});

  /// Starts the OAuth login flow and returns the token response.
  Future<TokenEndpointResponse> login(BuildContext context) async {
    _codeVerifier = OAuth2.generateCodeVerifier();
    final codeChallenge = OAuth2.generateCodeChallenge(_codeVerifier!);

    final authUrl = OAuth2.getAuthorizationUrl(
      loginServer: config.loginServer,
      clientId: config.clientId,
      callbackUrl: config.callbackUrl,
      scopes: config.scopes,
      useWebServerAuthentication: config.useWebServerAuthentication,
      codeChallenge: codeChallenge,
      additionalParams: config.additionalParams,
    );

    final result = await Navigator.of(context).push<_AuthResult>(
      MaterialPageRoute(
        builder: (_) => _LoginWebView(
          authUrl: authUrl,
          callbackUrl: config.callbackUrl,
          loginServerHost: config.loginServer.host,
          additionalAllowedDomains: config.additionalAllowedDomains,
        ),
      ),
    );

    if (result == null) {
      throw OAuthFailedException('Login was cancelled', 0);
    }

    if (result.error != null) {
      throw OAuthFailedException(result.error!, 0);
    }

    if (config.useWebServerAuthentication && result.code != null) {
      return OAuth2.exchangeCode(
        httpClient: _createHttpClient(),
        loginServer: config.loginServer,
        clientId: config.clientId,
        code: result.code!,
        codeVerifier: _codeVerifier!,
        callbackUrl: config.callbackUrl,
      );
    }

    if (result.params != null) {
      return TokenEndpointResponse.fromCallbackParams(result.params!);
    }

    throw OAuthFailedException('No auth result', 0);
  }

  /// Refreshes the current access token.
  Future<TokenEndpointResponse> refreshToken(String refreshToken) async {
    return OAuth2.refreshAuthToken(
      httpClient: _createHttpClient(),
      loginServer: config.loginServer,
      clientId: config.clientId,
      refreshToken: refreshToken,
    );
  }

  /// Revokes the refresh token (logout).
  Future<void> revokeToken(String refreshToken) async {
    return OAuth2.revokeRefreshToken(
      httpClient: _createHttpClient(),
      loginServer: config.loginServer,
      refreshToken: refreshToken,
    );
  }

  /// Clears all WebView session data (cookies, cache).
  static Future<void> clearSessionData() async {
    await WebViewCookieManager().clearCookies();
  }

  static http.Client _createHttpClient() =>
      SalesforceHttpClient(config: HttpClientConfig.production);
}

class _AuthResult {
  final String? code;
  final Map<String, String>? params;
  final String? error;
  _AuthResult({this.code, this.params, this.error});
}

/// Known Salesforce domain suffixes allowed in the login WebView.
const _salesforceDomains = [
  '.salesforce.com',
  '.force.com',
  '.sfdc.net',
  '.salesforce-sites.com',
  '.documentforce.com',
  '.visualforce.com',
  '.cloudforce.com',
  '.salesforceliveagent.com',
  '.my.salesforce.com',
  '.lightning.force.com',
];

/// Internal WebView widget for the OAuth login flow.
class _LoginWebView extends StatefulWidget {
  final Uri authUrl;
  final String callbackUrl;
  final String loginServerHost;
  final List<String> additionalAllowedDomains;

  const _LoginWebView({
    required this.authUrl,
    required this.callbackUrl,
    required this.loginServerHost,
    this.additionalAllowedDomains = const [],
  });

  @override
  State<_LoginWebView> createState() => _LoginWebViewState();
}

class _LoginWebViewState extends State<_LoginWebView> {
  late final WebViewController _controller;

  @override
  void initState() {
    super.initState();
    _controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setNavigationDelegate(
        NavigationDelegate(
          onNavigationRequest: (request) {
            // Always allow the callback URL
            if (request.url.startsWith(widget.callbackUrl)) {
              _handleCallback(request.url);
              return NavigationDecision.prevent;
            }

            // Validate navigation against Salesforce domain allowlist
            if (!_isAllowedUrl(request.url)) {
              return NavigationDecision.prevent;
            }

            return NavigationDecision.navigate;
          },
        ),
      )
      ..loadRequest(widget.authUrl);
  }

  bool _isAllowedUrl(String url) {
    final uri = Uri.tryParse(url);
    if (uri == null) return false;

    final host = uri.host.toLowerCase();

    // Allow the configured login server
    if (host == widget.loginServerHost.toLowerCase()) return true;

    // Allow known Salesforce domains
    for (final domain in _salesforceDomains) {
      if (host.endsWith(domain) || host == domain.substring(1)) return true;
    }

    // Allow additional configured domains
    for (final domain in widget.additionalAllowedDomains) {
      if (host.endsWith(domain) || host == domain) return true;
    }

    return false;
  }

  void _handleCallback(String url) {
    final uri = Uri.parse(url);
    final params = <String, String>{};

    params.addAll(uri.queryParameters);

    if (uri.fragment.isNotEmpty) {
      params.addAll(Uri.splitQueryString(uri.fragment));
    }

    if (params.containsKey('error')) {
      Navigator.of(context).pop(
        _AuthResult(error: params['error_description'] ?? params['error']),
      );
    } else if (params.containsKey('code')) {
      Navigator.of(context).pop(_AuthResult(code: params['code']));
    } else {
      Navigator.of(context).pop(_AuthResult(params: params));
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Salesforce Login'),
        leading: IconButton(
          icon: const Icon(Icons.close),
          onPressed: () => Navigator.of(context).pop(),
        ),
      ),
      body: WebViewWidget(controller: _controller),
    );
  }
}
