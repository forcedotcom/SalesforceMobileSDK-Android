import '../rest/rest_client.dart';

/// Represents a single authenticated Salesforce user account.
///
/// Stores all credential and profile information needed to
/// make authenticated API calls and manage multi-user scenarios.
class UserAccount {
  /// The current access token.
  final String authToken;

  /// The refresh token for obtaining new access tokens.
  final String refreshToken;

  /// The Salesforce login server URL.
  final Uri loginServer;

  /// The user's instance URL.
  final Uri instanceUrl;

  /// The identity service URL.
  final Uri? idUrl;

  /// The Salesforce user ID.
  final String userId;

  /// The Salesforce org ID.
  final String orgId;

  /// The user's username.
  final String? username;

  /// The account display name.
  final String? accountName;

  /// The community ID (if applicable).
  final String? communityId;

  /// The community URL (if applicable).
  final String? communityUrl;

  /// User's first name.
  final String? firstName;

  /// User's last name.
  final String? lastName;

  /// User's display name.
  final String? displayName;

  /// User's email address.
  final String? email;

  /// URL to the user's profile photo.
  final String? photoUrl;

  /// URL to the user's thumbnail photo.
  final String? thumbnailUrl;

  /// Lightning domain.
  final String? lightningDomain;

  /// Lightning SID.
  final String? lightningSid;

  /// Visualforce domain.
  final String? vfDomain;

  /// Visualforce SID.
  final String? vfSid;

  /// Content domain.
  final String? contentDomain;

  /// Content SID.
  final String? contentSid;

  /// CSRF token.
  final String? csrfToken;

  /// Additional OAuth values.
  final Map<String, String> additionalOauthValues;

  UserAccount({
    required this.authToken,
    required this.refreshToken,
    required this.loginServer,
    required this.instanceUrl,
    this.idUrl,
    required this.userId,
    required this.orgId,
    this.username,
    this.accountName,
    this.communityId,
    this.communityUrl,
    this.firstName,
    this.lastName,
    this.displayName,
    this.email,
    this.photoUrl,
    this.thumbnailUrl,
    this.lightningDomain,
    this.lightningSid,
    this.vfDomain,
    this.vfSid,
    this.contentDomain,
    this.contentSid,
    this.csrfToken,
    this.additionalOauthValues = const {},
  });

  /// Builds a unique identifier for this user account.
  String get uniqueId => '${orgId}_$userId';

  /// Builds a filename-safe suffix for per-user files.
  String get userLevelFilenameSuffix => '${orgId}_${userId}_$communityId';

  /// Builds a filename-safe suffix for per-org files.
  String get orgLevelFilenameSuffix => orgId;

  /// Converts to ClientInfo for use with RestClient.
  ClientInfo toClientInfo() {
    return ClientInfo(
      instanceUrl: instanceUrl,
      loginUrl: loginServer,
      identityUrl: idUrl,
      accountName: accountName,
      username: username,
      userId: userId,
      orgId: orgId,
      communityId: communityId,
      communityUrl: communityUrl,
      firstName: firstName,
      lastName: lastName,
      displayName: displayName,
      email: email,
      photoUrl: photoUrl,
      thumbnailUrl: thumbnailUrl,
      lightningDomain: lightningDomain,
      lightningSid: lightningSid,
      vfDomain: vfDomain,
      vfSid: vfSid,
      contentDomain: contentDomain,
      contentSid: contentSid,
      csrfToken: csrfToken,
      additionalOauthValues: additionalOauthValues,
    );
  }

