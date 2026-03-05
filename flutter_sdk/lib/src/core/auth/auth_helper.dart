import 'dart:async';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:webview_flutter/webview_flutter.dart';
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

  const OAuthConfig({
    required this.loginServer,
    required this.clientId,
    required this.callbackUrl,
    required this.scopes,
    this.useWebServerAuthentication = true,
    this.additionalParams,
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
/// WebView-based login, and code exchange for tokens.
class LoginManager {
  final OAuthConfig config;
  String? _codeVerifier;

  LoginManager({required this.config});

  /// Starts the OAuth login flow and returns the token response.
  ///
  /// Opens a WebView login page, handles the OAuth callback,
  /// and exchanges the authorization code for tokens.
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

  static http.Client _createHttpClient() => http.Client();
}

class _AuthResult {
  final String? code;
  final Map<String, String>? params;
  final String? error;
  _AuthResult({this.code, this.params, this.error});
}

/// Internal WebView widget for the OAuth login flow.
class _LoginWebView extends StatefulWidget {
  final Uri authUrl;
  final String callbackUrl;

  const _LoginWebView({
    required this.authUrl,
    required this.callbackUrl,
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
            if (request.url.startsWith(widget.callbackUrl)) {
              _handleCallback(request.url);
              return NavigationDecision.prevent;
            }
            return NavigationDecision.navigate;
          },
        ),
      )
      ..loadRequest(widget.authUrl);
  }

  void _handleCallback(String url) {
    final uri = Uri.parse(url);
    final params = <String, String>{};

    // Check query params (web server flow)
    params.addAll(uri.queryParameters);

    // Check fragment params (user-agent flow)
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
