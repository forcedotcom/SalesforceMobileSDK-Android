import 'dart:convert';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:salesforce_sdk/salesforce_sdk.dart';

void main() {
  group('RestRequest - Factory Methods', () {
    test('getRequestForResources', () {
      final req = RestRequest.getRequestForResources('59.0');
      expect(req.method, equals(RestMethod.get));
      expect(req.path, equals('/services/data/v59.0/'));
    });

    test('getRequestForDescribeGlobal', () {
      final req = RestRequest.getRequestForDescribeGlobal('59.0');
      expect(req.path, equals('/services/data/v59.0/sobjects/'));
    });

    test('getRequestForMetadata', () {
      final req = RestRequest.getRequestForMetadata('59.0', 'Account');
      expect(req.path, contains('sobjects/Account/'));
    });

    test('getRequestForDescribe', () {
      final req = RestRequest.getRequestForDescribe('59.0', 'Account');
      expect(req.path, contains('Account/describe/'));
    });

    test('getRequestForRetrieve includes fields', () {
      final req = RestRequest.getRequestForRetrieve(
        '59.0',
        'Account',
        '001xxx',
        ['Name', 'Industry'],
      );
      expect(req.path, contains('001xxx'));
      expect(req.path, contains('Name,Industry'));
    });

    test('getRequestForUpsert', () {
      final req = RestRequest.getRequestForUpsert(
        '59.0',
        'Account',
        'ExternalId__c',
        'ext-123',
        {'Name': 'Test'},
      );
      expect(req.method, equals(RestMethod.patch));
      expect(req.path, contains('ExternalId__c/ext-123'));
      expect(req.requestBody!['Name'], equals('Test'));
    });

    test('getRequestForQuery with batch size', () {
      final req = RestRequest.getRequestForQuery(
        '59.0',
        'SELECT Id FROM Account',
        batchSize: 500,
      );
      expect(req.additionalHttpHeaders!['Sforce-Query-Options'],
          equals('batchSize=500'));
    });

    test('getRequestForQueryAll', () {
      final req =
          RestRequest.getRequestForQueryAll('59.0', 'SELECT Id FROM Account');
      expect(req.path, contains('queryAll'));
    });

    test('getRequestForSearchScopeAndOrder', () {
      final req = RestRequest.getRequestForSearchScopeAndOrder('59.0');
      expect(req.path, contains('scopeOrder'));
    });

    test('getRequestForLimits', () {
      final req = RestRequest.getRequestForLimits('59.0');
      expect(req.path, contains('limits'));
    });

    test('getRequestForUserInfo', () {
      final req = RestRequest.getRequestForUserInfo();
      expect(req.path, equals('/services/oauth2/userinfo'));
    });

    test('getRequestForUpdate with ifUnmodifiedSince', () {
      final date = DateTime(2024, 1, 15, 10, 30);
      final req = RestRequest.getRequestForUpdate(
        '59.0',
        'Account',
        '001xxx',
        {'Name': 'Test'},
        ifUnmodifiedSinceDate: date,
      );
      expect(req.additionalHttpHeaders, isNotNull);
      expect(req.additionalHttpHeaders!['If-Unmodified-Since'], isNotNull);
    });
  });

  group('RestRequest - Collection Operations', () {
    test('getRequestForCollectionCreate', () {
      final req = RestRequest.getRequestForCollectionCreate(
        '59.0',
        true,
        [
          {'attributes': {'type': 'Account'}, 'Name': 'Test1'},
          {'attributes': {'type': 'Account'}, 'Name': 'Test2'},
        ],
      );
      expect(req.method, equals(RestMethod.post));
      expect(req.path, contains('composite/sobjects'));
      expect(req.requestBody!['allOrNone'], isTrue);
      expect(
          (req.requestBody!['records'] as List).length, equals(2));
    });

    test('getRequestForCollectionDelete', () {
      final req = RestRequest.getRequestForCollectionDelete(
        '59.0',
        false,
        ['001xxx', '001yyy'],
      );
      expect(req.method, equals(RestMethod.delete));
      expect(req.path, contains('001xxx,001yyy'));
    });
  });

  group('RestRequest - Composite & Batch', () {
    test('getCompositeRequest', () {
      final req = RestRequest.getCompositeRequest(
        '59.0',
        true,
        {
          'ref1': RestRequest.getRequestForCreate(
              '59.0', 'Account', {'Name': 'Test'}),
          'ref2': RestRequest.getRequestForQuery(
              '59.0', 'SELECT Id FROM Account'),
        },
      );
      expect(req.method, equals(RestMethod.post));
      expect(req.requestBody!['allOrNone'], isTrue);
      final compositeReqs =
          req.requestBody!['compositeRequest'] as List;
      expect(compositeReqs.length, equals(2));
    });

    test('getBatchRequest', () {
      final req = RestRequest.getBatchRequest(
        '59.0',
        false,
        [
          RestRequest.getRequestForVersions(),
          RestRequest.getRequestForLimits('59.0'),
        ],
      );
      expect(req.method, equals(RestMethod.post));
      expect(req.requestBody!['haltOnError'], isFalse);
      final batchReqs = req.requestBody!['batchRequests'] as List;
      expect(batchReqs.length, equals(2));
    });
  });

  group('RestRequest - Notifications', () {
    test('getRequestForNotificationsStatus', () {
      final req = RestRequest.getRequestForNotificationsStatus('59.0');
      expect(req.path, contains('notifications/status'));
    });

    test('getRequestForNotification', () {
      final req = RestRequest.getRequestForNotification('59.0', 'notif123');
      expect(req.path, contains('notif123'));
    });

    test('getRequestForNotificationUpdate', () {
      final req = RestRequest.getRequestForNotificationUpdate(
        '59.0',
        'notif123',
        read: true,
        seen: true,
      );
      expect(req.method, equals(RestMethod.patch));
      expect(req.requestBody!['read'], isTrue);
      expect(req.requestBody!['seen'], isTrue);
    });

    test('getRequestForNotifications with params', () {
      final req = RestRequest.getRequestForNotifications(
        '59.0',
        size: 10,
      );
      expect(req.path, contains('size=10'));
    });
  });

  group('RestRequest - Misc', () {
    test('bodyAsString returns JSON', () {
      final req = RestRequest(
        method: RestMethod.post,
        path: '/test',
        requestBody: {'key': 'value'},
      );
      final body = req.bodyAsString;
      expect(body, isNotNull);
      final parsed = jsonDecode(body!);
      expect(parsed['key'], equals('value'));
    });

    test('bodyAsString returns rawBody when set', () {
      final req = RestRequest(
        method: RestMethod.post,
        path: '/test',
        rawBody: 'raw content',
      );
      expect(req.bodyAsString, equals('raw content'));
    });

    test('bodyAsString returns null for GET', () {
      final req = RestRequest(method: RestMethod.get, path: '/test');
      expect(req.bodyAsString, isNull);
    });

    test('toString includes method and path', () {
      final req = RestRequest(method: RestMethod.get, path: '/test/path');
      expect(req.toString(), contains('GET'));
      expect(req.toString(), contains('/test/path'));
    });

    test('getCheapRequest returns a valid request', () {
      final req = RestRequest.getCheapRequest('59.0');
      expect(req.method, equals(RestMethod.get));
    });
  });

  group('RestResponse', () {
    test('isSuccess for 200', () {
      final response = RestResponse(http.Response('{}', 200));
      expect(response.isSuccess, isTrue);
      expect(response.statusCode, equals(200));
    });

    test('isSuccess for 201', () {
      final response = RestResponse(http.Response('{}', 201));
      expect(response.isSuccess, isTrue);
    });

    test('isSuccess false for 400', () {
      final response = RestResponse(http.Response('{}', 400));
      expect(response.isSuccess, isFalse);
    });

    test('isSuccess false for 500', () {
      final response = RestResponse(http.Response('{}', 500));
      expect(response.isSuccess, isFalse);
    });

    test('asJsonObject parses response body', () {
      final response = RestResponse(
          http.Response('{"key": "value"}', 200));
      expect(response.asJsonObject()['key'], equals('value'));
    });

    test('asJsonObject caches result', () {
      final response = RestResponse(
          http.Response('{"key": "value"}', 200));
      final obj1 = response.asJsonObject();
      final obj2 = response.asJsonObject();
      expect(identical(obj1, obj2), isTrue);
    });

    test('asJsonArray parses array', () {
      final response = RestResponse(
          http.Response('[1, 2, 3]', 200));
      expect(response.asJsonArray(), equals([1, 2, 3]));
    });

    test('query response helpers', () {
      final body = jsonEncode({
        'totalSize': 5,
        'done': false,
        'nextRecordsUrl': '/services/data/v59.0/query/next',
        'records': [
          {'Id': '001', 'Name': 'Test'}
        ],
      });
      final response = RestResponse(http.Response(body, 200));
      expect(response.totalSize, equals(5));
      expect(response.isDone, isFalse);
      expect(response.nextRecordsUrl, isNotNull);
      expect(response.records, isNotNull);
      expect(response.records!.length, equals(1));
    });

    test('isDone true when response is done', () {
      final body = jsonEncode({
        'totalSize': 1,
        'done': true,
        'records': [],
      });
      final response = RestResponse(http.Response(body, 200));
      expect(response.isDone, isTrue);
    });

    test('totalSize returns null for non-query response', () {
      final response = RestResponse(http.Response('"hello"', 200));
      expect(response.totalSize, isNull);
    });

    test('static isSuccessStatus', () {
      expect(RestResponse.isSuccessStatus(200), isTrue);
      expect(RestResponse.isSuccessStatus(299), isTrue);
      expect(RestResponse.isSuccessStatus(300), isFalse);
      expect(RestResponse.isSuccessStatus(404), isFalse);
    });
  });

  group('RestClient', () {
    test('sends GET request with auth header', () async {
      final mockClient = MockClient((request) async {
        expect(request.headers['Authorization'], startsWith('Bearer '));
        return http.Response('{"ok": true}', 200);
      });

      final client = RestClient(
        clientInfo: ClientInfo(
          instanceUrl: Uri.parse('https://na1.salesforce.com'),
          loginUrl: Uri.parse('https://login.salesforce.com'),
          userId: '005xx',
          orgId: '00Dxx',
        ),
        authToken: 'test_token',
        httpClient: mockClient,
      );

      final response = await client.sendAsync(
        RestRequest(method: RestMethod.get, path: '/services/data/v59.0/'),
      );
      expect(response.isSuccess, isTrue);
    });

    test('sends POST request with body', () async {
      final mockClient = MockClient((request) async {
        expect(request.method, equals('POST'));
        final body = jsonDecode(request.body);
        expect(body['Name'], equals('Test'));
        return http.Response('{"id": "001xxx"}', 201);
      });

      final client = RestClient(
        clientInfo: ClientInfo(
          instanceUrl: Uri.parse('https://na1.salesforce.com'),
          loginUrl: Uri.parse('https://login.salesforce.com'),
          userId: '005xx',
          orgId: '00Dxx',
        ),
        authToken: 'test_token',
        httpClient: mockClient,
      );

      final response = await client.sendAsync(
        RestRequest.getRequestForCreate('59.0', 'Account', {'Name': 'Test'}),
      );
      expect(response.isSuccess, isTrue);
      expect(response.asJsonObject()['id'], equals('001xxx'));
    });

    test('getJsonCredentials includes token info', () {
      final client = RestClient(
        clientInfo: ClientInfo(
          instanceUrl: Uri.parse('https://na1.salesforce.com'),
          loginUrl: Uri.parse('https://login.salesforce.com'),
          userId: '005xx',
          orgId: '00Dxx',
        ),
        authToken: 'test_token',
      );

      final creds = client.getJsonCredentials();
      expect(creds['accessToken'], equals('test_token'));
      expect(creds['instanceUrl'], equals('https://na1.salesforce.com'));
      expect(creds['orgId'], equals('00Dxx'));
      expect(creds['userId'], equals('005xx'));
    });
  });

  group('ClientInfo', () {
    test('uniqueId format', () {
      final info = ClientInfo(
        instanceUrl: Uri.parse('https://na1.salesforce.com'),
        loginUrl: Uri.parse('https://login.salesforce.com'),
        userId: '005xx',
        orgId: '00Dxx',
      );
      expect(info.uniqueId, equals('00Dxx_005xx'));
    });

    test('resolveUrl for instance endpoint', () {
      final info = ClientInfo(
        instanceUrl: Uri.parse('https://na1.salesforce.com'),
        loginUrl: Uri.parse('https://login.salesforce.com'),
        userId: '005xx',
        orgId: '00Dxx',
      );
      final url = info.resolveUrl('/services/data/v59.0/');
      expect(url.host, equals('na1.salesforce.com'));
    });

    test('resolveUrl for login endpoint', () {
      final info = ClientInfo(
        instanceUrl: Uri.parse('https://na1.salesforce.com'),
        loginUrl: Uri.parse('https://login.salesforce.com'),
        userId: '005xx',
        orgId: '00Dxx',
      );
      final url =
          info.resolveUrl('/services/oauth2/token', endpoint: RestEndpoint.login);
      expect(url.host, equals('login.salesforce.com'));
    });

    test('resolveUrl with full URL returns it unchanged', () {
      final info = ClientInfo(
        instanceUrl: Uri.parse('https://na1.salesforce.com'),
        loginUrl: Uri.parse('https://login.salesforce.com'),
        userId: '005xx',
        orgId: '00Dxx',
      );
      final url = info.resolveUrl('https://other.salesforce.com/path');
      expect(url.host, equals('other.salesforce.com'));
    });

    test('toJson and fromJson roundtrip', () {
      final info = ClientInfo(
        instanceUrl: Uri.parse('https://na1.salesforce.com'),
        loginUrl: Uri.parse('https://login.salesforce.com'),
        userId: '005xx',
        orgId: '00Dxx',
        username: 'user@test.com',
        email: 'user@test.com',
      );
      final json = info.toJson();
      final restored = ClientInfo.fromJson(json);
      expect(restored.userId, equals('005xx'));
      expect(restored.username, equals('user@test.com'));
    });
  });
}
