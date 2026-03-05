import 'package:flutter_test/flutter_test.dart';
import 'package:salesforce_sdk/salesforce_sdk.dart';

void main() {
  group('SyncState', () {
    test('all status helpers', () {
      for (final status in SyncStatus.values) {
        final state = SyncState(
          id: 1,
          type: SyncType.syncDown,
          target: {},
          options: SyncOptions.defaultOptions,
          soupName: 'test',
          status: status,
        );
        switch (status) {
          case SyncStatus.done:
            expect(state.isDone, isTrue);
            expect(state.isRunning, isFalse);
            expect(state.hasFailed, isFalse);
            expect(state.isStopped, isFalse);
            break;
          case SyncStatus.running:
            expect(state.isDone, isFalse);
            expect(state.isRunning, isTrue);
            expect(state.hasFailed, isFalse);
            break;
          case SyncStatus.failed:
            expect(state.isDone, isFalse);
            expect(state.hasFailed, isTrue);
            break;
          case SyncStatus.stopped:
            expect(state.isStopped, isTrue);
            break;
          case SyncStatus.newSync:
            expect(state.isDone, isFalse);
            expect(state.isRunning, isFalse);
            break;
        }
      }
    });

    test('mergeMode delegates to options', () {
      final state = SyncState(
        id: 1,
        type: SyncType.syncUp,
        target: {},
        options: const SyncOptions(mergeMode: MergeMode.leaveIfChanged),
        soupName: 'test',
      );
      expect(state.mergeMode, equals(MergeMode.leaveIfChanged));
    });

    test('fromJson handles all fields', () {
      final state = SyncState.fromJson({
        '_soupEntryId': 42,
        'type': 'syncDown',
        'name': 'mySync',
        'target': {'type': 'SoqlSyncDownTarget', 'query': 'SELECT Id FROM Account'},
        'options': {'mergeMode': 'overwrite'},
        'soupName': 'Accounts',
        'status': 'running',
        'progress': 50,
        'totalSize': 100,
        'maxTimeStamp': 1234567890,
        'startTime': 1000.0,
        'endTime': 2000.0,
        'error': null,
      });

      expect(state.id, equals(42));
      expect(state.type, equals(SyncType.syncDown));
      expect(state.name, equals('mySync'));
      expect(state.soupName, equals('Accounts'));
      expect(state.status, equals(SyncStatus.running));
      expect(state.progress, equals(50));
      expect(state.totalSize, equals(100));
      expect(state.maxTimeStamp, equals(1234567890));
      expect(state.startTime, equals(1000.0));
      expect(state.endTime, equals(2000.0));
    });

    test('fromJson with missing fields uses defaults', () {
      final state = SyncState.fromJson({
        'type': 'syncUp',
        'target': {},
        'options': {},
        'soupName': 'Test',
      });

      expect(state.id, equals(0));
      expect(state.type, equals(SyncType.syncUp));
      expect(state.status, equals(SyncStatus.newSync));
      expect(state.progress, equals(0));
      expect(state.totalSize, equals(-1));
      expect(state.maxTimeStamp, equals(-1));
    });

    test('copy creates independent copy', () {
      final original = SyncState(
        id: 1,
        type: SyncType.syncDown,
        target: {'query': 'SELECT Id FROM Account'},
        options: SyncOptions.defaultOptions,
        soupName: 'Accounts',
        status: SyncStatus.running,
        progress: 25,
      );
      final copy = original.copy();
      copy.progress = 50;
      copy.status = SyncStatus.done;

      expect(original.progress, equals(25));
      expect(original.status, equals(SyncStatus.running));
      expect(copy.progress, equals(50));
      expect(copy.status, equals(SyncStatus.done));
    });

    test('toJson includes all fields', () {
      final state = SyncState(
        id: 1,
        type: SyncType.syncUp,
        name: 'uploadSync',
        target: {'type': 'DefaultSyncUpTarget'},
        options: const SyncOptions(
          mergeMode: MergeMode.leaveIfChanged,
          fieldlist: ['Name'],
        ),
        soupName: 'Contacts',
        status: SyncStatus.done,
        progress: 10,
        totalSize: 10,
        maxTimeStamp: 999,
        startTime: 100.0,
        endTime: 200.0,
        error: null,
      );

      final json = state.toJson();
      expect(json['type'], equals('syncUp'));
      expect(json['name'], equals('uploadSync'));
      expect(json['soupName'], equals('Contacts'));
      expect(json['status'], equals('done'));
      expect(json['progress'], equals(10));
    });

    test('toString is readable', () {
      final state = SyncState(
        id: 5,
        type: SyncType.syncDown,
        target: {},
        options: SyncOptions.defaultOptions,
        soupName: 'test',
        status: SyncStatus.running,
        progress: 3,
        totalSize: 10,
      );
      final str = state.toString();
      expect(str, contains('id=5'));
      expect(str, contains('syncDown'));
      expect(str, contains('running'));
      expect(str, contains('3/10'));
    });
  });

  group('SyncOptions', () {
    test('default fieldlist is null', () {
      expect(SyncOptions.defaultOptions.fieldlist, isNull);
    });

    test('fromJson with fieldlist', () {
      final options = SyncOptions.fromJson({
        'mergeMode': 'leaveIfChanged',
        'fieldlist': ['Name', 'Industry', 'Phone'],
      });
      expect(options.mergeMode, equals(MergeMode.leaveIfChanged));
      expect(options.fieldlist, equals(['Name', 'Industry', 'Phone']));
    });

    test('fromJson with invalid mergeMode defaults to overwrite', () {
      final options = SyncOptions.fromJson({
        'mergeMode': 'invalid_mode',
      });
      expect(options.mergeMode, equals(MergeMode.overwrite));
    });

    test('toJson omits null fieldlist', () {
      final json = SyncOptions.defaultOptions.toJson();
      expect(json.containsKey('fieldlist'), isFalse);
    });
  });

  group('SyncDownTarget - Serialization', () {
    test('SoqlSyncDownTarget roundtrip', () {
      final target = SoqlSyncDownTarget(
        query: 'SELECT Id, Name, Industry FROM Account',
        idFieldName: 'Id',
        modificationDateFieldName: 'SystemModstamp',
      );
      final json = target.toJson();
      expect(json['type'], equals('SoqlSyncDownTarget'));
      expect(json['query'], contains('Account'));
      expect(json['modificationDateFieldName'], equals('SystemModstamp'));

      final restored = SoqlSyncDownTarget.fromJson(json);
      expect(restored.query, equals(target.query));
      expect(
          restored.modificationDateFieldName, equals('SystemModstamp'));
    });

    test('SoslSyncDownTarget roundtrip', () {
      final target = SoslSyncDownTarget(
        query: 'FIND {test} IN ALL FIELDS RETURNING Account(Id, Name)',
      );
      final json = target.toJson();
      expect(json['type'], equals('SoslSyncDownTarget'));

      final restored = SoslSyncDownTarget.fromJson(json);
      expect(restored.query, equals(target.query));
    });

    test('RefreshSyncDownTarget roundtrip', () {
      final target = RefreshSyncDownTarget(
        objectType: 'Account',
        fieldlist: ['Id', 'Name', 'Industry'],
        soupName: 'Accounts',
      );
      final json = target.toJson();
      expect(json['type'], equals('RefreshSyncDownTarget'));
      expect(json['objectType'], equals('Account'));
      expect(json['fieldlist'], equals(['Id', 'Name', 'Industry']));

      final restored = RefreshSyncDownTarget.fromJson(json);
      expect(restored.objectType, equals('Account'));
      expect(restored.fieldlist, equals(['Id', 'Name', 'Industry']));
    });

    test('fromJson dispatches to correct subclass', () {
      final soqlTarget = SyncDownTarget.fromJson({
        'type': 'SoqlSyncDownTarget',
        'query': 'SELECT Id FROM Account',
      });
      expect(soqlTarget, isA<SoqlSyncDownTarget>());

      final soslTarget = SyncDownTarget.fromJson({
        'type': 'SoslSyncDownTarget',
        'query': 'FIND {test}',
      });
      expect(soslTarget, isA<SoslSyncDownTarget>());

      final refreshTarget = SyncDownTarget.fromJson({
        'type': 'RefreshSyncDownTarget',
        'objectType': 'Account',
        'fieldlist': ['Id'],
        'soupName': 'Accounts',
      });
      expect(refreshTarget, isA<RefreshSyncDownTarget>());
    });

    test('fromJson defaults to SoqlSyncDownTarget', () {
      final target = SyncDownTarget.fromJson({
        'query': 'SELECT Id FROM Account',
      });
      expect(target, isA<SoqlSyncDownTarget>());
    });
  });

  group('SyncUpTarget - Serialization', () {
    test('DefaultSyncUpTarget with all fields', () {
      final target = DefaultSyncUpTarget(
        createFieldlist: ['Name', 'Industry'],
        updateFieldlist: ['Name'],
        idFieldName: 'Id',
        modificationDateFieldName: 'LastModifiedDate',
        externalIdFieldName: 'External_Id__c',
      );
      final json = target.toJson();
      expect(json['createFieldlist'], equals(['Name', 'Industry']));
      expect(json['updateFieldlist'], equals(['Name']));
      expect(json['externalIdFieldName'], equals('External_Id__c'));

      final restored = DefaultSyncUpTarget.fromJson(json);
      expect(restored.createFieldlist, equals(['Name', 'Industry']));
      expect(restored.externalIdFieldName, equals('External_Id__c'));
    });

    test('DefaultSyncUpTarget with null fieldlists', () {
      final target = DefaultSyncUpTarget();
      final json = target.toJson();
      expect(json['createFieldlist'], isNull);
      expect(json['updateFieldlist'], isNull);
      expect(json['externalIdFieldName'], isNull);
    });

    test('fromJson creates DefaultSyncUpTarget', () {
      final target = SyncUpTarget.fromJson({
        'createFieldlist': ['Name'],
      });
      expect(target, isA<DefaultSyncUpTarget>());
    });

    test('default field names', () {
      final target = DefaultSyncUpTarget();
      expect(target.idFieldName, equals('Id'));
      expect(target.modificationDateFieldName, equals('LastModifiedDate'));
    });
  });

  group('SyncDownTarget - getLatestModificationTimeStamp', () {
    test('extracts max timestamp from records', () {
      final target = SoqlSyncDownTarget(query: 'SELECT Id FROM Account');
      final records = [
        {'Id': '001', 'LastModifiedDate': '2024-01-01T00:00:00.000Z'},
        {'Id': '002', 'LastModifiedDate': '2024-06-15T12:00:00.000Z'},
        {'Id': '003', 'LastModifiedDate': '2024-03-10T06:00:00.000Z'},
      ];
      final ts = target.getLatestModificationTimeStamp(records);
      expect(ts, greaterThan(0));
      // June 15 should be the latest
      final expectedMs =
          DateTime.parse('2024-06-15T12:00:00.000Z').millisecondsSinceEpoch;
      expect(ts, equals(expectedMs));
    });

    test('returns 0 for empty records', () {
      final target = SoqlSyncDownTarget(query: 'SELECT Id FROM Account');
      final ts = target.getLatestModificationTimeStamp([]);
      expect(ts, equals(0));
    });

    test('handles missing timestamp field', () {
      final target = SoqlSyncDownTarget(query: 'SELECT Id FROM Account');
      final records = [
        {'Id': '001'},
      ];
      final ts = target.getLatestModificationTimeStamp(records);
      expect(ts, equals(0));
    });
  });

  group('MobileSyncException', () {
    test('message and cause', () {
      final ex = MobileSyncException('test error', 'underlying cause');
      expect(ex.message, equals('test error'));
      expect(ex.cause, equals('underlying cause'));
      expect(ex.toString(), contains('test error'));
    });

    test('without cause', () {
      final ex = MobileSyncException('simple error');
      expect(ex.cause, isNull);
    });
  });
}
