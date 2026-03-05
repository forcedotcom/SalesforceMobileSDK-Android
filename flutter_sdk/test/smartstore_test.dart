import 'package:flutter_test/flutter_test.dart';
import 'package:salesforce_sdk/salesforce_sdk.dart';

void main() {
  group('IndexSpec', () {
    test('pathType combines path and type', () {
      final spec = IndexSpec(path: 'Name', type: SmartStoreType.json1);
      expect(spec.pathType, equals('Name|json1'));
    });

    test('fullText pathType', () {
      final spec = IndexSpec.fullText('Description');
      expect(spec.pathType, equals('Description|fullText'));
    });

    test('fromJsonList parses array', () {
      final specs = IndexSpec.fromJsonList([
        {'path': 'Name', 'type': 'json1'},
        {'path': 'Email', 'type': 'json1'},
        {'path': 'Bio', 'type': 'fullText'},
      ]);
      expect(specs.length, equals(3));
      expect(specs[0].path, equals('Name'));
      expect(specs[2].type, equals(SmartStoreType.fullText));
    });

    test('mapForIndexSpecs creates correct map', () {
      final specs = [
        IndexSpec.json1('Name'),
        IndexSpec.json1('Email'),
        IndexSpec.fullText('Bio'),
      ];
      final map = IndexSpec.mapForIndexSpecs(specs);
      expect(map.length, equals(3));
      expect(map['Name']?.type, equals(SmartStoreType.json1));
      expect(map['Bio']?.type, equals(SmartStoreType.fullText));
    });

    test('hasJSON1 detects json1 specs', () {
      expect(IndexSpec.hasJSON1([IndexSpec.json1('Name')]), isTrue);
      expect(IndexSpec.hasJSON1([IndexSpec.fullText('Bio')]), isFalse);
    });

    test('equality works', () {
      final a = IndexSpec.json1('Name');
      final b = IndexSpec.json1('Name');
      final c = IndexSpec.fullText('Name');
      expect(a, equals(b));
      expect(a, isNot(equals(c)));
    });

    test('toString is readable', () {
      final spec = IndexSpec.json1('Name');
      expect(spec.toString(), contains('Name'));
      expect(spec.toString(), contains('json1'));
    });
  });

  group('QuerySpec', () {
    test('buildAllQuerySpec generates correct SQL', () {
      final spec = QuerySpec.buildAllQuerySpec(
        'Accounts',
        orderPath: 'Name',
        order: SortOrder.descending,
        pageSize: 25,
      );
      expect(spec.smartSql, contains('FROM {Accounts}'));
      expect(spec.smartSql, contains('ORDER BY'));
      expect(spec.smartSql, contains('DESC'));
      expect(spec.pageSize, equals(25));
    });

    test('buildAllQuerySpec without order', () {
      final spec = QuerySpec.buildAllQuerySpec('Contacts');
      expect(spec.smartSql, contains('FROM {Contacts}'));
      expect(spec.smartSql, isNot(contains('ORDER BY')));
    });

    test('buildExactQuerySpec generates WHERE clause', () {
      final spec = QuerySpec.buildExactQuerySpec(
        'Accounts',
        'Industry',
        'Technology',
      );
      expect(spec.smartSql, contains('WHERE'));
      expect(spec.smartSql, contains('{Accounts:Industry}'));
      expect(spec.smartSql, contains('= ?'));
      expect(spec.args, equals(['Technology']));
    });

    test('buildRangeQuerySpec with both bounds', () {
      final spec = QuerySpec.buildRangeQuerySpec(
        'Products',
        'Price',
        beginKey: '10',
        endKey: '100',
      );
      expect(spec.smartSql, contains('>='));
      expect(spec.smartSql, contains('<='));
      expect(spec.args, equals(['10', '100']));
    });

    test('buildRangeQuerySpec with only beginKey', () {
      final spec = QuerySpec.buildRangeQuerySpec(
        'Products',
        'Price',
        beginKey: '10',
      );
      expect(spec.smartSql, contains('>='));
      expect(spec.smartSql, isNot(contains('<=')));
      expect(spec.args, equals(['10']));
    });

    test('buildRangeQuerySpec with only endKey', () {
      final spec = QuerySpec.buildRangeQuerySpec(
        'Products',
        'Price',
        endKey: '100',
      );
      expect(spec.smartSql, contains('<='));
      expect(spec.args, equals(['100']));
    });

    test('buildLikeQuerySpec', () {
      final spec = QuerySpec.buildLikeQuerySpec(
        'Accounts',
        'Name',
        '%Acme%',
        order: SortOrder.ascending,
      );
      expect(spec.smartSql, contains('LIKE ?'));
      expect(spec.args, equals(['%Acme%']));
    });

    test('buildMatchQuerySpec for FTS', () {
      final spec = QuerySpec.buildMatchQuerySpec(
        'Articles',
        'Content',
        'salesforce flutter',
        pageSize: 20,
      );
      expect(spec.smartSql, contains('MATCH ?'));
      expect(spec.args, equals(['salesforce flutter']));
      expect(spec.pageSize, equals(20));
    });

    test('buildSmartQuerySpec with raw SQL', () {
      const sql = "SELECT {Accounts:Name} FROM {Accounts} WHERE {Accounts:Industry} = 'Tech'";
      final spec = QuerySpec.buildSmartQuerySpec(sql, pageSize: 50);
      expect(spec.smartSql, equals(sql));
      expect(spec.pageSize, equals(50));
      expect(spec.queryType, equals(QueryType.smart));
    });

    test('countSmartSql wraps query for count', () {
      final spec = QuerySpec.buildAllQuerySpec(
        'Accounts',
        orderPath: 'Name',
      );
      expect(spec.countSmartSql, contains('count(*)'));
      expect(spec.countSmartSql, contains('{Accounts}'));
    });

    test('exact query countSmartSql includes WHERE', () {
      final spec = QuerySpec.buildExactQuerySpec(
        'Accounts',
        'Name',
        'Acme',
      );
      expect(spec.countSmartSql, contains('count(*)'));
      expect(spec.countSmartSql, contains('WHERE'));
    });

    test('toJson and fromJson roundtrip', () {
      final spec = QuerySpec.buildExactQuerySpec(
        'Accounts',
        'Name',
        'Test',
        pageSize: 15,
        order: SortOrder.descending,
      );
      final json = spec.toJson();
      final restored = QuerySpec.fromJson(json);
      expect(restored.queryType, equals(QueryType.exact));
      expect(restored.soupName, equals('Accounts'));
      expect(restored.pageSize, equals(15));
      expect(restored.order, equals(SortOrder.descending));
    });

    test('buildAllQuerySpec with selectPaths', () {
      final spec = QuerySpec.buildAllQuerySpec(
        'Accounts',
        selectPaths: ['Name', 'Industry'],
      );
      expect(spec.smartSql, contains('{Accounts:Name}'));
      expect(spec.smartSql, contains('{Accounts:Industry}'));
    });

    test('toString is readable', () {
      final spec = QuerySpec.buildAllQuerySpec('Test');
      expect(spec.toString(), contains('Test'));
    });
  });

  group('SmartStore - project utility', () {
    test('extracts top-level field', () {
      final result = SmartStore.project(
        {'Name': 'Test', 'Industry': 'Tech'},
        'Name',
      );
      expect(result, equals('Test'));
    });

    test('extracts nested field', () {
      final result = SmartStore.project(
        {
          'Account': {'Name': 'Acme', 'Owner': {'Name': 'John'}}
        },
        'Account.Name',
      );
      expect(result, equals('Acme'));
    });

    test('extracts deeply nested field', () {
      final result = SmartStore.project(
        {
          'Account': {'Owner': {'Name': 'John'}}
        },
        'Account.Owner.Name',
      );
      expect(result, equals('John'));
    });

    test('returns null for missing field', () {
      final result = SmartStore.project({'Name': 'Test'}, 'Missing');
      expect(result, isNull);
    });

    test('returns null for missing nested path', () {
      final result = SmartStore.project({'Name': 'Test'}, 'Account.Name');
      expect(result, isNull);
    });
  });
}
