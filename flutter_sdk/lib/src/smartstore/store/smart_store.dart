import 'dart:convert';
import 'package:sqflite_sqlcipher/sqflite.dart';
import 'package:path/path.dart' as path_lib;
import '../../analytics/logger/salesforce_logger.dart';
import '../../core/security/encryptor.dart';
import 'index_spec.dart';
import 'query_spec.dart';
import 'schema_migration.dart';
import 'smart_sql_validator.dart';

/// SmartStore entry ID field name.
const String soupEntryId = '_soupEntryId';

/// SmartStore last modified date field name.
const String soupLastModifiedDate = '_soupLastModifiedDate';

/// Internal soup field.
const String soupField = '_soup';

/// SmartStore - local encrypted SQLite-based storage for Salesforce data.
///
/// Provides a document-oriented (JSON) store built on SQLite with:
/// - Named "soups" (analogous to tables)
/// - Configurable indexes (JSON1, full-text search)
/// - Full CRUD operations on JSON documents
/// - Query support (exact, range, LIKE, match, smart SQL)
/// - Pagination
/// - Full-database encryption via SQLCipher
/// - Application-layer encryption for JSON data
///
/// Usage:
/// ```dart
/// final store = await SmartStore.getInstance(encryptionKey: 'my-key');
/// store.registerSoup('Accounts', [IndexSpec.json1('Name')]);
/// final id = await store.create('Accounts', {'Name': 'Acme'});
/// ```
class SmartStore {
  static const String _tag = 'SmartStore';
  static const String _soupAttrTable = 'soup_attrs';
  static const String _soupIndexMapTable = 'soup_index_map';

  static final SalesforceLogger _logger =
      SalesforceLogger.getLogger('SmartStore');
  static final Map<String, SmartStore> _instances = {};

  final String _dbName;
  final String? _encryptionKey;
  final String? _dbPassword;
  Database? _database;

  SmartStore._({
    required String dbName,
    String? encryptionKey,
    String? dbPassword,
  })  : _dbName = dbName,
        _encryptionKey = encryptionKey,
        _dbPassword = dbPassword;

  /// Gets or creates a SmartStore instance.
  ///
  /// [dbPassword] is the SQLCipher database encryption password.
  /// [encryptionKey] is an additional application-layer encryption key
  /// for the JSON data stored in soups.
  ///
  /// For maximum security, provide both. [dbPassword] encrypts the
  /// entire database file, while [encryptionKey] adds a second layer
  /// of encryption to the stored JSON documents.
  static Future<SmartStore> getInstance({
    String dbName = 'smartstore',
    String? encryptionKey,
    String? dbPassword,
  }) async {
    if (_instances.containsKey(dbName)) {
      return _instances[dbName]!;
    }
    if (dbPassword == null && encryptionKey == null) {
      _logger.w(_tag,
          'SmartStore created without encryption. Data will be stored in plaintext.');
    }
    final store = SmartStore._(
        dbName: dbName, encryptionKey: encryptionKey, dbPassword: dbPassword);
    await store._openDatabase();
    _instances[dbName] = store;
    return store;
  }

  /// Gets the underlying database (for advanced use).
  Database? get database => _database;

  // ===== Soup Management =====

