import 'dart:async';
import 'package:http/http.dart' as http;
import '../auth/oauth2.dart';
import '../network/connectivity_manager.dart';
import '../../analytics/logger/salesforce_logger.dart';
import 'http_client_factory.dart';
import 'rest_request.dart';
import 'rest_response.dart';

/// Information about the authenticated client.
class ClientInfo {
  final Uri instanceUrl;
  final Uri loginUrl;
  final Uri? identityUrl;
  final String? accountName;
  final String? username;
  final String userId;
  final String orgId;
  final String? communityId;
  final String? communityUrl;
  final String? firstName;
  final String? lastName;
  final String? displayName;
  final String? email;
  final String? photoUrl;
  final String? thumbnailUrl;
  final String? lightningDomain;
  final String? lightningSid;
  final String? vfDomain;
  final String? vfSid;
  final String? contentDomain;
  final String? contentSid;
  final String? csrfToken;
  final Map<String, String> additionalOauthValues;

  ClientInfo({
    required this.instanceUrl,
    required this.loginUrl,
    this.identityUrl,
    this.accountName,
    this.username,
    required this.userId,
    required this.orgId,
    this.communityId,
    this.communityUrl,
    this.firstName,
    this.lastName,
    this.displayName,
    this.email,
    this.photoUrl,
    this.thumbnailUrl,
    this.lightningDomain,
    this.lightningSid,
    this.vfDomain,
    this.vfSid,
    this.contentDomain,
    this.contentSid,
    this.csrfToken,
    this.additionalOauthValues = const {},
  });

  /// Builds a unique ID for this client.
  String get uniqueId => '${orgId}_$userId';

  /// Resolves a path to a full URL.
  Uri resolveUrl(String path, {RestEndpoint endpoint = RestEndpoint.instance}) {
    if (path.startsWith('http')) {
      final uri = Uri.parse(path);
      // Enforce HTTPS for absolute URLs
      if (uri.scheme != 'https') {
        throw ArgumentError('HTTPS required but got ${uri.scheme}://${uri.host}');
      }
      return uri;
    }
    final baseUrl =
        endpoint == RestEndpoint.login ? loginUrl : instanceUrl;
    return baseUrl.replace(path: path);
  }

  Map<String, dynamic> toJson() => {
        'instanceUrl': instanceUrl.toString(),
        'loginUrl': loginUrl.toString(),
        'identityUrl': identityUrl?.toString(),
        'accountName': accountName,
        'username': username,
        'userId': userId,
        'orgId': orgId,
        'communityId': communityId,
        'communityUrl': communityUrl,
        'firstName': firstName,
        'lastName': lastName,
        'displayName': displayName,
        'email': email,
        'photoUrl': photoUrl,
        'thumbnailUrl': thumbnailUrl,
        'lightningDomain': lightningDomain,
        'lightningSid': lightningSid,
        'vfDomain': vfDomain,
        'vfSid': vfSid,
        'contentDomain': contentDomain,
        'contentSid': contentSid,
        'csrfToken': csrfToken,
        'additionalOauthValues': additionalOauthValues,
      };

  factory ClientInfo.fromJson(Map<String, dynamic> json) {
    return ClientInfo(
      instanceUrl: Uri.parse(json['instanceUrl']),
      loginUrl: Uri.parse(json['loginUrl']),
      identityUrl: json['identityUrl'] != null
          ? Uri.parse(json['identityUrl'])
          : null,
      accountName: json['accountName'],
      username: json['username'],
      userId: json['userId'],
      orgId: json['orgId'],
      communityId: json['communityId'],
      communityUrl: json['communityUrl'],
      firstName: json['firstName'],
      lastName: json['lastName'],
      displayName: json['displayName'],
      email: json['email'],
      photoUrl: json['photoUrl'],
      thumbnailUrl: json['thumbnailUrl'],
      lightningDomain: json['lightningDomain'],
      lightningSid: json['lightningSid'],
      vfDomain: json['vfDomain'],
      vfSid: json['vfSid'],
      contentDomain: json['contentDomain'],
      contentSid: json['contentSid'],
      csrfToken: json['csrfToken'],
      additionalOauthValues: json['additionalOauthValues'] != null
          ? Map<String, String>.from(json['additionalOauthValues'])
          : {},
    );
  }
}

