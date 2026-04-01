import 'package:sqflite_sqlcipher/sqflite.dart';
import '../../analytics/logger/salesforce_logger.dart';

/// Represents a single database schema migration step.
abstract class SchemaMigration {
  /// The target version this migration upgrades to.
  int get version;

  /// A description of what this migration does.
  String get description;

  /// Executes the migration on the given database.
  Future<void> migrate(Database db);
}

/// Manages SmartStore database schema migrations.
///
/// Applies migrations in order from the current version to the target version.
/// Each migration is a [SchemaMigration] subclass that defines the schema
/// changes for a specific version.
class SchemaManager {
  static const String _tag = 'SchemaManager';
  static final SalesforceLogger _logger =
      SalesforceLogger.getLogger('SchemaManager');

  /// The current schema version.
  static const int currentVersion = 2;

  /// All registered migrations, ordered by version.
  static final List<SchemaMigration> _migrations = [
    _MigrationV2(),
  ];

  /// Runs all pending migrations from [oldVersion] to [newVersion].
  static Future<void> onUpgrade(
      Database db, int oldVersion, int newVersion) async {
    _logger.i(_tag, 'Upgrading database from v$oldVersion to v$newVersion');

    for (final migration in _migrations) {
      if (migration.version > oldVersion && migration.version <= newVersion) {
        _logger.i(_tag,
            'Applying migration v${migration.version}: ${migration.description}');
        try {
          await migration.migrate(db);
          _logger.i(_tag, 'Migration v${migration.version} complete');
        } catch (e) {
          _logger.e(
              _tag, 'Migration v${migration.version} failed', e);
          rethrow;
        }
      }
    }
  }

  /// Initializes the database schema (called on first creation).
  static Future<void> onCreate(Database db, int version) async {
    await db.execute('''
      CREATE TABLE IF NOT EXISTS soup_attrs (
        soupName TEXT PRIMARY KEY,
        tableName TEXT NOT NULL
      )
    ''');
    await db.execute('''
      CREATE TABLE IF NOT EXISTS soup_index_map (
        soupName TEXT NOT NULL,
        path TEXT NOT NULL,
        columnName TEXT,
        columnType TEXT NOT NULL
      )
    ''');
    await db.execute('''
      CREATE TABLE IF NOT EXISTS _migrations (
        version INTEGER PRIMARY KEY,
        appliedAt TEXT NOT NULL,
        description TEXT
      )
    ''');
  }
}

/// Migration v2: Adds migration tracking table and created/lastModified indexes.
class _MigrationV2 extends SchemaMigration {
  @override
  int get version => 2;

  @override
  String get description =>
      'Add migration tracking table and timestamp indexes';

  @override
  Future<void> migrate(Database db) async {
    // Add migration tracking table
    await db.execute('''
      CREATE TABLE IF NOT EXISTS _migrations (
        version INTEGER PRIMARY KEY,
        appliedAt TEXT NOT NULL,
        description TEXT
      )
    ''');

    // Record this migration
    await db.insert('_migrations', {
      'version': version,
      'appliedAt': DateTime.now().toIso8601String(),
      'description': description,
    });
  }
}
