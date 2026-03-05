import 'package:flutter_test/flutter_test.dart';
import 'package:salesforce_sdk/salesforce_sdk.dart';

void main() {
  group('OAuth2', () {
    test('generateCodeVerifier returns non-empty string', () {
      final verifier = OAuth2.generateCodeVerifier();
      expect(verifier, isNotEmpty);
      expect(verifier.length, greaterThanOrEqualTo(43));
    });

    test('generateCodeChallenge returns valid challenge', () {
      final verifier = OAuth2.generateCodeVerifier();
      final challenge = OAuth2.generateCodeChallenge(verifier);
      expect(challenge, isNotEmpty);
      expect(challenge, isNot(equals(verifier)));
    });

    test('getAuthorizationUrl builds correct URL', () {
      final url = OAuth2.getAuthorizationUrl(
        loginServer: Uri.parse('https://login.salesforce.com'),
        clientId: 'test_client_id',
        callbackUrl: 'sfdc://callback',
        scopes: ['api', 'web'],
        codeChallenge: 'test_challenge',
      );

      expect(url.host, equals('login.salesforce.com'));
      expect(url.path, equals('/services/oauth2/authorize'));
      expect(url.queryParameters['client_id'], equals('test_client_id'));
      expect(url.queryParameters['redirect_uri'], equals('sfdc://callback'));
      expect(url.queryParameters['response_type'], equals('code'));
      expect(url.queryParameters['code_challenge'], equals('test_challenge'));
    });

    test('computeScopeParameter joins scopes', () {
      final scope = OAuth2.computeScopeParameter(['api', 'web', 'refresh_token']);
      expect(scope, equals('api web refresh_token'));
    });
  });

  group('RestRequest', () {
    test('getRequestForVersions', () {
      final request = RestRequest.getRequestForVersions();
      expect(request.method, equals(RestMethod.get));
      expect(request.path, equals('/services/data/'));
    });

    test('getRequestForQuery', () {
      final request = RestRequest.getRequestForQuery(
        '59.0',
        'SELECT Id FROM Account',
      );
      expect(request.method, equals(RestMethod.get));
      expect(request.path, contains('query'));
      expect(request.path, contains('SELECT'));
    });

    test('getRequestForCreate', () {
      final request = RestRequest.getRequestForCreate(
        '59.0',
        'Account',
        {'Name': 'Test'},
      );
      expect(request.method, equals(RestMethod.post));
      expect(request.path, contains('Account'));
      expect(request.requestBody, isNotNull);
      expect(request.requestBody!['Name'], equals('Test'));
    });

    test('getRequestForUpdate', () {
      final request = RestRequest.getRequestForUpdate(
        '59.0',
        'Account',
        '001xx000003DGXYZ',
        {'Name': 'Updated'},
      );
      expect(request.method, equals(RestMethod.patch));
      expect(request.path, contains('001xx000003DGXYZ'));
    });

    test('getRequestForDelete', () {
      final request = RestRequest.getRequestForDelete(
        '59.0',
        'Account',
        '001xx000003DGXYZ',
      );
      expect(request.method, equals(RestMethod.delete));
    });

    test('getRequestForSearch', () {
      final request = RestRequest.getRequestForSearch(
        '59.0',
        'FIND {test}',
      );
      expect(request.method, equals(RestMethod.get));
      expect(request.path, contains('search'));
    });
  });

  group('IndexSpec', () {
    test('json1 factory constructor', () {
      final spec = IndexSpec.json1('Name');
      expect(spec.path, equals('Name'));
      expect(spec.type, equals(SmartStoreType.json1));
    });

    test('fullText factory constructor', () {
      final spec = IndexSpec.fullText('Description');
      expect(spec.path, equals('Description'));
      expect(spec.type, equals(SmartStoreType.fullText));
    });

    test('toJson and fromJson roundtrip', () {
      final spec = IndexSpec(
          path: 'Name', type: SmartStoreType.json1, columnName: 'col1');
      final json = spec.toJson();
      final restored = IndexSpec.fromJson(json);
      expect(restored.path, equals('Name'));
      expect(restored.type, equals(SmartStoreType.json1));
      expect(restored.columnName, equals('col1'));
    });

    test('hasFTS detects full-text specs', () {
      final specs = [
        IndexSpec.json1('Name'),
        IndexSpec.fullText('Description'),
      ];
      expect(IndexSpec.hasFTS(specs), isTrue);
      expect(
          IndexSpec.hasFTS([IndexSpec.json1('Name')]), isFalse);
    });
  });

  group('QuerySpec', () {
    test('buildAllQuerySpec', () {
      final spec = QuerySpec.buildAllQuerySpec(
        'Accounts',
        orderPath: 'Name',
        pageSize: 25,
      );
      expect(spec.queryType, equals(QueryType.smart));
      expect(spec.soupName, equals('Accounts'));
      expect(spec.pageSize, equals(25));
      expect(spec.smartSql, contains('FROM {Accounts}'));
    });

    test('buildExactQuerySpec', () {
      final spec = QuerySpec.buildExactQuerySpec(
        'Accounts',
        'Name',
        'Acme',
        pageSize: 10,
      );
      expect(spec.queryType, equals(QueryType.exact));
      expect(spec.smartSql, contains('WHERE'));
      expect(spec.args, equals(['Acme']));
    });

    test('buildLikeQuerySpec', () {
      final spec = QuerySpec.buildLikeQuerySpec(
        'Accounts',
        'Name',
        '%Acme%',
      );
      expect(spec.queryType, equals(QueryType.like));
      expect(spec.smartSql, contains('LIKE'));
      expect(spec.args, equals(['%Acme%']));
    });

    test('buildSmartQuerySpec', () {
      final spec = QuerySpec.buildSmartQuerySpec(
        'SELECT {Accounts:Name} FROM {Accounts}',
        pageSize: 50,
      );
      expect(spec.queryType, equals(QueryType.smart));
      expect(spec.pageSize, equals(50));
    });
  });

  group('SyncState', () {
    test('fromJson and toJson roundtrip', () {
      final state = SyncState(
        id: 1,
        type: SyncType.syncDown,
        name: 'testSync',
        target: {'type': 'SoqlSyncDownTarget', 'query': 'SELECT Id FROM Account'},
        options: const SyncOptions(mergeMode: MergeMode.overwrite),
        soupName: 'Accounts',
        status: SyncStatus.done,
        progress: 10,
        totalSize: 10,
      );
      final json = state.toJson();
      final restored = SyncState.fromJson(json);
      expect(restored.name, equals('testSync'));
      expect(restored.type, equals(SyncType.syncDown));
      expect(restored.status, equals(SyncStatus.done));
      expect(restored.soupName, equals('Accounts'));
    });

    test('status helpers', () {
      final state = SyncState(
        id: 1,
        type: SyncType.syncUp,
        target: {},
        options: SyncOptions.defaultOptions,
        soupName: 'test',
        status: SyncStatus.running,
      );
      expect(state.isRunning, isTrue);
      expect(state.isDone, isFalse);
      expect(state.hasFailed, isFalse);
    });
  });

  group('SyncOptions', () {
    test('defaultOptions has overwrite mode', () {
      expect(SyncOptions.defaultOptions.mergeMode, equals(MergeMode.overwrite));
    });

    test('fromJson and toJson roundtrip', () {
      final options = SyncOptions(
        mergeMode: MergeMode.leaveIfChanged,
        fieldlist: ['Name', 'Industry'],
      );
      final json = options.toJson();
      final restored = SyncOptions.fromJson(json);
      expect(restored.mergeMode, equals(MergeMode.leaveIfChanged));
      expect(restored.fieldlist, equals(['Name', 'Industry']));
    });
  });

  group('UserAccount', () {
    test('uniqueId', () {
      final account = UserAccount(
        authToken: 'token',
        refreshToken: 'refresh',
        loginServer: Uri.parse('https://login.salesforce.com'),
        instanceUrl: Uri.parse('https://na1.salesforce.com'),
        userId: '005xx000001',
        orgId: '00Dxx0000001',
      );
      expect(account.uniqueId, equals('00Dxx0000001_005xx000001'));
    });

    test('fromJson and toJson roundtrip', () {
      final account = UserAccount(
        authToken: 'token',
        refreshToken: 'refresh',
        loginServer: Uri.parse('https://login.salesforce.com'),
        instanceUrl: Uri.parse('https://na1.salesforce.com'),
        userId: '005xx000001',
        orgId: '00Dxx0000001',
        username: 'test@test.com',
        firstName: 'Test',
        lastName: 'User',
      );
      final json = account.toJson();
      final restored = UserAccount.fromJson(json);
      expect(restored.userId, equals('005xx000001'));
      expect(restored.username, equals('test@test.com'));
      expect(restored.firstName, equals('Test'));
    });

    test('equality', () {
      final a1 = UserAccount(
        authToken: 'token1',
        refreshToken: 'refresh1',
        loginServer: Uri.parse('https://login.salesforce.com'),
        instanceUrl: Uri.parse('https://na1.salesforce.com'),
        userId: '005xx000001',
        orgId: '00Dxx0000001',
      );
      final a2 = UserAccount(
        authToken: 'token2',
        refreshToken: 'refresh2',
        loginServer: Uri.parse('https://login.salesforce.com'),
        instanceUrl: Uri.parse('https://na1.salesforce.com'),
        userId: '005xx000001',
        orgId: '00Dxx0000001',
      );
      expect(a1, equals(a2));
    });
  });

  group('Encryptor', () {
    test('encrypt and decrypt roundtrip', () {
      const key = 'test-encryption-key-123';
      const plaintext = 'Hello, Salesforce!';
      final encrypted = Encryptor.encryptString(plaintext, key);
      final decrypted = Encryptor.decryptString(encrypted, key);
      expect(decrypted, equals(plaintext));
    });

    test('hash returns consistent results', () {
      final hash1 = Encryptor.hash('test');
      final hash2 = Encryptor.hash('test');
      expect(hash1, equals(hash2));
      expect(hash1, isNotEmpty);
    });

    test('generateKey returns unique keys', () {
      final key1 = Encryptor.generateKey();
      final key2 = Encryptor.generateKey();
      expect(key1, isNot(equals(key2)));
    });
  });

  group('DeviceAppAttributes', () {
    test('fromJson and toJson roundtrip', () {
      final attrs = DeviceAppAttributes(
        appVersion: '1.0.0',
        appName: 'TestApp',
        osVersion: '14.0',
        osName: 'iOS',
        nativeAppType: 'Flutter',
        mobileSdkVersion: '1.0.0',
        deviceModel: 'iPhone 15',
        deviceId: 'test-device-id',
        clientId: 'test-client-id',
      );
      final json = attrs.toJson();
      final restored = DeviceAppAttributes.fromJson(json);
      expect(restored.appName, equals('TestApp'));
      expect(restored.nativeAppType, equals('Flutter'));
    });
  });

  group('InstrumentationEvent', () {
    test('builder creates valid event', () {
      final attrs = DeviceAppAttributes(
        appVersion: '1.0.0',
        appName: 'TestApp',
        osVersion: '14.0',
        osName: 'iOS',
        nativeAppType: 'Flutter',
        mobileSdkVersion: '1.0.0',
        deviceModel: 'iPhone 15',
        deviceId: 'test-device-id',
        clientId: 'test-client-id',
      );
      final event = InstrumentationEventBuilder()
          .name('test_event')
          .startTime(1000.0)
          .endTime(2000.0)
          .deviceAppAttributes(attrs)
          .eventType(EventType.user)
          .build();

      expect(event.name, equals('test_event'));
      expect(event.startTime, equals(1000.0));
      expect(event.endTime, equals(2000.0));
      expect(event.eventId, isNotEmpty);
    });
  });

  group('BootConfig', () {
    test('fromJson parses correctly', () {
      final config = BootConfig.fromJson({
        'remoteAccessConsumerKey': 'testKey',
        'oauthRedirectURI': 'sfdc://callback',
        'oauthScopes': ['api', 'web'],
      });
      expect(config.clientId, equals('testKey'));
      expect(config.callbackUrl, equals('sfdc://callback'));
      expect(config.scopes, equals(['api', 'web']));
    });
  });

  group('SyncDownTarget', () {
    test('SoqlSyncDownTarget serialization', () {
      final target = SoqlSyncDownTarget(
        query: 'SELECT Id, Name FROM Account',
      );
      final json = target.toJson();
      expect(json['type'], equals('SoqlSyncDownTarget'));
      expect(json['query'], equals('SELECT Id, Name FROM Account'));

      final restored = SoqlSyncDownTarget.fromJson(json);
      expect(restored.query, equals('SELECT Id, Name FROM Account'));
    });
  });

  group('SyncUpTarget', () {
    test('DefaultSyncUpTarget serialization', () {
      final target = DefaultSyncUpTarget(
        createFieldlist: ['Name', 'Industry'],
        updateFieldlist: ['Name'],
      );
      final json = target.toJson();
      expect(json['createFieldlist'], equals(['Name', 'Industry']));
      expect(json['updateFieldlist'], equals(['Name']));
    });
  });
}
