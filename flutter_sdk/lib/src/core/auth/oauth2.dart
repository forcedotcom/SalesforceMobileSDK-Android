import 'dart:convert';
import 'dart:math';
import 'package:crypto/crypto.dart';
import 'package:http/http.dart' as http;

/// Reasons for user logout.
enum LogoutReason {
  corruptState,
  corruptStateAppConfigurationSettings,
  corruptStateAppProviderErrorInvalidUser,
  corruptStateAppInvalidRestClient,
  corruptStateAppOther,
  corruptStateMsdk,
  refreshTokenExpired,
  ssdkLogoutPolicy,
  timeout,
  unexpected,
  unexpectedResponse,
  unknown,
  userLogout,
  refreshTokenRotated,
}

/// Response from the OAuth2 token endpoint.
class TokenEndpointResponse {
  final String? authToken;
  final String? refreshToken;
  final String? instanceUrl;
  final String? idUrl;
  final String? orgId;
  final String? userId;
  final String? code;
  final String? communityId;
  final String? communityUrl;
  final String? lightningDomain;
  final String? lightningSid;
  final String? vfDomain;
  final String? vfSid;
  final String? contentDomain;
  final String? contentSid;
  final String? csrfToken;
  final String? idToken;
  final String? tokenFormat;
  final int? expiresIn;
  final DateTime? expiresAt;
  final Map<String, String> additionalOauthValues;

  TokenEndpointResponse({
    this.authToken,
    this.refreshToken,
    this.instanceUrl,
    this.idUrl,
    this.orgId,
    this.userId,
    this.code,
    this.communityId,
    this.communityUrl,
    this.lightningDomain,
    this.lightningSid,
    this.vfDomain,
    this.vfSid,
    this.contentDomain,
    this.contentSid,
    this.csrfToken,
    this.idToken,
    this.tokenFormat,
    this.expiresIn,
    this.expiresAt,
    this.additionalOauthValues = const {},
  });

  factory TokenEndpointResponse.fromJson(Map<String, dynamic> json) {
    final expiresIn = json['expires_in'] is int
        ? json['expires_in'] as int
        : json['expires_in'] is String
            ? int.tryParse(json['expires_in'])
            : null;
    return TokenEndpointResponse(
      authToken: json['access_token'],
      refreshToken: json['refresh_token'],
      instanceUrl: json['instance_url'],
      idUrl: json['id'],
      orgId: _extractOrgId(json['id']),
      userId: _extractUserId(json['id']),
      communityId: json['sfdc_community_id'],
      communityUrl: json['sfdc_community_url'],
      lightningDomain: json['lightning_domain'],
      lightningSid: json['lightning_sid'],
      vfDomain: json['visualforce_domain'],
      vfSid: json['visualforce_sid'],
      contentDomain: json['content_domain'],
      contentSid: json['content_sid'],
      csrfToken: json['csrf_token'],
      idToken: json['id_token'],
      tokenFormat: json['token_format'],
      expiresIn: expiresIn,
      expiresAt: expiresIn != null
          ? DateTime.now().add(Duration(seconds: expiresIn))
          : null,
    );
  }

  factory TokenEndpointResponse.fromCallbackParams(
      Map<String, String> params) {
    return TokenEndpointResponse(
      authToken: params['access_token'],
      refreshToken: params['refresh_token'],
      instanceUrl: params['instance_url'],
      idUrl: params['id'],
      orgId: _extractOrgId(params['id']),
      userId: _extractUserId(params['id']),
      communityId: params['sfdc_community_id'],
      communityUrl: params['sfdc_community_url'],
    );
  }

  static String? _extractOrgId(String? idUrl) {
    if (idUrl == null) return null;
    final parts = idUrl.split('/');
    return parts.length >= 2 ? parts[parts.length - 2] : null;
  }

  static String? _extractUserId(String? idUrl) {
    if (idUrl == null) return null;
    final parts = idUrl.split('/');
    return parts.isNotEmpty ? parts.last : null;
  }

  Map<String, dynamic> toJson() => {
        'access_token': authToken,
        'refresh_token': refreshToken,
        'instance_url': instanceUrl,
        'id': idUrl,
        'sfdc_community_id': communityId,
        'sfdc_community_url': communityUrl,
        'lightning_domain': lightningDomain,
        'lightning_sid': lightningSid,
        'visualforce_domain': vfDomain,
        'visualforce_sid': vfSid,
        'content_domain': contentDomain,
        'content_sid': contentSid,
        'csrf_token': csrfToken,
        'id_token': idToken,
        'token_format': tokenFormat,
      };
}

