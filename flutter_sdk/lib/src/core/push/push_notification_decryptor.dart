import 'dart:convert';
import 'dart:typed_data';
import 'package:pointycastle/export.dart' as pc;

/// Decrypts incoming encrypted push notifications using RSA and AES encryption.
///
/// Push notifications from Salesforce can be encrypted. The payload contains:
/// - "encrypted": flag indicating encryption
/// - "secret": RSA-encrypted symmetric key
/// - "content": AES-encrypted notification content
///
/// Mirrors Android PushNotificationDecryptor for feature parity.
class PushNotificationDecryptor {
  static PushNotificationDecryptor? _instance;

  /// Gets the singleton instance.
  static PushNotificationDecryptor get instance {
    _instance ??= PushNotificationDecryptor._();
    return _instance!;
  }

  PushNotificationDecryptor._();

  /// Processes a push notification payload, decrypting if needed.
  ///
  /// Returns the decrypted data map, or the original if not encrypted.
  Map<String, String> processNotificationPayload(
    Map<String, String> data, {
    required pc.RSAPrivateKey privateKey,
  }) {
    final encrypted = data['encrypted'];
    if (encrypted != 'true') return data;

    final encryptedSecret = data['secret'];
    if (encryptedSecret == null) return data;

    final result = Map<String, String>.from(data);
    result.remove('secret');

    return _decryptPayload(encryptedSecret, result, privateKey: privateKey);
  }

  /// Decrypts the payload using RSA to unwrap the AES key.
  Map<String, String> _decryptPayload(
    String encryptedSecretKey,
    Map<String, String> data, {
    required pc.RSAPrivateKey privateKey,
  }) {
    // Decrypt symmetric key using RSA
    final encryptedKeyBytes = base64Decode(encryptedSecretKey);
    final rsaCipher = pc.OAEPEncoding(pc.RSAEngine())
      ..init(false, pc.PrivateKeyParameter<pc.RSAPrivateKey>(privateKey));

    final symmetricKeyBytes = rsaCipher.process(Uint8List.fromList(encryptedKeyBytes));

    // Extract AES key (first 16 bytes) and IV (next 16 bytes)
    final aesKey = Uint8List.fromList(symmetricKeyBytes.sublist(0, 16));
    final iv = Uint8List.fromList(symmetricKeyBytes.sublist(16, 32));

    // Decrypt content field using AES-CBC
    final encryptedContent = data['content'];
    if (encryptedContent != null) {
      final encryptedBytes = base64Decode(encryptedContent);

      final aesCipher = pc.PaddedBlockCipherImpl(
        pc.PKCS7Padding(),
        pc.CBCBlockCipher(pc.AESEngine()),
      )..init(
          false,
          pc.PaddedBlockCipherParameters(
            pc.ParametersWithIV(pc.KeyParameter(aesKey), iv),
            null,
          ),
        );

      final decryptedBytes = aesCipher.process(Uint8List.fromList(encryptedBytes));
      data['content'] = utf8.decode(decryptedBytes);
    }

    return data;
  }
}

/// Data model for Salesforce actionable push notification content.
///
/// Mirrors Android SalesforceActionableNotificationContent for feature parity.
class SalesforceActionableNotificationContent {
  final SfdcNotification? sfdc;
  final String? sourceJson;

  SalesforceActionableNotificationContent({this.sfdc, this.sourceJson});

  factory SalesforceActionableNotificationContent.fromJson(String json) {
    final decoded = jsonDecode(json) as Map<String, dynamic>;
    return SalesforceActionableNotificationContent(
      sfdc: decoded['sfdc'] != null
          ? SfdcNotification.fromJson(decoded['sfdc'] as Map<String, dynamic>)
          : null,
      sourceJson: json,
    );
  }
}

/// Core notification data from Salesforce.
class SfdcNotification {
  final String? notifType;
  final String? nid;
  final String? oid;
  final int? type;
  final String? alertTitle;
  final String? sid;
  final String? rid;
  final String? targetPageRef;
  final int? badge;
  final String? uid;
  final SfdcNotificationAction? act;
  final String? alertBody;
  final String? alert;
  final String? cid;
  final int? timestamp;

  SfdcNotification({
    this.notifType,
    this.nid,
    this.oid,
    this.type,
    this.alertTitle,
    this.sid,
    this.rid,
    this.targetPageRef,
    this.badge,
    this.uid,
    this.act,
    this.alertBody,
    this.alert,
    this.cid,
    this.timestamp,
  });

  factory SfdcNotification.fromJson(Map<String, dynamic> json) {
    return SfdcNotification(
      notifType: json['notifType'] as String?,
      nid: json['nid'] as String?,
      oid: json['oid'] as String?,
      type: json['type'] as int?,
      alertTitle: json['alertTitle'] as String?,
      sid: json['sid'] as String?,
      rid: json['rid'] as String?,
      targetPageRef: json['targetPageRef'] as String?,
      badge: json['badge'] as int?,
      uid: json['uid'] as String?,
      act: json['act'] != null
          ? SfdcNotificationAction.fromJson(json['act'] as Map<String, dynamic>)
          : null,
      alertBody: json['alertBody'] as String?,
      alert: json['alert'] as String?,
      cid: json['cid'] as String?,
      timestamp: json['timestamp'] as int?,
    );
  }
}

/// Action associated with a notification.
class SfdcNotificationAction {
  final String? group;
  final String? type;
  final String? description;
  final Map<String, dynamic>? properties;

  SfdcNotificationAction({
    this.group,
    this.type,
    this.description,
    this.properties,
  });

  factory SfdcNotificationAction.fromJson(Map<String, dynamic> json) {
    return SfdcNotificationAction(
      group: json['group'] as String?,
      type: json['type'] as String?,
      description: json['description'] as String?,
      properties: json['properties'] as Map<String, dynamic>?,
    );
  }
}
