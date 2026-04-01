import 'dart:convert';
import 'smart_store.dart';
import 'query_spec.dart';

/// Provides debug inspection capabilities for SmartStore.
///
/// Allows listing soups, inspecting indexes, querying data, and
/// viewing store statistics. Intended for development/debugging only.
///
/// Mirrors Android SmartStoreInspectorActivity for feature parity.
class SmartStoreInspector {
  final SmartStore _store;

  SmartStoreInspector(this._store);

  /// Lists all registered soups.
  Future<List<String>> listSoups() async {
    return _store.getAllSoupNames();
  }

  /// Gets the index specifications for a soup.
  Future<List<Map<String, dynamic>>> getSoupIndexes(String soupName) async {
    final specs = await _store.getSoupIndexSpecs(soupName);
    return specs.map((s) => s.toJson()).toList();
  }

  /// Counts the entries in a soup.
  Future<int> countEntries(String soupName) async {
    return _store.countQuery(
        QuerySpec.buildAllQuerySpec(soupName, pageSize: 1));
  }

  /// Queries a soup and returns the results as formatted JSON.
  Future<List<Map<String, dynamic>>> querySoup(
    String soupName, {
    int pageSize = 25,
    int pageIndex = 0,
    String? orderPath,
    SortOrder order = SortOrder.ascending,
  }) async {
    final querySpec = QuerySpec.buildAllQuerySpec(
      soupName,
      orderPath: orderPath,
      order: order,
      pageSize: pageSize,
    );
    final results = await _store.query(querySpec, pageIndex);
    return results;
  }

  /// Runs a SmartSQL query and returns formatted results.
  Future<List<Map<String, dynamic>>> runSmartSql(String smartSql,
      {int pageSize = 25, int pageIndex = 0}) async {
    final querySpec =
        QuerySpec.buildSmartQuerySpec(smartSql, pageSize: pageSize);
    return _store.query(querySpec, pageIndex);
  }

  /// Gets store statistics: soup count, total entries.
  Future<Map<String, dynamic>> getStoreStats() async {
    final soups = await listSoups();
    final soupStats = <String, int>{};
    int totalEntries = 0;
    for (final soup in soups) {
      final count = await countEntries(soup);
      soupStats[soup] = count;
      totalEntries += count;
    }
    return {
      'soupCount': soups.length,
      'totalEntries': totalEntries,
      'soups': soupStats,
    };
  }

  /// Exports a soup's contents as a JSON string.
  Future<String> exportSoup(String soupName) async {
    final results = await querySoup(soupName, pageSize: 10000);
    return const JsonEncoder.withIndent('  ').convert(results);
  }

  /// Clears all entries from a soup (drops and re-registers).
  Future<void> clearSoup(String soupName) async {
    await _store.deleteAll(soupName);
  }
}