/// Response from the identity service.
class IdServiceResponse {
  final String? username;
  final String? email;
  final String? firstName;
  final String? lastName;
  final String? displayName;
  final String? pictureUrl;
  final String? thumbnailUrl;
  final bool screenLock;
  final int screenLockTimeout;
  final bool biometricAuth;
  final int biometricAuthTimeout;
  final Map<String, dynamic>? customAttributes;
  final Map<String, dynamic>? customPermissions;

  IdServiceResponse({
    this.username,
    this.email,
    this.firstName,
    this.lastName,
    this.displayName,
    this.pictureUrl,
    this.thumbnailUrl,
    this.screenLock = false,
    this.screenLockTimeout = 0,
    this.biometricAuth = false,
    this.biometricAuthTimeout = 0,
    this.customAttributes,
    this.customPermissions,
  });

  factory IdServiceResponse.fromJson(Map<String, dynamic> json) {
    final mobilePolicy =
        json['mobile_policy'] as Map<String, dynamic>? ?? {};
    return IdServiceResponse(
      username: json['username'],
      email: json['email'],
      firstName: json['first_name'],
      lastName: json['last_name'],
      displayName: json['display_name'],
      pictureUrl: json['photos']?['picture'],
      thumbnailUrl: json['photos']?['thumbnail'],
      screenLock: mobilePolicy['screen_lock'] ?? false,
      screenLockTimeout: mobilePolicy['screen_lock_timeout'] ?? 0,
      biometricAuth: mobilePolicy['biometric_auth'] ?? false,
      biometricAuthTimeout: mobilePolicy['biometric_auth_timeout'] ?? 0,
      customAttributes: json['custom_attributes'] != null
          ? Map<String, dynamic>.from(json['custom_attributes'])
          : null,
      customPermissions: json['custom_permissions'] != null
          ? Map<String, dynamic>.from(json['custom_permissions'])
          : null,
    );
  }
}

/// OAuth failure exception.
class OAuthFailedException implements Exception {
  final String message;
  final int httpStatusCode;
  final TokenErrorResponse? tokenErrorResponse;

  OAuthFailedException(this.message, this.httpStatusCode,
      [this.tokenErrorResponse]);

  bool get isRefreshTokenInvalid =>
      tokenErrorResponse?.error == 'invalid_grant';

  @override
  String toString() => 'OAuthFailedException: $message (HTTP $httpStatusCode)';
}

/// Token error response from the server.
class TokenErrorResponse {
  final String? error;
  final String? errorDescription;

  TokenErrorResponse({this.error, this.errorDescription});

  factory TokenErrorResponse.fromJson(Map<String, dynamic> json) {
    return TokenErrorResponse(
      error: json['error'],
      errorDescription: json['error_description'],
    );
  }

  @override
  String toString() => '$error: $errorDescription';
}

/// OAuth2 utility class providing all OAuth flows for Salesforce authentication.
///
/// Supports:
/// - Authorization URL generation (User-Agent and Web Server flows)
/// - Authorization code exchange
/// - Token refresh
/// - Token revocation
/// - Identity service calls
/// - PKCE (Proof Key for Code Exchange)
class OAuth2 {
  static const String _oauthAuthorizationPath = '/services/oauth2/authorize';
  static const String _oauthTokenPath = '/services/oauth2/token';
  static const String _oauthRevokePath = '/services/oauth2/revoke';

  /// Generates PKCE code verifier (random string 43-128 chars).
  static String generateCodeVerifier() {
    final random = Random.secure();
    final values = List<int>.generate(64, (_) => random.nextInt(256));
    return base64Url.encode(values).replaceAll('=', '');
  }

  /// Generates PKCE code challenge from verifier (S256).
  static String generateCodeChallenge(String codeVerifier) {
    final bytes = utf8.encode(codeVerifier);
    final digest = sha256.convert(bytes);
    return base64Url.encode(digest.bytes).replaceAll('=', '');
  }

  /// Builds the authorization URL for the OAuth flow.
  static Uri getAuthorizationUrl({
    required Uri loginServer,
    required String clientId,
    required String callbackUrl,
    required List<String> scopes,
    bool useWebServerAuthentication = true,
    String? loginHint,
    String displayType = 'touch',
    String? codeChallenge,
    Map<String, String>? additionalParams,
  }) {
    final responseType =
        useWebServerAuthentication ? 'code' : 'token';
    final scopeParam = scopes.join(' ');

    final params = <String, String>{
      'response_type': responseType,
      'client_id': clientId,
      'redirect_uri': callbackUrl,
      'display': displayType,
    };

    if (scopeParam.isNotEmpty) {
      params['scope'] = scopeParam;
    }
    if (loginHint != null) {
      params['login_hint'] = loginHint;
    }
    if (codeChallenge != null) {
      params['code_challenge'] = codeChallenge;
      params['code_challenge_method'] = 'S256';
    }
    if (additionalParams != null) {
      params.addAll(additionalParams);
    }

    return loginServer.replace(
      path: _oauthAuthorizationPath,
      queryParameters: params,
    );
  }

