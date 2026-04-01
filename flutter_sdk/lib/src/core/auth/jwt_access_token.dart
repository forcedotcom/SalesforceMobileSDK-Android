import 'dart:convert';

/// Parsed JWT header fields.
class JwtHeader {
  final String? algorithm;
  final String? type;
  final String? keyId;
  final String? tokenType;
  final String? tenantKey;
  final String? version;

  JwtHeader({
    this.algorithm,
    this.type,
    this.keyId,
    this.tokenType,
    this.tenantKey,
    this.version,
  });

  factory JwtHeader.fromJson(Map<String, dynamic> json) {
    return JwtHeader(
      algorithm: json['alg'],
      type: json['typ'],
      keyId: json['kid'],
      tokenType: json['tty'],
      tenantKey: json['tnk'],
      version: json['ver'],
    );
  }

  Map<String, dynamic> toJson() => {
        'alg': algorithm,
        'typ': type,
        'kid': keyId,
        'tty': tokenType,
        'tnk': tenantKey,
        'ver': version,
      };
}

/// Parsed JWT payload fields.
class JwtPayload {
  final List<String>? audience;
  final int? expirationTime;
  final String? issuer;
  final int? notBeforeTime;
  final String? subject;
  final String? scopes;
  final String? clientId;

  JwtPayload({
    this.audience,
    this.expirationTime,
    this.issuer,
    this.notBeforeTime,
    this.subject,
    this.scopes,
    this.clientId,
  });

  factory JwtPayload.fromJson(Map<String, dynamic> json) {
    final aud = json['aud'];
    return JwtPayload(
      audience: aud is List
          ? aud.cast<String>()
          : aud is String
              ? [aud]
              : null,
      expirationTime: json['exp'] is int ? json['exp'] : null,
      issuer: json['iss'],
      notBeforeTime: json['nbf'] is int ? json['nbf'] : null,
      subject: json['sub'],
      scopes: json['scp'],
      clientId: json['client_id'],
    );
  }

  Map<String, dynamic> toJson() => {
        'aud': audience,
        'exp': expirationTime,
        'iss': issuer,
        'nbf': notBeforeTime,
        'sub': subject,
        'scp': scopes,
        'client_id': clientId,
      };
}

/// Decodes and parses a JWT access token into its header and payload.
///
/// Supports standard 3-part JWT format: header.payload.signature.
/// Does not verify the signature — use for inspecting token claims only.
class JwtAccessToken {
  /// The original raw JWT string.
  final String rawJwt;

  /// Parsed header fields.
  final JwtHeader header;

  /// Parsed payload fields.
  final JwtPayload payload;

  JwtAccessToken._({
    required this.rawJwt,
    required this.header,
    required this.payload,
  });

  /// Parses a JWT string into header and payload.
  ///
  /// Throws [FormatException] if the JWT is malformed.
  factory JwtAccessToken(String jwt) {
    final parts = jwt.split('.');
    if (parts.length != 3) {
      throw FormatException(
          'Invalid JWT format: expected 3 parts, got ${parts.length}');
    }

    final headerJson = _decodeBase64Url(parts[0]);
    final payloadJson = _decodeBase64Url(parts[1]);

    return JwtAccessToken._(
      rawJwt: jwt,
      header: JwtHeader.fromJson(headerJson),
      payload: JwtPayload.fromJson(payloadJson),
    );
  }

  /// Returns the expiration time as a [DateTime], or null if not set.
  DateTime? get expirationDate {
    if (payload.expirationTime == null) return null;
    return DateTime.fromMillisecondsSinceEpoch(
        payload.expirationTime! * 1000);
  }

  /// Whether the token is expired.
  bool get isExpired {
    final exp = expirationDate;
    if (exp == null) return false;
    return DateTime.now().isAfter(exp);
  }

  /// Decodes a Base64 URL-safe encoded JSON segment.
  static Map<String, dynamic> _decodeBase64Url(String segment) {
    // Add padding if needed
    var normalized = segment.replaceAll('-', '+').replaceAll('_', '/');
    switch (normalized.length % 4) {
      case 2:
        normalized += '==';
        break;
      case 3:
        normalized += '=';
        break;
    }
    final bytes = base64Decode(normalized);
    return jsonDecode(utf8.decode(bytes)) as Map<String, dynamic>;
  }

  @override
  String toString() =>
      'JwtAccessToken(sub=${payload.subject}, exp=$expirationDate)';
}
