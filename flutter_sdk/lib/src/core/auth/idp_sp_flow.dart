import 'dart:async';
import 'package:http/http.dart' as http;
import 'package:uuid/uuid.dart';
import '../rest/rest_client.dart';
import 'oauth2.dart';

/// Configuration for a Service Provider (SP) app.
class SPConfig {
  /// The SP app's OAuth client ID (connected app consumer key).
  final String oauthClientId;

  /// The SP app's OAuth callback URL.
  final String oauthCallbackUrl;

  /// The SP app's requested OAuth scopes.
  final List<String> oauthScopes;

  /// A unique identifier for the SP app.
  final String appIdentifier;

  const SPConfig({
    required this.oauthClientId,
    required this.oauthCallbackUrl,
    required this.oauthScopes,
    required this.appIdentifier,
  });

  Map<String, dynamic> toJson() => {
        'oauthClientId': oauthClientId,
        'oauthCallbackUrl': oauthCallbackUrl,
        'oauthScopes': oauthScopes,
        'appIdentifier': appIdentifier,
      };

  factory SPConfig.fromJson(Map<String, dynamic> json) {
    return SPConfig(
      oauthClientId: json['oauthClientId'],
      oauthCallbackUrl: json['oauthCallbackUrl'],
      oauthScopes: List<String>.from(json['oauthScopes']),
      appIdentifier: json['appIdentifier'],
    );
  }
}

/// Types of IDP/SP messages.
enum IDPSPMessageType {
  idpToSpRequest,
  spToIdpRequest,
  idpToSpResponse,
  spToIdpResponse,
}

/// Base class for IDP/SP inter-app messages.
abstract class IDPSPMessage {
  final String uuid;
  final IDPSPMessageType type;

  IDPSPMessage({required this.uuid, required this.type});

  Map<String, dynamic> toJson();

  factory IDPSPMessage.fromJson(Map<String, dynamic> json) {
    final type = IDPSPMessageType.values.firstWhere(
        (t) => t.name == json['type'],
        orElse: () => throw FormatException(
            'Unknown message type: ${json['type']}'));
    switch (type) {
      case IDPSPMessageType.idpToSpRequest:
        return IDPToSPRequest.fromJson(json);
      case IDPSPMessageType.spToIdpRequest:
        return SPToIDPRequest.fromJson(json);
      case IDPSPMessageType.idpToSpResponse:
        return IDPToSPResponse.fromJson(json);
      case IDPSPMessageType.spToIdpResponse:
        return SPToIDPResponse.fromJson(json);
    }
  }
}

/// IDP → SP: Initiate IDP-driven login with user hint.
class IDPToSPRequest extends IDPSPMessage {
  final String orgId;
  final String userId;

  IDPToSPRequest({
    required String uuid,
    required this.orgId,
    required this.userId,
  }) : super(uuid: uuid, type: IDPSPMessageType.idpToSpRequest);

  @override
  Map<String, dynamic> toJson() => {
        'uuid': uuid,
        'type': type.name,
        'orgId': orgId,
        'userId': userId,
      };

  factory IDPToSPRequest.fromJson(Map<String, dynamic> json) {
    return IDPToSPRequest(
      uuid: json['uuid'],
      orgId: json['orgId'],
      userId: json['userId'],
    );
  }
}

/// SP → IDP: Request auth code with PKCE challenge.
class SPToIDPRequest extends IDPSPMessage {
  final String codeChallenge;

  SPToIDPRequest({
    required String uuid,
    required this.codeChallenge,
  }) : super(uuid: uuid, type: IDPSPMessageType.spToIdpRequest);

  @override
  Map<String, dynamic> toJson() => {
        'uuid': uuid,
        'type': type.name,
        'codeChallenge': codeChallenge,
      };

  factory SPToIDPRequest.fromJson(Map<String, dynamic> json) {
    return SPToIDPRequest(
      uuid: json['uuid'],
      codeChallenge: json['codeChallenge'],
    );
  }
}

/// IDP → SP: Auth code or error response.
class IDPToSPResponse extends IDPSPMessage {
  final String? code;
  final String? loginUrl;
  final String? error;

  IDPToSPResponse({
    required String uuid,
    this.code,
    this.loginUrl,
    this.error,
  }) : super(uuid: uuid, type: IDPSPMessageType.idpToSpResponse);

  bool get isSuccess => code != null && error == null;

  @override
  Map<String, dynamic> toJson() => {
        'uuid': uuid,
        'type': type.name,
        'code': code,
        'loginUrl': loginUrl,
        'error': error,
      };

