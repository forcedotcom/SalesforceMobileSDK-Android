/// A single priming record.
class PrimingRecord {
  final String id;
  final DateTime? systemModstamp;

  PrimingRecord({required this.id, this.systemModstamp});

  factory PrimingRecord.fromJson(Map<String, dynamic> json) {
    DateTime? modstamp;
    final modstampStr = json['systemModstamp'] as String?;
    if (modstampStr != null) {
      modstamp = DateTime.tryParse(modstampStr);
    }
    return PrimingRecord(id: json['id'], systemModstamp: modstamp);
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'systemModstamp': systemModstamp?.toUtc().toIso8601String(),
      };
}

/// Error from a priming rule evaluation.
class PrimingRuleError {
  final String ruleId;

  PrimingRuleError({required this.ruleId});

  factory PrimingRuleError.fromJson(Map<String, dynamic> json) {
    return PrimingRuleError(ruleId: json['ruleId']);
  }
}

/// Statistics about a priming operation.
class PrimingStats {
  final int ruleCountTotal;
  final int recordCountTotal;
  final int ruleCountServed;
  final int recordCountServed;

  PrimingStats({
    required this.ruleCountTotal,
    required this.recordCountTotal,
    required this.ruleCountServed,
    required this.recordCountServed,
  });

  factory PrimingStats.fromJson(Map<String, dynamic> json) {
    return PrimingStats(
      ruleCountTotal: json['ruleCountTotal'] as int? ?? 0,
      recordCountTotal: json['recordCountTotal'] as int? ?? 0,
      ruleCountServed: json['ruleCountServed'] as int? ?? 0,
      recordCountServed: json['recordCountServed'] as int? ?? 0,
    );
  }
}

/// Response from priming records requests.
///
/// Priming records allow pre-caching data for offline access.
/// Structure: ObjectApiName -> RecordType -> List<PrimingRecord>
///
/// Mirrors Android PrimingRecordsResponse for feature parity.
class PrimingRecordsResponse {
  /// Nested map: objectApiName → recordType → records.
  final Map<String, Map<String, List<PrimingRecord>>> primingRecords;

  /// Token for relay/tracking.
  final String? relayToken;

  /// Errors from rule evaluation.
  final List<PrimingRuleError> ruleErrors;

  /// Statistics about the priming operation.
  final PrimingStats? stats;

  PrimingRecordsResponse({
    required this.primingRecords,
    this.relayToken,
    this.ruleErrors = const [],
    this.stats,
  });

  factory PrimingRecordsResponse.fromJson(Map<String, dynamic> json) {
    final records = <String, Map<String, List<PrimingRecord>>>{};
    final primingJson =
        json['primingRecords'] as Map<String, dynamic>? ?? {};
    for (final objectEntry in primingJson.entries) {
      final recordTypes = <String, List<PrimingRecord>>{};
      final typesMap = objectEntry.value as Map<String, dynamic>? ?? {};
      for (final typeEntry in typesMap.entries) {
        final recordList = (typeEntry.value as List<dynamic>?)
                ?.map((r) =>
                    PrimingRecord.fromJson(r as Map<String, dynamic>))
                .toList() ??
            [];
        recordTypes[typeEntry.key] = recordList;
      }
      records[objectEntry.key] = recordTypes;
    }

    return PrimingRecordsResponse(
      primingRecords: records,
      relayToken: json['relayToken'] as String?,
      ruleErrors: (json['ruleErrors'] as List<dynamic>?)
              ?.map((e) =>
                  PrimingRuleError.fromJson(e as Map<String, dynamic>))
              .toList() ??
          [],
      stats: json['stats'] != null
          ? PrimingStats.fromJson(json['stats'] as Map<String, dynamic>)
          : null,
    );
  }
}