  /// Registers a new soup with the given index specifications.
  Future<void> registerSoup(
      String soupName, List<IndexSpec> indexSpecs) async {
    SmartSqlValidator.validateSoupName(soupName);

    if (await hasSoup(soupName)) {
      _logger.w(_tag, 'Soup $soupName already exists');
      return;
    }

    final db = _ensureDb();
    await db.transaction((txn) async {
      final tableName = _soupTableName(soupName);
      await txn.execute('''
        CREATE TABLE IF NOT EXISTS "$tableName" (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          soup TEXT NOT NULL,
          created TEXT NOT NULL,
          lastModified TEXT NOT NULL
        )
      ''');

      await txn.insert(_soupAttrTable, {
        'soupName': soupName,
        'tableName': tableName,
      });

      for (var i = 0; i < indexSpecs.length; i++) {
        final spec = indexSpecs[i];
        SmartSqlValidator.validateFieldPath(spec.path);
        final colName = spec.columnName ?? '${tableName}_$i';

        await txn.insert(_soupIndexMapTable, {
          'soupName': soupName,
          'path': spec.path,
          'columnName': colName,
          'columnType': spec.type.name,
        });
      }

      if (IndexSpec.hasFTS(indexSpecs)) {
        final ftsPaths = indexSpecs
            .where((s) => s.type == SmartStoreType.fullText)
            .map((s) => s.path)
            .toList();
        await txn.execute('''
          CREATE VIRTUAL TABLE IF NOT EXISTS "${tableName}_fts"
          USING fts5(${ftsPaths.join(', ')}, content="$tableName")
        ''');
      }
    });

    _logger.i(_tag, 'Registered soup: $soupName');
  }

  /// Checks if a soup exists.
  Future<bool> hasSoup(String soupName) async {
    final db = _ensureDb();
    final result = await db.query(
      _soupAttrTable,
      where: 'soupName = ?',
      whereArgs: [soupName],
    );
    return result.isNotEmpty;
  }

  /// Drops (deletes) a soup and all its data.
  Future<void> dropSoup(String soupName) async {
    final db = _ensureDb();
    final tableName = await _getTableName(soupName);
    if (tableName == null) return;

    await db.transaction((txn) async {
      await txn.execute('DROP TABLE IF EXISTS "$tableName"');
      await txn.execute('DROP TABLE IF EXISTS "${tableName}_fts"');
      await txn.delete(_soupAttrTable,
          where: 'soupName = ?', whereArgs: [soupName]);
      await txn.delete(_soupIndexMapTable,
          where: 'soupName = ?', whereArgs: [soupName]);
    });

    _logger.i(_tag, 'Dropped soup: $soupName');
  }

  /// Gets the index specs for a soup.
  Future<List<IndexSpec>> getSoupIndexSpecs(String soupName) async {
    final db = _ensureDb();
    final results = await db.query(
      _soupIndexMapTable,
      where: 'soupName = ?',
      whereArgs: [soupName],
    );
    return results.map((row) {
      return IndexSpec(
        path: row['path'] as String,
        type: SmartStoreType.values.firstWhere(
          (t) => t.name == row['columnType'],
          orElse: () => SmartStoreType.json1,
        ),
        columnName: row['columnName'] as String?,
      );
    }).toList();
  }

  /// Returns all soup names.
  Future<List<String>> getAllSoupNames() async {
    final db = _ensureDb();
    final results = await db.query(_soupAttrTable, columns: ['soupName']);
    return results.map((r) => r['soupName'] as String).toList();
  }

  /// Alters a soup's index specifications.
  Future<void> alterSoup(
    String soupName,
    List<IndexSpec> newIndexSpecs, {
    bool reIndexing = true,
  }) async {
    final existingData = await _getAllSoupData(soupName);
    await dropSoup(soupName);
    await registerSoup(soupName, newIndexSpecs);

    for (final entry in existingData) {
      await create(soupName, entry);
    }

    _logger.i(_tag, 'Altered soup: $soupName');
  }

  // ===== CRUD Operations =====

  /// Creates a new entry in the soup. Returns the entry ID.
  Future<int> create(String soupName, Map<String, dynamic> entry) async {
    final db = _ensureDb();
    final tableName = await _getTableName(soupName);
    if (tableName == null) {
      throw SmartStoreException('Soup $soupName does not exist');
    }

    final now = DateTime.now().toIso8601String();
    final soupData = _maybeEncrypt(jsonEncode(entry));

    final id = await db.insert(tableName, {
      'soup': soupData,
      'created': now,
      'lastModified': now,
    });

    entry[soupEntryId] = id;
    entry[soupLastModifiedDate] = now;

    await db.update(
      tableName,
      {'soup': _maybeEncrypt(jsonEncode(entry))},
      where: 'id = ?',
      whereArgs: [id],
    );

    return id;
  }