  /// Creates a copy with updated fields.
  UserAccount copyWith({
    String? authToken,
    String? refreshToken,
    Uri? loginServer,
    Uri? instanceUrl,
    Uri? idUrl,
    String? userId,
    String? orgId,
    String? username,
    String? accountName,
    String? communityId,
    String? communityUrl,
    String? firstName,
    String? lastName,
    String? displayName,
    String? email,
    String? photoUrl,
    String? thumbnailUrl,
    String? lightningDomain,
    String? lightningSid,
    String? vfDomain,
    String? vfSid,
    String? contentDomain,
    String? contentSid,
    String? csrfToken,
    Map<String, String>? additionalOauthValues,
  }) {
    return UserAccount(
      authToken: authToken ?? this.authToken,
      refreshToken: refreshToken ?? this.refreshToken,
      loginServer: loginServer ?? this.loginServer,
      instanceUrl: instanceUrl ?? this.instanceUrl,
      idUrl: idUrl ?? this.idUrl,
      userId: userId ?? this.userId,
      orgId: orgId ?? this.orgId,
      username: username ?? this.username,
      accountName: accountName ?? this.accountName,
      communityId: communityId ?? this.communityId,
      communityUrl: communityUrl ?? this.communityUrl,
      firstName: firstName ?? this.firstName,
      lastName: lastName ?? this.lastName,
      displayName: displayName ?? this.displayName,
      email: email ?? this.email,
      photoUrl: photoUrl ?? this.photoUrl,
      thumbnailUrl: thumbnailUrl ?? this.thumbnailUrl,
      lightningDomain: lightningDomain ?? this.lightningDomain,
      lightningSid: lightningSid ?? this.lightningSid,
      vfDomain: vfDomain ?? this.vfDomain,
      vfSid: vfSid ?? this.vfSid,
      contentDomain: contentDomain ?? this.contentDomain,
      contentSid: contentSid ?? this.contentSid,
      csrfToken: csrfToken ?? this.csrfToken,
      additionalOauthValues:
          additionalOauthValues ?? this.additionalOauthValues,
    );
  }

  Map<String, dynamic> toJson() => {
        'authToken': authToken,
        'refreshToken': refreshToken,
        'loginServer': loginServer.toString(),
        'instanceUrl': instanceUrl.toString(),
        'idUrl': idUrl?.toString(),
        'userId': userId,
        'orgId': orgId,
        'username': username,
        'accountName': accountName,
        'communityId': communityId,
        'communityUrl': communityUrl,
        'firstName': firstName,
        'lastName': lastName,
        'displayName': displayName,
        'email': email,
        'photoUrl': photoUrl,
        'thumbnailUrl': thumbnailUrl,
        'lightningDomain': lightningDomain,
        'lightningSid': lightningSid,
        'vfDomain': vfDomain,
        'vfSid': vfSid,
        'contentDomain': contentDomain,
        'contentSid': contentSid,
        'csrfToken': csrfToken,
        'additionalOauthValues': additionalOauthValues,
      };

  factory UserAccount.fromJson(Map<String, dynamic> json) {
    return UserAccount(
      authToken: json['authToken'],
      refreshToken: json['refreshToken'],
      loginServer: Uri.parse(json['loginServer']),
      instanceUrl: Uri.parse(json['instanceUrl']),
      idUrl: json['idUrl'] != null ? Uri.parse(json['idUrl']) : null,
      userId: json['userId'],
      orgId: json['orgId'],
      username: json['username'],
      accountName: json['accountName'],
      communityId: json['communityId'],
      communityUrl: json['communityUrl'],
      firstName: json['firstName'],
      lastName: json['lastName'],
      displayName: json['displayName'],
      email: json['email'],
      photoUrl: json['photoUrl'],
      thumbnailUrl: json['thumbnailUrl'],
      lightningDomain: json['lightningDomain'],
      lightningSid: json['lightningSid'],
      vfDomain: json['vfDomain'],
      vfSid: json['vfSid'],
      contentDomain: json['contentDomain'],
      contentSid: json['contentSid'],
      csrfToken: json['csrfToken'],
      additionalOauthValues: json['additionalOauthValues'] != null
          ? Map<String, String>.from(json['additionalOauthValues'])
          : {},
    );
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is UserAccount && userId == other.userId && orgId == other.orgId;

  @override
  int get hashCode => userId.hashCode ^ orgId.hashCode;

  @override
  String toString() => 'UserAccount(userId=$userId, orgId=$orgId, username=$username)';
}
