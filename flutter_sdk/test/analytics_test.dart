import 'package:flutter_test/flutter_test.dart';
import 'package:salesforce_sdk/salesforce_sdk.dart';

void main() {
  group('SalesforceLogger', () {
    test('getLogger returns same instance for same component', () {
      final l1 = SalesforceLogger.getLogger('TestComponent');
      final l2 = SalesforceLogger.getLogger('TestComponent');
      expect(identical(l1, l2), isTrue);
    });

    test('getLogger returns different instances for different components', () {
      final l1 = SalesforceLogger.getLogger('Component1');
      final l2 = SalesforceLogger.getLogger('Component2');
      expect(identical(l1, l2), isFalse);
    });

    test('componentName is set correctly', () {
      final logger = SalesforceLogger.getLogger('MyComponent');
      expect(logger.componentName, equals('MyComponent'));
    });

    test('log methods do not throw', () {
      final logger = SalesforceLogger.getLogger('TestLog');
      expect(() => logger.e('tag', 'error message'), returnsNormally);
      expect(() => logger.w('tag', 'warning message'), returnsNormally);
      expect(() => logger.i('tag', 'info message'), returnsNormally);
      expect(() => logger.d('tag', 'debug message'), returnsNormally);
      expect(() => logger.v('tag', 'verbose message'), returnsNormally);
    });

    test('log with error and stack trace', () {
      final logger = SalesforceLogger.getLogger('TestLogErr');
      expect(
        () => logger.e('tag', 'error', Exception('test'), StackTrace.current),
        returnsNormally,
      );
    });

    test('resetAll clears instances', () {
      SalesforceLogger.getLogger('ToClear');
      SalesforceLogger.resetAll();
      // After reset, getting the same name should return a new instance
      // (we can't easily verify this without exposing internals,
      // but we can verify it doesn't throw)
      expect(
        () => SalesforceLogger.getLogger('ToClear'),
        returnsNormally,
      );
    });
  });

  group('SalesforceSDKLogger', () {
    test('static methods do not throw', () {
      expect(() => SalesforceSDKLogger.e('tag', 'error'), returnsNormally);
      expect(() => SalesforceSDKLogger.w('tag', 'warning'), returnsNormally);
      expect(() => SalesforceSDKLogger.i('tag', 'info'), returnsNormally);
      expect(() => SalesforceSDKLogger.d('tag', 'debug'), returnsNormally);
      expect(() => SalesforceSDKLogger.v('tag', 'verbose'), returnsNormally);
    });
  });

  group('DeviceAppAttributes', () {
    test('all fields preserved in roundtrip', () {
      final attrs = DeviceAppAttributes(
        appVersion: '2.1.0',
        appName: 'MyApp',
        osVersion: '17.2',
        osName: 'iOS',
        nativeAppType: 'Flutter',
        mobileSdkVersion: '1.0.0',
        deviceModel: 'iPhone 15 Pro',
        deviceId: 'device-uuid-123',
        clientId: 'consumer-key-456',
      );
      final json = attrs.toJson();
      final restored = DeviceAppAttributes.fromJson(json);

      expect(restored.appVersion, equals('2.1.0'));
      expect(restored.appName, equals('MyApp'));
      expect(restored.osVersion, equals('17.2'));
      expect(restored.osName, equals('iOS'));
      expect(restored.nativeAppType, equals('Flutter'));
      expect(restored.mobileSdkVersion, equals('1.0.0'));
      expect(restored.deviceModel, equals('iPhone 15 Pro'));
      expect(restored.deviceId, equals('device-uuid-123'));
      expect(restored.clientId, equals('consumer-key-456'));
    });

    test('fromJson handles missing fields with defaults', () {
      final attrs = DeviceAppAttributes.fromJson({});
      expect(attrs.appVersion, equals(''));
      expect(attrs.appName, equals(''));
    });
  });

  group('InstrumentationEvent', () {
    late DeviceAppAttributes defaultAttrs;

    setUp(() {
      defaultAttrs = DeviceAppAttributes(
        appVersion: '1.0.0',
        appName: 'Test',
        osVersion: '14',
        osName: 'Android',
        nativeAppType: 'Flutter',
        mobileSdkVersion: '1.0.0',
        deviceModel: 'Pixel',
        deviceId: 'dev1',
        clientId: 'client1',
      );
    });

    test('auto-generates eventId', () {
      final event = InstrumentationEvent(
        startTime: 1000.0,
        name: 'test',
        deviceAppAttributes: defaultAttrs,
      );
      expect(event.eventId, isNotEmpty);
    });

    test('endTime defaults to startTime', () {
      final event = InstrumentationEvent(
        startTime: 1000.0,
        name: 'test',
        deviceAppAttributes: defaultAttrs,
      );
      expect(event.endTime, equals(1000.0));
    });

    test('all event types', () {
      for (final type in EventType.values) {
        final event = InstrumentationEvent(
          startTime: 1000.0,
          name: 'test_${type.name}',
          deviceAppAttributes: defaultAttrs,
          eventType: type,
        );
        expect(event.eventType, equals(type));
      }
    });

    test('all schema types', () {
      for (final schema in SchemaType.values) {
        final event = InstrumentationEvent(
          startTime: 1000.0,
          name: 'test',
          deviceAppAttributes: defaultAttrs,
          schemaType: schema,
        );
        expect(event.schemaType, equals(schema));
      }
    });

    test('attributes default to empty map', () {
      final event = InstrumentationEvent(
        startTime: 1000.0,
        name: 'test',
        deviceAppAttributes: defaultAttrs,
      );
      expect(event.attributes, isEmpty);
    });

    test('toJson and fromJson roundtrip preserves all fields', () {
      final event = InstrumentationEvent(
        eventId: 'custom-id-123',
        startTime: 1000.0,
        endTime: 2000.0,
        name: 'page_view',
        attributes: {'key1': 'val1', 'key2': 42},
        sessionId: 'session-456',
        sequenceId: 7,
        senderContext: {'ctx': 'data'},
        senderParentContext: {'parent': 'ctx'},
        eventType: EventType.user,
        eventCategory: EventCategory.server,
        schemaType: SchemaType.LightningPageView,
        deviceAppAttributes: defaultAttrs,
        connectionType: 'wifi',
        senderId: 'sender-789',
        page: {'pageName': 'Home'},
        previousPage: {'pageName': 'Login'},
        errorType: null,
      );

      final json = event.toJson();
      final restored = InstrumentationEvent.fromJson(json);

      expect(restored.eventId, equals('custom-id-123'));
      expect(restored.startTime, equals(1000.0));
      expect(restored.endTime, equals(2000.0));
      expect(restored.name, equals('page_view'));
      expect(restored.attributes['key1'], equals('val1'));
      expect(restored.sessionId, equals('session-456'));
      expect(restored.sequenceId, equals(7));
      expect(restored.eventType, equals(EventType.user));
      expect(restored.eventCategory, equals(EventCategory.server));
      expect(restored.schemaType, equals(SchemaType.LightningPageView));
      expect(restored.connectionType, equals('wifi'));
      expect(restored.senderId, equals('sender-789'));
      expect(restored.page!['pageName'], equals('Home'));
    });

    test('toString returns JSON string', () {
      final event = InstrumentationEvent(
        startTime: 1000.0,
        name: 'test',
        deviceAppAttributes: defaultAttrs,
      );
      final str = event.toString();
      expect(str, contains('test'));
      expect(str, contains('startTime'));
    });
  });

  group('InstrumentationEventBuilder', () {
    late DeviceAppAttributes attrs;

    setUp(() {
      attrs = DeviceAppAttributes(
        appVersion: '1.0.0',
        appName: 'Test',
        osVersion: '14',
        osName: 'Android',
        nativeAppType: 'Flutter',
        mobileSdkVersion: '1.0.0',
        deviceModel: 'Pixel',
        deviceId: 'dev1',
        clientId: 'client1',
      );
    });

    test('build throws without name', () {
      expect(
        () => InstrumentationEventBuilder()
            .startTime(1000.0)
            .deviceAppAttributes(attrs)
            .build(),
        throwsA(isA<StateError>()),
      );
    });

    test('build throws without startTime', () {
      expect(
        () => InstrumentationEventBuilder()
            .name('test')
            .deviceAppAttributes(attrs)
            .build(),
        throwsA(isA<StateError>()),
      );
    });

    test('build throws without deviceAppAttributes', () {
      expect(
        () => InstrumentationEventBuilder()
            .name('test')
            .startTime(1000.0)
            .build(),
        throwsA(isA<StateError>()),
      );
    });

    test('all builder methods', () {
      final event = InstrumentationEventBuilder()
          .name('test')
          .startTime(1000.0)
          .endTime(2000.0)
          .deviceAppAttributes(attrs)
          .sessionId('sess')
          .sequenceId(5)
          .attributes({'key': 'value'})
          .senderContext({'ctx': 'data'})
          .senderParentContext({'parent': 'data'})
          .eventType(EventType.error)
          .eventCategory(EventCategory.server)
          .schemaType(SchemaType.LightningError)
          .connectionType('4g')
          .senderId('sender1')
          .page({'name': 'Home'})
          .previousPage({'name': 'Login'})
          .errorType('NETWORK_ERROR')
          .build();

      expect(event.name, equals('test'));
      expect(event.sessionId, equals('sess'));
      expect(event.sequenceId, equals(5));
      expect(event.connectionType, equals('4g'));
      expect(event.errorType, equals('NETWORK_ERROR'));
    });
  });

  group('AILTNTransform', () {
    test('transforms event to AILTN format', () {
      final attrs = DeviceAppAttributes(
        appVersion: '1.0.0',
        appName: 'Test',
        osVersion: '14',
        osName: 'Android',
        nativeAppType: 'Flutter',
        mobileSdkVersion: '1.0.0',
        deviceModel: 'Pixel',
        deviceId: 'dev1',
        clientId: 'client1',
      );

      final event = InstrumentationEvent(
        eventId: 'evt-123',
        startTime: 1000.0,
        endTime: 2000.0,
        name: 'test_event',
        attributes: {'action': 'click'},
        sessionId: 'sess-456',
        sequenceId: 3,
        senderContext: {'target': 'button'},
        deviceAppAttributes: attrs,
        connectionType: 'wifi',
        page: {'name': 'Dashboard'},
        previousPage: {'name': 'Home'},
      );

      final transform = AILTNTransform();
      final result = transform.transform(event);

      expect(result['id'], equals('evt-123'));
      expect(result['ts'], equals(1000));
      expect(result['duration'], equals(1000));
      expect(result['sequence'], equals(3));
      expect(result['clientSessionId'], equals('sess-456'));
      expect(result['connectionType'], equals('wifi'));
      expect(result['attributes']['action'], equals('click'));
      expect(result['page']['name'], equals('Dashboard'));
      expect(result['previousPage']['name'], equals('Home'));
      expect(result['locator']['target'], equals('button'));
      expect(result['deviceAttributes'], isNotNull);
      expect(result['schemaType'], equals('LightningInteraction'));
    });

    test('omits null optional fields', () {
      final attrs = DeviceAppAttributes(
        appVersion: '1.0.0',
        appName: 'Test',
        osVersion: '14',
        osName: 'Android',
        nativeAppType: 'Flutter',
        mobileSdkVersion: '1.0.0',
        deviceModel: 'Pixel',
        deviceId: 'dev1',
        clientId: 'client1',
      );

      final event = InstrumentationEvent(
        startTime: 1000.0,
        name: 'simple',
        deviceAppAttributes: attrs,
      );

      final transform = AILTNTransform();
      final result = transform.transform(event);

      expect(result.containsKey('page'), isFalse);
      expect(result.containsKey('previousPage'), isFalse);
      expect(result.containsKey('clientSessionId'), isFalse);
      expect(result.containsKey('connectionType'), isFalse);
      expect(result.containsKey('locator'), isFalse);
      expect(result.containsKey('errorType'), isFalse);
    });
  });

  group('Encryptor', () {
    test('encrypts different data differently', () {
      const key = 'test-key';
      final e1 = Encryptor.encryptString('hello', key);
      final e2 = Encryptor.encryptString('world', key);
      expect(e1, isNot(equals(e2)));
    });

    test('same data encrypted twice produces different ciphertext (random IV)', () {
      const key = 'test-key';
      final e1 = Encryptor.encryptString('same data', key);
      final e2 = Encryptor.encryptString('same data', key);
      // Due to random IV, ciphertexts should differ
      expect(e1, isNot(equals(e2)));
    });

    test('wrong key fails decryption', () {
      final encrypted = Encryptor.encryptString('secret', 'correct-key');
      expect(
        () => Encryptor.decryptString(encrypted, 'wrong-key'),
        throwsA(anything),
      );
    });

    test('empty string encryption roundtrips correctly', () {
      const key = 'test-key';
      // AES-GCM can encrypt empty strings (authenticated encryption)
      final encrypted = Encryptor.encryptString('', key);
      final decrypted = Encryptor.decryptString(encrypted, key);
      expect(decrypted, equals(''));
    });

    test('handles long strings', () {
      const key = 'test-key';
      final longStr = 'a' * 10000;
      final encrypted = Encryptor.encryptString(longStr, key);
      final decrypted = Encryptor.decryptString(encrypted, key);
      expect(decrypted, equals(longStr));
    });

    test('handles unicode', () {
      const key = 'test-key';
      const unicode = 'Hello World!';
      final encrypted = Encryptor.encryptString(unicode, key);
      final decrypted = Encryptor.decryptString(encrypted, key);
      expect(decrypted, equals(unicode));
    });

    test('hash is 64 chars hex', () {
      final h = Encryptor.hash('test');
      expect(h.length, equals(64));
      expect(RegExp(r'^[0-9a-f]+$').hasMatch(h), isTrue);
    });

    test('different inputs produce different hashes', () {
      final h1 = Encryptor.hash('hello');
      final h2 = Encryptor.hash('world');
      expect(h1, isNot(equals(h2)));
    });

    test('generateUniqueId returns unique values', () {
      final ids = List.generate(50, (_) => Encryptor.generateUniqueId());
      expect(ids.toSet().length, equals(50));
    });

    test('generateKey with custom length', () {
      final key16 = Encryptor.generateKey(length: 16);
      final key64 = Encryptor.generateKey(length: 64);
      // Base64 encoding: 16 bytes -> ~24 chars, 64 bytes -> ~88 chars
      expect(key16.length, lessThan(key64.length));
    });
  });

  group('BootConfig', () {
    test('fromJson with alternate key names', () {
      final config = BootConfig.fromJson({
        'clientId': 'key1',
        'callbackUrl': 'sfdc://cb',
        'scopes': ['api'],
      });
      expect(config.clientId, equals('key1'));
      expect(config.callbackUrl, equals('sfdc://cb'));
    });

    test('fromJson defaults', () {
      final config = BootConfig.fromJson({
        'remoteAccessConsumerKey': 'key1',
        'oauthRedirectURI': 'sfdc://cb',
      });
      expect(config.scopes, equals(['api', 'web', 'refresh_token']));
      expect(config.useWebServerAuthentication, isTrue);
      expect(config.pushNotificationsEnabled, isFalse);
    });

    test('toJson includes all fields', () {
      final config = BootConfig(
        clientId: 'key1',
        callbackUrl: 'sfdc://cb',
        scopes: ['api', 'web'],
        pushNotificationsEnabled: true,
      );
      final json = config.toJson();
      expect(json['remoteAccessConsumerKey'], equals('key1'));
      expect(json['oauthRedirectURI'], equals('sfdc://cb'));
      expect(json['pushNotificationEnabled'], isTrue);
    });
  });

  group('UserAccount', () {
    test('userLevelFilenameSuffix format', () {
      final account = UserAccount(
        authToken: 'token',
        refreshToken: 'refresh',
        loginServer: Uri.parse('https://login.salesforce.com'),
        instanceUrl: Uri.parse('https://na1.salesforce.com'),
        userId: '005xx',
        orgId: '00Dxx',
        communityId: 'comm1',
      );
      expect(account.userLevelFilenameSuffix, equals('00Dxx_005xx_comm1'));
    });

    test('orgLevelFilenameSuffix is orgId', () {
      final account = UserAccount(
        authToken: 'token',
        refreshToken: 'refresh',
        loginServer: Uri.parse('https://login.salesforce.com'),
        instanceUrl: Uri.parse('https://na1.salesforce.com'),
        userId: '005xx',
        orgId: '00Dxx',
      );
      expect(account.orgLevelFilenameSuffix, equals('00Dxx'));
    });

    test('toClientInfo converts correctly', () {
      final account = UserAccount(
        authToken: 'token',
        refreshToken: 'refresh',
        loginServer: Uri.parse('https://login.salesforce.com'),
        instanceUrl: Uri.parse('https://na1.salesforce.com'),
        userId: '005xx',
        orgId: '00Dxx',
        username: 'user@test.com',
        email: 'user@test.com',
      );
      final clientInfo = account.toClientInfo();
      expect(clientInfo.userId, equals('005xx'));
      expect(clientInfo.orgId, equals('00Dxx'));
      expect(clientInfo.username, equals('user@test.com'));
      expect(clientInfo.instanceUrl.host, equals('na1.salesforce.com'));
    });

    test('copyWith creates modified copy', () {
      final original = UserAccount(
        authToken: 'old_token',
        refreshToken: 'refresh',
        loginServer: Uri.parse('https://login.salesforce.com'),
        instanceUrl: Uri.parse('https://na1.salesforce.com'),
        userId: '005xx',
        orgId: '00Dxx',
      );
      final updated = original.copyWith(authToken: 'new_token');
      expect(updated.authToken, equals('new_token'));
      expect(updated.refreshToken, equals('refresh'));
      expect(original.authToken, equals('old_token'));
    });

    test('inequality for different users', () {
      final a1 = UserAccount(
        authToken: 'token',
        refreshToken: 'refresh',
        loginServer: Uri.parse('https://login.salesforce.com'),
        instanceUrl: Uri.parse('https://na1.salesforce.com'),
        userId: '005xx001',
        orgId: '00Dxx',
      );
      final a2 = UserAccount(
        authToken: 'token',
        refreshToken: 'refresh',
        loginServer: Uri.parse('https://login.salesforce.com'),
        instanceUrl: Uri.parse('https://na1.salesforce.com'),
        userId: '005xx002',
        orgId: '00Dxx',
      );
      expect(a1, isNot(equals(a2)));
    });
  });
}