  /// Retrieves an entry by its soup entry ID.
  Future<Map<String, dynamic>?> retrieve(
      String soupName, int entryId) async {
    final db = _ensureDb();
    final tableName = await _getTableName(soupName);
    if (tableName == null) return null;

    final results = await db.query(
      tableName,
      where: 'id = ?',
      whereArgs: [entryId],
    );

    if (results.isEmpty) return null;
    return _parseSoupEntry(results.first);
  }

  /// Updates an existing entry.
  Future<Map<String, dynamic>> update(
    String soupName,
    Map<String, dynamic> entry,
    int entryId,
  ) async {
    final db = _ensureDb();
    final tableName = await _getTableName(soupName);
    if (tableName == null) {
      throw SmartStoreException('Soup $soupName does not exist');
    }

    final now = DateTime.now().toIso8601String();
    entry[soupEntryId] = entryId;
    entry[soupLastModifiedDate] = now;

    await db.update(
      tableName,
      {
        'soup': _maybeEncrypt(jsonEncode(entry)),
        'lastModified': now,
      },
      where: 'id = ?',
      whereArgs: [entryId],
    );

    return entry;
  }

  /// Inserts or updates an entry based on an external ID path.
  Future<Map<String, dynamic>> upsert(
    String soupName,
    Map<String, dynamic> entry, {
    String externalIdPath = soupEntryId,
  }) async {
    if (externalIdPath == soupEntryId && entry.containsKey(soupEntryId)) {
      final existingId = entry[soupEntryId] as int;
      return update(soupName, entry, existingId);
    }

    if (entry.containsKey(externalIdPath)) {
      final matchKey = entry[externalIdPath].toString();
      final existingId =
          await lookupSoupEntryId(soupName, externalIdPath, matchKey);
      if (existingId != null) {
        return update(soupName, entry, existingId);
      }
    }

    final id = await create(soupName, entry);
    entry[soupEntryId] = id;
    return entry;
  }

  /// Deletes an entry by ID.
  Future<void> delete(String soupName, int entryId) async {
    final db = _ensureDb();
    final tableName = await _getTableName(soupName);
    if (tableName == null) return;

    await db.delete(tableName, where: 'id = ?', whereArgs: [entryId]);
  }

  /// Deletes multiple entries by IDs.
  Future<void> deleteMultiple(String soupName, List<int> entryIds) async {
    final db = _ensureDb();
    final tableName = await _getTableName(soupName);
    if (tableName == null) return;

    final placeholders = entryIds.map((_) => '?').join(',');
    await db.delete(
      tableName,
      where: 'id IN ($placeholders)',
      whereArgs: entryIds,
    );
  }

  /// Deletes all entries in a soup.
  Future<void> deleteAll(String soupName) async {
    final db = _ensureDb();
    final tableName = await _getTableName(soupName);
    if (tableName == null) return;

    await db.delete(tableName);
  }

  // ===== Query Operations =====

  /// Executes a query and returns paginated results.
  Future<List<Map<String, dynamic>>> query(
      QuerySpec spec, int pageIndex) async {
    final db = _ensureDb();
    final sql = _convertSmartSql(spec.smartSql);
    final offset = pageIndex * spec.pageSize;

    final results = await db.rawQuery(
      '$sql LIMIT ? OFFSET ?',
      [...spec.args, spec.pageSize, offset],
    );

    return results.map((row) {
      if (row.containsKey('soup')) {
        return _parseSoupEntry(row);
      }
      return Map<String, dynamic>.from(row);
    }).toList();
  }

  /// Returns the count of records matching a query.
  Future<int> countQuery(QuerySpec spec) async {
    final db = _ensureDb();
    final sql = _convertSmartSql(spec.countSmartSql);
    final results = await db.rawQuery(sql, spec.args);

    if (results.isNotEmpty) {
      final firstValue = results.first.values.first;
      return firstValue is int ? firstValue : 0;
    }
    return 0;
  }

