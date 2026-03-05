/// Query types supported by SmartStore.
enum QueryType {
  exact,
  range,
  like,
  match,
  smart,
}

/// Sort order for query results.
enum SortOrder {
  ascending,
  descending,
}

/// Specification for querying a SmartStore soup.
///
/// Supports multiple query types: exact match, range, LIKE pattern,
/// full-text match, and raw SmartSQL queries. All queries support
/// pagination via pageSize.
class QuerySpec {
  /// The type of query.
  final QueryType queryType;

  /// The soup to query.
  final String soupName;

  /// Fields to return (null means all).
  final List<String>? selectPaths;

  /// The path to query against.
  final String? path;

  /// The path to order results by.
  final String? orderPath;

  /// Sort order.
  final SortOrder order;

  /// Number of records per page.
  final int pageSize;

  // Query-specific parameters
  final String? _exactMatchKey;
  final String? _beginKey;
  final String? _endKey;
  final String? _likeKey;
  final String? _matchKey;
  final String? _smartSql;
  final String? _countSmartSql;

  QuerySpec._({
    required this.queryType,
    required this.soupName,
    this.selectPaths,
    this.path,
    this.orderPath,
    this.order = SortOrder.ascending,
    this.pageSize = 10,
    String? exactMatchKey,
    String? beginKey,
    String? endKey,
    String? likeKey,
    String? matchKey,
    String? smartSql,
    String? countSmartSql,
  })  : _exactMatchKey = exactMatchKey,
        _beginKey = beginKey,
        _endKey = endKey,
        _likeKey = likeKey,
        _matchKey = matchKey,
        _smartSql = smartSql,
        _countSmartSql = countSmartSql;

  /// The generated SmartSQL query.
  String get smartSql => _smartSql ?? _buildSmartSql();

  /// The generated count SmartSQL query.
  String get countSmartSql => _countSmartSql ?? _buildCountSmartSql();

  /// The query arguments for parameterized execution.
  List<String> get args {
    switch (queryType) {
      case QueryType.exact:
        return _exactMatchKey != null ? [_exactMatchKey!] : [];
      case QueryType.range:
        final result = <String>[];
        if (_beginKey != null) result.add(_beginKey!);
        if (_endKey != null) result.add(_endKey!);
        return result;
      case QueryType.like:
        return _likeKey != null ? [_likeKey!] : [];
      case QueryType.match:
        return _matchKey != null ? [_matchKey!] : [];
      case QueryType.smart:
        return [];
    }
  }

  // ===== Builder Methods =====

  /// Builds a query that returns all records from a soup.
  static QuerySpec buildAllQuerySpec(
    String soupName, {
    String? orderPath,
    SortOrder order = SortOrder.ascending,
    int pageSize = 10,
    List<String>? selectPaths,
  }) {
    final selectClause = _buildSelectClause(soupName, selectPaths);
    final orderClause = orderPath != null
        ? ' ORDER BY {$soupName:$orderPath} ${order == SortOrder.ascending ? 'ASC' : 'DESC'}'
        : '';
    return QuerySpec._(
      queryType: QueryType.smart,
      soupName: soupName,
      selectPaths: selectPaths,
      orderPath: orderPath,
      order: order,
      pageSize: pageSize,
      smartSql: 'SELECT $selectClause FROM {$soupName}$orderClause',
      countSmartSql: 'SELECT count(*) FROM {$soupName}',
    );
  }

  /// Builds an exact match query.
  static QuerySpec buildExactQuerySpec(
    String soupName,
    String path,
    String exactMatchKey, {
    String? orderPath,
    SortOrder order = SortOrder.ascending,
    int pageSize = 10,
    List<String>? selectPaths,
  }) {
    final selectClause = _buildSelectClause(soupName, selectPaths);
    final effectiveOrderPath = orderPath ?? path;
    final orderDir = order == SortOrder.ascending ? 'ASC' : 'DESC';
    return QuerySpec._(
      queryType: QueryType.exact,
      soupName: soupName,
      selectPaths: selectPaths,
      path: path,
      orderPath: effectiveOrderPath,
      order: order,
      pageSize: pageSize,
      exactMatchKey: exactMatchKey,
      smartSql:
          'SELECT $selectClause FROM {$soupName} WHERE {$soupName:$path} = ? ORDER BY {$soupName:$effectiveOrderPath} $orderDir',
      countSmartSql:
          'SELECT count(*) FROM {$soupName} WHERE {$soupName:$path} = ?',
    );
  }