/// Callback for async REST requests.
typedef AsyncRequestCallback = void Function(
    RestRequest request, RestResponse? response, Exception? error);

/// Provider for auth tokens (enables automatic token refresh).
abstract class AuthTokenProvider {
  String? getInstanceUrl();
  Future<String?> getNewAuthToken();
  String? getRefreshToken();
  DateTime? getLastRefreshTime();
  DateTime? getTokenExpiresAt();
}

/// Exception thrown when refresh token is revoked.
class RefreshTokenRevokedException implements Exception {
  final String message;
  RefreshTokenRevokedException(this.message);
  @override
  String toString() => 'RefreshTokenRevokedException: $message';
}

/// Salesforce REST API client with automatic token refresh.
///
/// Provides:
/// - Sync and async methods for sending REST requests
/// - Automatic OAuth token refresh on 401 responses
/// - Proactive token refresh before expiration
/// - Connectivity-aware request handling
/// - HTTPS enforcement
/// - Configurable HTTP client with timeouts and retry
class RestClient {
  static const String _tag = 'RestClient';
  static final SalesforceLogger _logger =
      SalesforceLogger.getLogger('RestClient');

  /// Buffer time before token expiry to trigger proactive refresh.
  static const Duration _proactiveRefreshBuffer = Duration(minutes: 5);

  ClientInfo clientInfo;
  String _authToken;
  DateTime? _tokenExpiresAt;
  final AuthTokenProvider? _authTokenProvider;
  http.Client _httpClient;
  ConnectivityManager? _connectivityManager;
  DateTime? _lastRefreshTime;

  static const Duration _minRefreshInterval = Duration(seconds: 30);

  RestClient({
    required this.clientInfo,
    required String authToken,
    AuthTokenProvider? authTokenProvider,
    http.Client? httpClient,
    DateTime? tokenExpiresAt,
    ConnectivityManager? connectivityManager,
  })  : _authToken = authToken,
        _tokenExpiresAt = tokenExpiresAt,
        _authTokenProvider = authTokenProvider,
        _httpClient = httpClient ?? SalesforceHttpClient(),
        _connectivityManager = connectivityManager;

  /// Gets the current auth token.
  String get authToken => _authToken;

  /// Gets the refresh token (if available via provider).
  String? get refreshToken => _authTokenProvider?.getRefreshToken();

  /// Whether the current token is expired.
  bool get isTokenExpired =>
      _tokenExpiresAt != null && DateTime.now().isAfter(_tokenExpiresAt!);

  /// Whether the token will expire within the proactive refresh buffer.
  bool get isTokenExpiringSoon =>
      _tokenExpiresAt != null &&
      DateTime.now()
          .isAfter(_tokenExpiresAt!.subtract(_proactiveRefreshBuffer));

  /// Sets a custom HTTP client.
  set httpClient(http.Client client) => _httpClient = client;

  /// Gets the HTTP client.
  http.Client get httpClient => _httpClient;

  /// Sets the connectivity manager.
  set connectivityManager(ConnectivityManager? manager) =>
      _connectivityManager = manager;

  /// Sends a request asynchronously.
  Future<RestResponse> sendAsync(RestRequest request) async {
    // Check connectivity
    _connectivityManager?.requireOnline();

    // Proactively refresh token if expiring soon
    if (isTokenExpiringSoon && _authTokenProvider != null) {
      await _refreshAccessToken();
    }

    try {
      var response = await _sendRequest(request);

      // Auto-refresh on 401
      if (response.statusCode == 401 && _authTokenProvider != null) {
        final refreshed = await _refreshAccessToken();
        if (refreshed) {
          response = await _sendRequest(request);
        }
      }

      return response;
    } catch (e) {
      rethrow;
    }
  }

