import 'dart:convert';
import 'package:http/http.dart' as http;
import '../rest/http_client_factory.dart';
import 'oauth2.dart';

/// Result of a native login attempt.
enum NativeLoginResult {
  invalidEmail,
  invalidUsername,
  invalidPassword,
  invalidCredentials,
  unknownError,
  success,
}

/// OTP verification method for passwordless and registration flows.
enum OtpVerificationMethod {
  email('email'),
  sms('sms');

  final String identityApiAuthVerificationTypeHeaderValue;
  const OtpVerificationMethod(this.identityApiAuthVerificationTypeHeaderValue);
}

/// Result of an OTP request.
class OtpRequestResult {
  final NativeLoginResult nativeLoginResult;
  final String? otpIdentifier;

  const OtpRequestResult({
    required this.nativeLoginResult,
    this.otpIdentifier,
  });
}

/// Result of starting a registration flow.
class StartRegistrationResult {
  final NativeLoginResult nativeLoginResult;
  final String? email;
  final String? requestIdentifier;

  const StartRegistrationResult({
    required this.nativeLoginResult,
    this.email,
    this.requestIdentifier,
  });
}

/// Manages native credential-based login using Salesforce Identity API headless flows.
///
/// Supports:
/// - Named-user login (username/password via OAuth2 PKCE)
/// - Passwordless OTP login (email/SMS)
/// - User registration (headless)
/// - Password reset (headless)
///
/// Mirrors Android NativeLoginManager for feature parity.
class NativeLoginManager {
  final Uri _loginServer;
  final String _clientId;
  final String _callbackUrl;
  final http.Client _httpClient;

  /// Whether the back button should be shown in native login UI.
  final bool shouldShowBackButton;

  /// Username for biometric re-authentication (if previously stored).
  final String? biometricAuthenticationUsername;

  static final _emailRegex = RegExp(r'^[^@\s]+@[^@\s]+\.[^@\s]+$');
  static const _maxUsernameLength = 80;
  static const _minPasswordLength = 8;
  static final _hasLetterRegex = RegExp(r'[a-zA-Z]');
  static final _hasDigitRegex = RegExp(r'[0-9]');

  NativeLoginManager({
    required Uri loginServer,
    required String clientId,
    required String callbackUrl,
    http.Client? httpClient,
    this.shouldShowBackButton = true,
    this.biometricAuthenticationUsername,
  })  : _loginServer = loginServer,
        _clientId = clientId,
        _callbackUrl = callbackUrl,
        _httpClient = httpClient ?? SalesforceHttpClient();

  /// Performs named-user login with username and password.
  ///
  /// Uses OAuth2 PKCE flow with Basic auth header containing credentials.
  Future<NativeLoginResult> login(String username, String password) async {
    // Validate inputs
    final usernameError = _validateUsername(username);
    if (usernameError != null) return usernameError;

    final passwordError = _validatePassword(password);
    if (passwordError != null) return passwordError;

    try {
      // Generate PKCE
      final codeVerifier = OAuth2.generateCodeVerifier();
      final codeChallenge = OAuth2.generateCodeChallenge(codeVerifier);

      // Encode credentials
      final credentials =
          base64Url.encode(utf8.encode('$username:$password'));

      // Step 1: Authorize with credentials
      final authorizeUrl =
          _loginServer.replace(path: '/services/oauth2/authorize');
      final authResponse = await _httpClient.post(
        authorizeUrl,
        headers: {
          'Authorization': 'Basic $credentials',
          'Content-Type': 'application/x-www-form-urlencoded',
          'Auth-Request-Type': 'Named-User',
        },
        body: {
          'response_type': 'code_credentials',
          'client_id': _clientId,
          'redirect_uri': _callbackUrl,
          'code_challenge': codeChallenge,
        },
      );

      if (authResponse.statusCode != 200) {
        if (authResponse.statusCode == 401 ||
            authResponse.statusCode == 403) {
          return NativeLoginResult.invalidCredentials;
        }
        return NativeLoginResult.unknownError;
      }

      final authJson = jsonDecode(authResponse.body) as Map<String, dynamic>;
      final code = authJson['code'] as String?;
      if (code == null) return NativeLoginResult.unknownError;

      // Step 2: Exchange code for tokens
      await OAuth2.exchangeCode(
        httpClient: _httpClient,
        loginServer: _loginServer,
        clientId: _clientId,
        code: code,
        codeVerifier: codeVerifier,
        callbackUrl: _callbackUrl,
      );

      return NativeLoginResult.success;
    } catch (e) {
      if (e is OAuthFailedException) {
        if (e.tokenErrorResponse?.error == 'invalid_grant') {
          return NativeLoginResult.invalidCredentials;
        }
      }
      return NativeLoginResult.unknownError;
    }
  }

