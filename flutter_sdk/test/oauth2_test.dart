import 'package:flutter_test/flutter_test.dart';
import 'package:salesforce_sdk/salesforce_sdk.dart';

void main() {
  group('OAuth2 - PKCE', () {
    test('code verifier has minimum length', () {
      for (var i = 0; i < 10; i++) {
        final verifier = OAuth2.generateCodeVerifier();
        expect(verifier.length, greaterThanOrEqualTo(43));
      }
    });

    test('code verifiers are unique', () {
      final verifiers =
          List.generate(100, (_) => OAuth2.generateCodeVerifier());
      expect(verifiers.toSet().length, equals(100));
    });

    test('code challenge is deterministic for same verifier', () {
      const verifier = 'test_verifier_string_12345678901234567890123';
      final c1 = OAuth2.generateCodeChallenge(verifier);
      final c2 = OAuth2.generateCodeChallenge(verifier);
      expect(c1, equals(c2));
    });

    test('different verifiers produce different challenges', () {
      final v1 = OAuth2.generateCodeVerifier();
      final v2 = OAuth2.generateCodeVerifier();
      final c1 = OAuth2.generateCodeChallenge(v1);
      final c2 = OAuth2.generateCodeChallenge(v2);
      expect(c1, isNot(equals(c2)));
    });
  });

  group('OAuth2 - Authorization URL', () {
    test('web server flow uses response_type=code', () {
      final url = OAuth2.getAuthorizationUrl(
        loginServer: Uri.parse('https://login.salesforce.com'),
        clientId: 'test',
        callbackUrl: 'sfdc://callback',
        scopes: ['api'],
        useWebServerAuthentication: true,
      );
      expect(url.queryParameters['response_type'], equals('code'));
    });

    test('user agent flow uses response_type=token', () {
      final url = OAuth2.getAuthorizationUrl(
        loginServer: Uri.parse('https://login.salesforce.com'),
        clientId: 'test',
        callbackUrl: 'sfdc://callback',
        scopes: ['api'],
        useWebServerAuthentication: false,
      );
      expect(url.queryParameters['response_type'], equals('token'));
    });

    test('login hint is included when provided', () {
      final url = OAuth2.getAuthorizationUrl(
        loginServer: Uri.parse('https://login.salesforce.com'),
        clientId: 'test',
        callbackUrl: 'sfdc://callback',
        scopes: ['api'],
        loginHint: 'user@example.com',
      );
      expect(
          url.queryParameters['login_hint'], equals('user@example.com'));
    });

    test('code challenge method is S256', () {
      final url = OAuth2.getAuthorizationUrl(
        loginServer: Uri.parse('https://login.salesforce.com'),
        clientId: 'test',
        callbackUrl: 'sfdc://callback',
        scopes: ['api'],
        codeChallenge: 'challenge123',
      );
      expect(url.queryParameters['code_challenge_method'], equals('S256'));
    });

    test('sandbox login server URL', () {
      final url = OAuth2.getAuthorizationUrl(
        loginServer: Uri.parse('https://test.salesforce.com'),
        clientId: 'test',
        callbackUrl: 'sfdc://callback',
        scopes: ['api'],
      );
      expect(url.host, equals('test.salesforce.com'));
    });

    test('additional params are included', () {
      final url = OAuth2.getAuthorizationUrl(
        loginServer: Uri.parse('https://login.salesforce.com'),
        clientId: 'test',
        callbackUrl: 'sfdc://callback',
        scopes: ['api'],
        additionalParams: {'prompt': 'login', 'nonce': 'abc123'},
      );
      expect(url.queryParameters['prompt'], equals('login'));
      expect(url.queryParameters['nonce'], equals('abc123'));
    });

    test('multiple scopes joined with space', () {
      final url = OAuth2.getAuthorizationUrl(
        loginServer: Uri.parse('https://login.salesforce.com'),
        clientId: 'test',
        callbackUrl: 'sfdc://callback',
        scopes: ['api', 'web', 'refresh_token'],
      );
      expect(url.queryParameters['scope'], equals('api web refresh_token'));
    });
  });

  group('OAuth2 - Token Response', () {
    test('fromJson parses all fields', () {
      final response = TokenEndpointResponse.fromJson({
        'access_token': 'access123',
        'refresh_token': 'refresh456',
        'instance_url': 'https://na1.salesforce.com',
        'id': 'https://login.salesforce.com/id/00Dxx/005xx',
        'sfdc_community_id': 'comm123',
        'sfdc_community_url': 'https://community.example.com',
        'lightning_domain': 'lightning.example.com',
        'visualforce_domain': 'vf.example.com',
        'content_domain': 'content.example.com',
        'csrf_token': 'csrf789',
        'id_token': 'jwt_id_token',
        'token_format': 'JWT',
      });

      expect(response.authToken, equals('access123'));
      expect(response.refreshToken, equals('refresh456'));
      expect(response.instanceUrl, equals('https://na1.salesforce.com'));
      expect(response.orgId, equals('00Dxx'));
      expect(response.userId, equals('005xx'));
      expect(response.communityId, equals('comm123'));
      expect(response.lightningDomain, equals('lightning.example.com'));
      expect(response.vfDomain, equals('vf.example.com'));
      expect(response.contentDomain, equals('content.example.com'));
      expect(response.csrfToken, equals('csrf789'));
      expect(response.idToken, equals('jwt_id_token'));
    });

    test('fromCallbackParams parses URL params', () {
      final response = TokenEndpointResponse.fromCallbackParams({
        'access_token': 'token',
        'refresh_token': 'refresh',
        'instance_url': 'https://na1.salesforce.com',
        'id': 'https://login.salesforce.com/id/00D/005',
      });

      expect(response.authToken, equals('token'));
      expect(response.orgId, equals('00D'));
      expect(response.userId, equals('005'));
    });

    test('handles missing id URL gracefully', () {
      final response = TokenEndpointResponse.fromJson({
        'access_token': 'token',
      });
      expect(response.orgId, isNull);
      expect(response.userId, isNull);
    });

    test('toJson roundtrip', () {
      final response = TokenEndpointResponse(
        authToken: 'token',
        refreshToken: 'refresh',
        instanceUrl: 'https://na1.salesforce.com',
      );
      final json = response.toJson();
      expect(json['access_token'], equals('token'));
      expect(json['refresh_token'], equals('refresh'));
    });
  });

  group('OAuth2 - IdServiceResponse', () {
    test('fromJson parses user info', () {
      final response = IdServiceResponse.fromJson({
        'username': 'user@test.com',
        'email': 'user@test.com',
        'first_name': 'Test',
        'last_name': 'User',
        'display_name': 'Test User',
        'photos': {
          'picture': 'https://example.com/photo.jpg',
          'thumbnail': 'https://example.com/thumb.jpg',
        },
      });

      expect(response.username, equals('user@test.com'));
      expect(response.firstName, equals('Test'));
      expect(response.lastName, equals('User'));
      expect(response.displayName, equals('Test User'));
      expect(response.pictureUrl, equals('https://example.com/photo.jpg'));
    });

    test('fromJson parses mobile policy', () {
      final response = IdServiceResponse.fromJson({
        'username': 'user@test.com',
        'mobile_policy': {
          'screen_lock': true,
          'screen_lock_timeout': 10,
          'biometric_auth': true,
          'biometric_auth_timeout': 5,
        },
      });

      expect(response.screenLock, isTrue);
      expect(response.screenLockTimeout, equals(10));
      expect(response.biometricAuth, isTrue);
      expect(response.biometricAuthTimeout, equals(5));
    });

    test('handles missing mobile policy', () {
      final response = IdServiceResponse.fromJson({
        'username': 'user@test.com',
      });
      expect(response.screenLock, isFalse);
      expect(response.screenLockTimeout, equals(0));
    });
  });

  group('OAuth2 - Exceptions', () {
    test('OAuthFailedException properties', () {
      final error = OAuthFailedException('test error', 401,
          TokenErrorResponse(error: 'invalid_grant'));
      expect(error.message, equals('test error'));
      expect(error.httpStatusCode, equals(401));
      expect(error.isRefreshTokenInvalid, isTrue);
    });

    test('non-invalid_grant is not refresh token invalid', () {
      final error = OAuthFailedException('test', 400,
          TokenErrorResponse(error: 'invalid_client'));
      expect(error.isRefreshTokenInvalid, isFalse);
    });

    test('null tokenErrorResponse means not refresh invalid', () {
      final error = OAuthFailedException('test', 500);
      expect(error.isRefreshTokenInvalid, isFalse);
    });
  });
}