  factory IDPToSPResponse.fromJson(Map<String, dynamic> json) {
    return IDPToSPResponse(
      uuid: json['uuid'],
      code: json['code'],
      loginUrl: json['loginUrl'],
      error: json['error'],
    );
  }
}

/// SP → IDP: Flow completion status.
class SPToIDPResponse extends IDPSPMessage {
  final String? error;

  SPToIDPResponse({
    required String uuid,
    this.error,
  }) : super(uuid: uuid, type: IDPSPMessageType.spToIdpResponse);

  bool get isSuccess => error == null;

  @override
  Map<String, dynamic> toJson() => {
        'uuid': uuid,
        'type': type.name,
        'error': error,
      };

  factory SPToIDPResponse.fromJson(Map<String, dynamic> json) {
    return SPToIDPResponse(
      uuid: json['uuid'],
      error: json['error'],
    );
  }
}

/// Status updates for IDP login flow.
enum IDPFlowStatus {
  spLoginRequestReceived,
  idpLoginInitiatedBySp,
  gettingAuthCodeFromServer,
  authCodeSentToSp,
  loginRequestSentToSp,
  spLoginComplete,
  errorReceivedFromSp,
}

/// Status updates for SP login flow.
enum SPFlowStatus {
  failedToSendRequestToIdp,
  loginRequestSentToIdp,
  authCodeReceivedFromIdp,
  errorReceivedFromIdp,
  failedToExchangeAuthorizationCode,
  loginComplete,
}

/// Callback for IDP/SP flow status changes.
typedef IDPStatusCallback = void Function(IDPFlowStatus status);
typedef SPStatusCallback = void Function(SPFlowStatus status);

/// Tracks state of an active IDP/SP login flow.
class ActiveFlow {
  final String uuid;
  final List<IDPSPMessage> messages = [];
  final IDPSPMessage firstMessage;

  ActiveFlow({required this.firstMessage})
      : uuid = firstMessage.uuid;

  bool isPartOfFlow(IDPSPMessage message) => message.uuid == uuid;

  void addMessage(IDPSPMessage message) {
    messages.add(message);
  }
}

/// Manages IDP-side of enterprise SSO flows.
///
/// The IDP app authenticates users and provides authorization codes to SP apps.
/// In Flutter, inter-app communication uses platform channels or deep links.
///
/// Mirrors Android IDPManager for feature parity.
class IDPManager {
  final List<SPConfig> allowedSPApps;
  ActiveFlow? _activeFlow;
  IDPStatusCallback? _statusCallback;

  IDPManager({required this.allowedSPApps});

  /// Gets the SP config for an app, or null if not allowed.
  SPConfig? getSPConfig(String appIdentifier) {
    try {
      return allowedSPApps.firstWhere((c) => c.appIdentifier == appIdentifier);
    } catch (_) {
      return null;
    }
  }

  /// Current active flow.
  ActiveFlow? get activeFlow => _activeFlow;

  /// Kicks off an IDP-initiated login flow for the given SP app.
  IDPToSPRequest kickOffIDPInitiatedLoginFlow({
    required String orgId,
    required String userId,
    required String spAppIdentifier,
    IDPStatusCallback? callback,
  }) {
    _statusCallback = callback;
    final message = IDPToSPRequest(
      uuid: const Uuid().v4(),
      orgId: orgId,
      userId: userId,
    );
    _activeFlow = ActiveFlow(firstMessage: message);
    _statusCallback?.call(IDPFlowStatus.loginRequestSentToSp);
    return message;
  }

  /// Handles an SP login request (SP-initiated flow).
  ///
  /// Returns the authorization URL that should be loaded in a WebView
  /// to obtain an auth code for the SP.
  Future<Uri?> handleSPLoginRequest({
    required SPToIDPRequest request,
    required RestClient idpRestClient,
    required String spAppIdentifier,
    IDPStatusCallback? callback,
  }) async {
    _statusCallback = callback;
    final spConfig = getSPConfig(spAppIdentifier);
    if (spConfig == null) return null;

    _activeFlow = ActiveFlow(firstMessage: request);
    _statusCallback?.call(IDPFlowStatus.spLoginRequestReceived);
    _statusCallback?.call(IDPFlowStatus.gettingAuthCodeFromServer);

    // Build authorization URL for the SP app
    final authUrl = OAuth2.getAuthorizationUrl(
      loginServer: idpRestClient.clientInfo.loginUrl,
      clientId: spConfig.oauthClientId,
      callbackUrl: spConfig.oauthCallbackUrl,
      scopes: spConfig.oauthScopes,
      codeChallenge: request.codeChallenge,
    );

    return authUrl;
  }