  /// Starts the headless registration flow.
  ///
  /// Sends OTP to the provided email or phone for verification.
  Future<StartRegistrationResult> startRegistration({
    required String email,
    required String firstName,
    required String lastName,
    required String username,
    required String newPassword,
    required String reCaptchaToken,
    required OtpVerificationMethod otpVerificationMethod,
  }) async {
    final usernameError = _validateUsername(username);
    if (usernameError != null) {
      return StartRegistrationResult(nativeLoginResult: usernameError);
    }

    final passwordError = _validatePassword(newPassword);
    if (passwordError != null) {
      return StartRegistrationResult(nativeLoginResult: passwordError);
    }

    if (!_emailRegex.hasMatch(email)) {
      return const StartRegistrationResult(
          nativeLoginResult: NativeLoginResult.invalidEmail);
    }

    try {
      final url = _loginServer.replace(
          path: '/services/auth/headless/init/registration');
      final response = await _httpClient.post(
        url,
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
          'Auth-Request-Type': 'Named-User',
          'Auth-Verification-Type':
              otpVerificationMethod.identityApiAuthVerificationTypeHeaderValue,
        },
        body: {
          'email': email,
          'first_name': firstName,
          'last_name': lastName,
          'username': username,
          'newpassword': newPassword,
          'recaptcha': reCaptchaToken,
          'verificationmethod':
              otpVerificationMethod.identityApiAuthVerificationTypeHeaderValue,
        },
      );

      if (response.statusCode == 200) {
        final json = jsonDecode(response.body) as Map<String, dynamic>;
        return StartRegistrationResult(
          nativeLoginResult: NativeLoginResult.success,
          email: json['email'] as String?,
          requestIdentifier: json['identifier'] as String?,
        );
      }
      return const StartRegistrationResult(
          nativeLoginResult: NativeLoginResult.unknownError);
    } catch (_) {
      return const StartRegistrationResult(
          nativeLoginResult: NativeLoginResult.unknownError);
    }
  }

  /// Completes the registration flow with the OTP received.
  Future<NativeLoginResult> completeRegistration({
    required String otp,
    required String requestIdentifier,
    required OtpVerificationMethod otpVerificationMethod,
  }) async {
    try {
      final codeVerifier = OAuth2.generateCodeVerifier();
      final codeChallenge = OAuth2.generateCodeChallenge(codeVerifier);

      final url =
          _loginServer.replace(path: '/services/oauth2/authorize');
      final response = await _httpClient.post(
        url,
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
          'Auth-Request-Type': 'Named-User',
          'Auth-Verification-Type':
              otpVerificationMethod.identityApiAuthVerificationTypeHeaderValue,
        },
        body: {
          'response_type': 'code_credentials',
          'client_id': _clientId,
          'redirect_uri': _callbackUrl,
          'code_challenge': codeChallenge,
          'otp': otp,
          'identifier': requestIdentifier,
          'verificationmethod':
              otpVerificationMethod.identityApiAuthVerificationTypeHeaderValue,
        },
      );

      if (response.statusCode != 200) {
        return NativeLoginResult.unknownError;
      }

      final json = jsonDecode(response.body) as Map<String, dynamic>;
      final code = json['code'] as String?;
      if (code == null) return NativeLoginResult.unknownError;

      await OAuth2.exchangeCode(
        httpClient: _httpClient,
        loginServer: _loginServer,
        clientId: _clientId,
        code: code,
        codeVerifier: codeVerifier,
        callbackUrl: _callbackUrl,
      );

      return NativeLoginResult.success;
    } catch (_) {
      return NativeLoginResult.unknownError;
    }
  }

  /// Starts the password reset flow.
  Future<NativeLoginResult> startPasswordReset({
    required String username,
    required String reCaptchaToken,
  }) async {
    final usernameError = _validateUsername(username);
    if (usernameError != null) return usernameError;

    try {
      final url = _loginServer.replace(
          path: '/services/auth/headless/forgot_password');
      final response = await _httpClient.post(
        url,
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
        },
        body: {
          'username': username,
          'recaptcha': reCaptchaToken,
        },
      );

      return response.statusCode == 200
          ? NativeLoginResult.success
          : NativeLoginResult.unknownError;
    } catch (_) {
      return NativeLoginResult.unknownError;
    }
  }

  /// Completes the password reset with the OTP and new password.
  Future<NativeLoginResult> completePasswordReset({
    required String username,
    required String otp,
    required String newPassword,
  }) async {
    final passwordError = _validatePassword(newPassword);
    if (passwordError != null) return passwordError;

    try {
      final url = _loginServer.replace(
          path: '/services/auth/headless/forgot_password/reset');
      final response = await _httpClient.post(
        url,
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
        },
        body: {
          'username': username,
          'otp': otp,
          'newpassword': newPassword,
        },
      );

      return response.statusCode == 200
          ? NativeLoginResult.success
          : NativeLoginResult.unknownError;
    } catch (_) {
      return NativeLoginResult.unknownError;
    }
  }

  /// Submits an OTP request for passwordless login.
  Future<OtpRequestResult> submitOtpRequest({
    required String username,
    required String reCaptchaToken,
    required OtpVerificationMethod otpVerificationMethod,
  }) async {
    final usernameError = _validateUsername(username);
    if (usernameError != null) {
      return OtpRequestResult(nativeLoginResult: usernameError);
    }

    try {
      final url = _loginServer.replace(
          path: '/services/auth/headless/init/passwordless/login');
      final response = await _httpClient.post(
        url,
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
          'Auth-Verification-Type':
              otpVerificationMethod.identityApiAuthVerificationTypeHeaderValue,
        },
        body: {
          'username': username,
          'recaptcha': reCaptchaToken,
          'verificationmethod':
              otpVerificationMethod.identityApiAuthVerificationTypeHeaderValue,
        },
      );

      if (response.statusCode == 200) {
        final json = jsonDecode(response.body) as Map<String, dynamic>;
        return OtpRequestResult(
          nativeLoginResult: NativeLoginResult.success,
          otpIdentifier: json['identifier'] as String?,
        );
      }
      return const OtpRequestResult(
          nativeLoginResult: NativeLoginResult.unknownError);
    } catch (_) {
      return const OtpRequestResult(
          nativeLoginResult: NativeLoginResult.unknownError);
    }
  }

  /// Submits the OTP for passwordless authorization.
  Future<NativeLoginResult> submitPasswordlessAuthorizationRequest({
    required String otp,
    required String otpIdentifier,
    required OtpVerificationMethod otpVerificationMethod,
  }) async {
    try {
      final codeVerifier = OAuth2.generateCodeVerifier();
      final codeChallenge = OAuth2.generateCodeChallenge(codeVerifier);

      final url =
          _loginServer.replace(path: '/services/oauth2/authorize');
      final response = await _httpClient.post(
        url,
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
          'Auth-Request-Type': 'Passwordless-Login',
          'Auth-Verification-Type':
              otpVerificationMethod.identityApiAuthVerificationTypeHeaderValue,
        },
        body: {
          'response_type': 'code_credentials',
          'client_id': _clientId,
          'redirect_uri': _callbackUrl,
          'code_challenge': codeChallenge,
          'otp': otp,
          'identifier': otpIdentifier,
          'verificationmethod':
              otpVerificationMethod.identityApiAuthVerificationTypeHeaderValue,
        },
      );

      if (response.statusCode != 200) {
        return NativeLoginResult.unknownError;
      }

      final json = jsonDecode(response.body) as Map<String, dynamic>;
      final code = json['code'] as String?;
      if (code == null) return NativeLoginResult.unknownError;

      await OAuth2.exchangeCode(
        httpClient: _httpClient,
        loginServer: _loginServer,
        clientId: _clientId,
        code: code,
        codeVerifier: codeVerifier,
        callbackUrl: _callbackUrl,
      );

      return NativeLoginResult.success;
    } catch (_) {
      return NativeLoginResult.unknownError;
    }
  }

  NativeLoginResult? _validateUsername(String username) {
    if (username.isEmpty || username.length > _maxUsernameLength) {
      return NativeLoginResult.invalidUsername;
    }
    if (!_emailRegex.hasMatch(username)) {
      return NativeLoginResult.invalidEmail;
    }
    return null;
  }

  NativeLoginResult? _validatePassword(String password) {
    if (password.length < _minPasswordLength) {
      return NativeLoginResult.invalidPassword;
    }
    if (!_hasLetterRegex.hasMatch(password) ||
        !_hasDigitRegex.hasMatch(password)) {
      return NativeLoginResult.invalidPassword;
    }
    return null;
  }
}