  /// Exchanges an authorization code for tokens.
  static Future<TokenEndpointResponse> exchangeCode({
    required http.Client httpClient,
    required Uri loginServer,
    required String clientId,
    required String code,
    required String codeVerifier,
    required String callbackUrl,
  }) async {
    final tokenUrl = loginServer.replace(path: _oauthTokenPath);
    final response = await httpClient.post(
      tokenUrl,
      body: {
        'grant_type': 'authorization_code',
        'client_id': clientId,
        'code': code,
        'code_verifier': codeVerifier,
        'redirect_uri': callbackUrl,
      },
    );

    if (response.statusCode != 200) {
      TokenErrorResponse? errorResp;
      try {
        errorResp = TokenErrorResponse.fromJson(jsonDecode(response.body));
      } catch (_) {}
      throw OAuthFailedException(
        'Token exchange failed',
        response.statusCode,
        errorResp,
      );
    }

    return TokenEndpointResponse.fromJson(jsonDecode(response.body));
  }

  /// Refreshes an access token using a refresh token.
  static Future<TokenEndpointResponse> refreshAuthToken({
    required http.Client httpClient,
    required Uri loginServer,
    required String clientId,
    required String refreshToken,
    Map<String, String>? additionalParams,
  }) async {
    final tokenUrl = loginServer.replace(path: _oauthTokenPath);
    final body = <String, String>{
      'grant_type': 'refresh_token',
      'client_id': clientId,
      'refresh_token': refreshToken,
    };
    if (additionalParams != null) body.addAll(additionalParams);

    final response = await httpClient.post(tokenUrl, body: body);

    if (response.statusCode != 200) {
      TokenErrorResponse? errorResp;
      try {
        errorResp = TokenErrorResponse.fromJson(jsonDecode(response.body));
      } catch (_) {}
      throw OAuthFailedException(
        'Token refresh failed',
        response.statusCode,
        errorResp,
      );
    }

    return TokenEndpointResponse.fromJson(jsonDecode(response.body));
  }

  /// Revokes a refresh token.
  static Future<void> revokeRefreshToken({
    required http.Client httpClient,
    required Uri loginServer,
    required String refreshToken,
    LogoutReason reason = LogoutReason.userLogout,
  }) async {
    final revokeUrl = loginServer.replace(path: _oauthRevokePath);
    await httpClient.post(revokeUrl, body: {'token': refreshToken});
  }

  /// Calls the Salesforce identity service to get user info.
  static Future<IdServiceResponse> callIdentityService({
    required http.Client httpClient,
    required String identityUrl,
    required String authToken,
  }) async {
    final response = await httpClient.get(
      Uri.parse(identityUrl),
      headers: {'Authorization': 'Bearer $authToken'},
    );

    if (response.statusCode != 200) {
      throw OAuthFailedException(
          'Identity service call failed', response.statusCode);
    }

    return IdServiceResponse.fromJson(jsonDecode(response.body));
  }

  /// Swaps a JWT for an access token (headless login).
  static Future<TokenEndpointResponse> swapJWTForTokens({
    required http.Client httpClient,
    required Uri loginServer,
    required String jwt,
  }) async {
    final tokenUrl = loginServer.replace(path: _oauthTokenPath);
    final response = await httpClient.post(
      tokenUrl,
      body: {
        'grant_type': 'urn:ietf:params:oauth:grant-type:jwt-bearer',
        'assertion': jwt,
      },
    );

    if (response.statusCode != 200) {
      TokenErrorResponse? errorResp;
      try {
        errorResp = TokenErrorResponse.fromJson(jsonDecode(response.body));
      } catch (_) {}
      throw OAuthFailedException(
        'JWT swap failed',
        response.statusCode,
        errorResp,
      );
    }

    return TokenEndpointResponse.fromJson(jsonDecode(response.body));
  }

  /// Computes the scope parameter from a list of scopes.
  static String computeScopeParameter(List<String> scopes) {
    return scopes.join(' ');
  }
}