  /// Looks up a soup entry ID by an indexed field value.
  Future<int?> lookupSoupEntryId(
      String soupName, String fieldPath, String matchKey) async {
    final spec = QuerySpec.buildExactQuerySpec(
      soupName,
      fieldPath,
      matchKey,
      pageSize: 1,
    );
    final results = await query(spec, 0);
    if (results.isEmpty) return null;
    return results.first[soupEntryId] as int?;
  }

  /// Extracts a value from a JSON object using a dot-separated path.
  static dynamic project(Map<String, dynamic> json, String jsonPath) {
    final parts = jsonPath.split('.');
    dynamic current = json;
    for (final part in parts) {
      if (current is Map<String, dynamic>) {
        current = current[part];
      } else {
        return null;
      }
    }
    return current;
  }

  // ===== Internal Methods =====

  Future<void> _openDatabase() async {
    final dbPath = await getDatabasesPath();
    final fullPath = path_lib.join(dbPath, '$_dbName.db');

    _database = await openDatabase(
      fullPath,
      version: SchemaManager.currentVersion,
      password: _dbPassword,
      onCreate: SchemaManager.onCreate,
      onUpgrade: SchemaManager.onUpgrade,
    );
  }

  Database _ensureDb() {
    if (_database == null || !_database!.isOpen) {
      throw SmartStoreException('Database is not open');
    }
    return _database!;
  }

  String _soupTableName(String soupName) {
    return 'TABLE_${soupName.hashCode.abs()}';
  }

  Future<String?> _getTableName(String soupName) async {
    final db = _ensureDb();
    final results = await db.query(
      _soupAttrTable,
      columns: ['tableName'],
      where: 'soupName = ?',
      whereArgs: [soupName],
    );
    if (results.isEmpty) return null;
    return results.first['tableName'] as String;
  }

  Map<String, dynamic> _parseSoupEntry(Map<String, dynamic> row) {
    final soupData = row['soup'] as String?;
    if (soupData == null) return {'id': row['id']};

    final decrypted = _maybeDecrypt(soupData);
    final parsed = jsonDecode(decrypted) as Map<String, dynamic>;
    if (!parsed.containsKey(soupEntryId)) {
      parsed[soupEntryId] = row['id'];
    }
    return parsed;
  }

  String _maybeEncrypt(String data) {
    if (_encryptionKey != null) {
      return Encryptor.encryptString(data, _encryptionKey!);
    }
    return data;
  }

  String _maybeDecrypt(String data) {
    if (_encryptionKey == null) return data;
    // Fail explicitly on decryption errors rather than falling back to plaintext
    return Encryptor.decryptString(data, _encryptionKey!);
  }

  /// Converts SmartSQL to actual SQL.
  /// SmartSQL uses {soupName:path} syntax.
  String _convertSmartSql(String smartSql) {
    return smartSql.replaceAllMapped(
      RegExp(r'\{(\w+):(\w+)\}'),
      (match) {
        final soup = match.group(1)!;
        final field = match.group(2)!;

        // Validate both soup name and field path to prevent SQL injection
        SmartSqlValidator.validateSoupName(soup);
        SmartSqlValidator.validateFieldPath(field);

        if (field == '_soup') return 'soup';
        if (field == soupEntryId) return 'id';
        if (field == soupLastModifiedDate) return 'lastModified';
        return "json_extract(soup, '\$.$field')";
      },
    );
  }

  Future<List<Map<String, dynamic>>> _getAllSoupData(String soupName) async {
    final spec = QuerySpec.buildAllQuerySpec(soupName, pageSize: 100000);
    return query(spec, 0);
  }

  /// Closes the database.
  Future<void> close() async {
    await _database?.close();
    _instances.remove(_dbName);
  }

  /// Resets all instances.
  static Future<void> resetAll() async {
    for (final store in _instances.values) {
      await store._database?.close();
    }
    _instances.clear();
  }
}

/// Exception thrown by SmartStore operations.
class SmartStoreException implements Exception {
  final String message;
  SmartStoreException(this.message);

  @override
  String toString() => 'SmartStoreException: $message';
}