  /// Called when auth code is obtained from the WebView redirect.
  IDPToSPResponse onAuthCodeObtained({
    required String code,
    required String loginUrl,
  }) {
    final response = IDPToSPResponse(
      uuid: _activeFlow?.uuid ?? const Uuid().v4(),
      code: code,
      loginUrl: loginUrl,
    );
    _activeFlow?.addMessage(response);
    _statusCallback?.call(IDPFlowStatus.authCodeSentToSp);
    return response;
  }

  /// Called when the SP reports flow completion.
  void onSPFlowComplete(SPToIDPResponse response) {
    _activeFlow?.addMessage(response);
    if (response.isSuccess) {
      _statusCallback?.call(IDPFlowStatus.spLoginComplete);
    } else {
      _statusCallback?.call(IDPFlowStatus.errorReceivedFromSp);
    }
    _activeFlow = null;
    _statusCallback = null;
  }

  /// Ends the active flow.
  void endActiveFlow() {
    _activeFlow = null;
    _statusCallback = null;
  }
}

/// Manages SP-side of enterprise SSO flows.
///
/// The SP app receives auth codes from an IDP app and exchanges them for tokens.
///
/// Mirrors Android SPManager for feature parity.
class SPManager {
  final SPConfig spConfig;
  final String idpAppIdentifier;
  ActiveFlow? _activeFlow;
  String? _codeVerifier;
  SPStatusCallback? _statusCallback;

  SPManager({
    required this.spConfig,
    required this.idpAppIdentifier,
  });

  /// Current active flow.
  ActiveFlow? get activeFlow => _activeFlow;

  /// Kicks off an SP-initiated login flow.
  ///
  /// Returns the request message to send to the IDP app.
  SPToIDPRequest kickOffSPInitiatedLoginFlow({
    SPStatusCallback? callback,
  }) {
    _statusCallback = callback;
    _codeVerifier = OAuth2.generateCodeVerifier();
    final codeChallenge = OAuth2.generateCodeChallenge(_codeVerifier!);

    final request = SPToIDPRequest(
      uuid: const Uuid().v4(),
      codeChallenge: codeChallenge,
    );
    _activeFlow = ActiveFlow(firstMessage: request);
    _statusCallback?.call(SPFlowStatus.loginRequestSentToIdp);
    return request;
  }

  /// Handles the IDP response containing auth code.
  ///
  /// Exchanges the code for tokens and returns the token response.
  Future<TokenEndpointResponse?> handleIDPResponse({
    required IDPToSPResponse response,
    required Uri loginServer,
    required http.Client httpClient,
  }) async {
    _activeFlow?.addMessage(response);

    if (!response.isSuccess) {
      _statusCallback?.call(SPFlowStatus.errorReceivedFromIdp);
      _activeFlow = null;
      return null;
    }

    _statusCallback?.call(SPFlowStatus.authCodeReceivedFromIdp);

    if (_codeVerifier == null || response.code == null) {
      _statusCallback?.call(SPFlowStatus.failedToExchangeAuthorizationCode);
      _activeFlow = null;
      return null;
    }

    try {
      final tokenResponse = await OAuth2.exchangeCode(
        httpClient: httpClient,
        loginServer: response.loginUrl != null
            ? Uri.parse(response.loginUrl!)
            : loginServer,
        clientId: spConfig.oauthClientId,
        code: response.code!,
        codeVerifier: _codeVerifier!,
        callbackUrl: spConfig.oauthCallbackUrl,
      );

      _statusCallback?.call(SPFlowStatus.loginComplete);
      _activeFlow = null;
      _codeVerifier = null;
      return tokenResponse;
    } catch (_) {
      _statusCallback?.call(SPFlowStatus.failedToExchangeAuthorizationCode);
      _activeFlow = null;
      _codeVerifier = null;
      return null;
    }
  }

  /// Handles an IDP-initiated login request with user hint.
  ///
  /// Returns null if user exists locally, or a SPToIDPRequest if login is needed.
  SPToIDPRequest? handleIDPLoginRequest({
    required IDPToSPRequest request,
    required bool userExistsLocally,
    SPStatusCallback? callback,
  }) {
    _statusCallback = callback;

    if (userExistsLocally) {
      // User exists, no login needed — just switch to them
      _statusCallback?.call(SPFlowStatus.loginComplete);
      return null;
    }

    // User doesn't exist, start SP-initiated flow
    return kickOffSPInitiatedLoginFlow(callback: callback);
  }

  /// Ends the active flow.
  void endActiveFlow() {
    _activeFlow = null;
    _codeVerifier = null;
    _statusCallback = null;
  }
}