  /// Builds a range query.
  static QuerySpec buildRangeQuerySpec(
    String soupName,
    String path, {
    String? beginKey,
    String? endKey,
    String? orderPath,
    SortOrder order = SortOrder.ascending,
    int pageSize = 10,
    List<String>? selectPaths,
  }) {
    final selectClause = _buildSelectClause(soupName, selectPaths);
    final effectiveOrderPath = orderPath ?? path;
    final orderDir = order == SortOrder.ascending ? 'ASC' : 'DESC';

    final conditions = <String>[];
    if (beginKey != null) {
      conditions.add('{$soupName:$path} >= ?');
    }
    if (endKey != null) {
      conditions.add('{$soupName:$path} <= ?');
    }
    final whereClause =
        conditions.isNotEmpty ? ' WHERE ${conditions.join(' AND ')}' : '';

    return QuerySpec._(
      queryType: QueryType.range,
      soupName: soupName,
      selectPaths: selectPaths,
      path: path,
      orderPath: effectiveOrderPath,
      order: order,
      pageSize: pageSize,
      beginKey: beginKey,
      endKey: endKey,
      smartSql:
          'SELECT $selectClause FROM {$soupName}$whereClause ORDER BY {$soupName:$effectiveOrderPath} $orderDir',
      countSmartSql:
          'SELECT count(*) FROM {$soupName}$whereClause',
    );
  }

  /// Builds a LIKE pattern query.
  static QuerySpec buildLikeQuerySpec(
    String soupName,
    String path,
    String likeKey, {
    String? orderPath,
    SortOrder order = SortOrder.ascending,
    int pageSize = 10,
    List<String>? selectPaths,
  }) {
    final selectClause = _buildSelectClause(soupName, selectPaths);
    final effectiveOrderPath = orderPath ?? path;
    final orderDir = order == SortOrder.ascending ? 'ASC' : 'DESC';
    return QuerySpec._(
      queryType: QueryType.like,
      soupName: soupName,
      selectPaths: selectPaths,
      path: path,
      orderPath: effectiveOrderPath,
      order: order,
      pageSize: pageSize,
      likeKey: likeKey,
      smartSql:
          'SELECT $selectClause FROM {$soupName} WHERE {$soupName:$path} LIKE ? ORDER BY {$soupName:$effectiveOrderPath} $orderDir',
      countSmartSql:
          'SELECT count(*) FROM {$soupName} WHERE {$soupName:$path} LIKE ?',
    );
  }

  /// Builds a full-text search match query.
  static QuerySpec buildMatchQuerySpec(
    String soupName,
    String path,
    String matchKey, {
    String? orderPath,
    SortOrder order = SortOrder.ascending,
    int pageSize = 10,
    List<String>? selectPaths,
  }) {
    final selectClause = _buildSelectClause(soupName, selectPaths);
    final effectiveOrderPath = orderPath ?? path;
    final orderDir = order == SortOrder.ascending ? 'ASC' : 'DESC';
    return QuerySpec._(
      queryType: QueryType.match,
      soupName: soupName,
      selectPaths: selectPaths,
      path: path,
      orderPath: effectiveOrderPath,
      order: order,
      pageSize: pageSize,
      matchKey: matchKey,
      smartSql:
          'SELECT $selectClause FROM {$soupName} WHERE {$soupName:$path} MATCH ? ORDER BY {$soupName:$effectiveOrderPath} $orderDir',
      countSmartSql:
          'SELECT count(*) FROM {$soupName} WHERE {$soupName:$path} MATCH ?',
    );
  }

  /// Builds a raw SmartSQL query.
  static QuerySpec buildSmartQuerySpec(
    String smartSql, {
    int pageSize = 10,
    String soupName = '',
  }) {
    return QuerySpec._(
      queryType: QueryType.smart,
      soupName: soupName,
      pageSize: pageSize,
      smartSql: smartSql,
      countSmartSql:
          'SELECT count(*) FROM (${smartSql.replaceAll(RegExp(r'ORDER BY.*$', caseSensitive: false), '')})',
    );
  }

  static String _buildSelectClause(
      String soupName, List<String>? selectPaths) {
    if (selectPaths == null || selectPaths.isEmpty) {
      return '{$soupName:_soup}';
    }
    return selectPaths.map((p) => '{$soupName:$p}').join(', ');
  }

  String _buildSmartSql() => '';
  String _buildCountSmartSql() => '';

  Map<String, dynamic> toJson() => {
        'queryType': queryType.name,
        'soupName': soupName,
        'selectPaths': selectPaths,
        'path': path,
        'orderPath': orderPath,
        'order': order.name,
        'pageSize': pageSize,
        'smartSql': smartSql,
        'countSmartSql': countSmartSql,
      };

  factory QuerySpec.fromJson(Map<String, dynamic> json) {
    return QuerySpec._(
      queryType: QueryType.values.firstWhere(
        (t) => t.name == json['queryType'],
      ),
      soupName: json['soupName'] ?? '',
      selectPaths: (json['selectPaths'] as List<dynamic>?)?.cast<String>(),
      path: json['path'],
      orderPath: json['orderPath'],
      order: SortOrder.values.firstWhere(
        (o) => o.name == json['order'],
        orElse: () => SortOrder.ascending,
      ),
      pageSize: json['pageSize'] ?? 10,
      smartSql: json['smartSql'],
      countSmartSql: json['countSmartSql'],
    );
  }

  @override
  String toString() =>
      'QuerySpec(type=${queryType.name}, soup=$soupName, sql=$smartSql)';
}