  /// Sends a request synchronously (blocking).
  Future<RestResponse> sendSync(RestRequest request) => sendAsync(request);

  /// Sends a request with a callback.
  void sendWithCallback(
      RestRequest request, AsyncRequestCallback callback) {
    sendAsync(request).then((response) {
      callback(request, response, null);
    }).catchError((Object e) {
      callback(request, null, e is Exception ? e : Exception(e.toString()));
    });
  }

  /// Builds an HTTP request from a RestRequest.
  http.Request buildHttpRequest(RestRequest request) {
    final url = clientInfo.resolveUrl(request.path, endpoint: request.endpoint);
    final httpRequest =
        http.Request(request.method.name.toUpperCase(), url);

    httpRequest.headers['Authorization'] = 'Bearer $_authToken';
    httpRequest.headers['Content-Type'] = 'application/json; charset=utf-8';
    httpRequest.headers['Accept'] = 'application/json';

    if (request.additionalHttpHeaders != null) {
      httpRequest.headers.addAll(request.additionalHttpHeaders!);
    }

    final body = request.bodyAsString;
    if (body != null) {
      httpRequest.body = body;
    }

    return httpRequest;
  }

  Future<RestResponse> _sendRequest(RestRequest request) async {
    final url = clientInfo.resolveUrl(request.path, endpoint: request.endpoint);

    final headers = <String, String>{
      'Authorization': 'Bearer $_authToken',
      'Content-Type': 'application/json; charset=utf-8',
      'Accept': 'application/json',
    };

    if (request.additionalHttpHeaders != null) {
      headers.addAll(request.additionalHttpHeaders!);
    }

    http.Response httpResponse;
    final body = request.bodyAsString;

    switch (request.method) {
      case RestMethod.get:
        httpResponse = await _httpClient.get(url, headers: headers);
        break;
      case RestMethod.post:
        httpResponse =
            await _httpClient.post(url, headers: headers, body: body);
        break;
      case RestMethod.put:
        httpResponse =
            await _httpClient.put(url, headers: headers, body: body);
        break;
      case RestMethod.delete:
        httpResponse = await _httpClient.delete(url, headers: headers);
        break;
      case RestMethod.patch:
        httpResponse =
            await _httpClient.patch(url, headers: headers, body: body);
        break;
      case RestMethod.head:
        httpResponse = await _httpClient.head(url, headers: headers);
        break;
    }

    return RestResponse(httpResponse);
  }

  Future<bool> _refreshAccessToken() async {
    if (_authTokenProvider == null) return false;

    if (_lastRefreshTime != null) {
      final elapsed = DateTime.now().difference(_lastRefreshTime!);
      if (elapsed < _minRefreshInterval) return false;
    }

    try {
      final newToken = await _authTokenProvider!.getNewAuthToken();
      if (newToken != null) {
        _authToken = newToken;
        _lastRefreshTime = DateTime.now();
        _tokenExpiresAt = _authTokenProvider!.getTokenExpiresAt();
        _logger.i(_tag, 'Access token refreshed successfully');
        return true;
      }
    } catch (e) {
      _logger.e(_tag, 'Failed to refresh access token', e);
      if (e is OAuthFailedException && e.isRefreshTokenInvalid) {
        throw RefreshTokenRevokedException(
            'Refresh token has been revoked');
      }
    }
    return false;
  }

  /// Clears cached data.
  void clearCaches() {}

  /// Disposes the client and releases resources.
  void dispose() {
    _httpClient.close();
  }

  /// Gets JSON credentials for passing to WebView or other components.
  ///
  /// Warning: This exposes sensitive tokens. Use with care.
  Map<String, dynamic> getJsonCredentials() {
    return {
      'accessToken': _authToken,
      'refreshToken': _authTokenProvider?.getRefreshToken(),
      'instanceUrl': clientInfo.instanceUrl.toString(),
      'loginUrl': clientInfo.loginUrl.toString(),
      'clientId': clientInfo.accountName,
      'orgId': clientInfo.orgId,
      'userId': clientInfo.userId,
    };
  }
}
