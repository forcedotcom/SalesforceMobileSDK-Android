import 'dart:convert';
import 'dart:math';
import 'dart:typed_data';
import 'package:crypto/crypto.dart';
import 'package:encrypt/encrypt.dart' as encrypt;

/// Provides encryption and decryption utilities for the SDK.
///
/// Uses AES-256-CBC for symmetric encryption and SHA-256 for key derivation.
class Encryptor {
  /// Encrypts a string using AES-256-CBC.
  static String encryptString(String data, String key) {
    final keyBytes = _deriveKey(key);
    final iv = _generateIV();
    final encrypter = encrypt.Encryptor(encrypt.AES(
      encrypt.Key(keyBytes),
      mode: encrypt.AESMode.cbc,
    ));
    final encrypted = encrypter.encrypt(data, iv: iv);
    // Prepend IV to ciphertext
    final combined = Uint8List(16 + encrypted.bytes.length);
    combined.setRange(0, 16, iv.bytes);
    combined.setRange(16, combined.length, encrypted.bytes);
    return base64Encode(combined);
  }

  /// Decrypts a string encrypted with [encryptString].
  static String decryptString(String encryptedData, String key) {
    final keyBytes = _deriveKey(key);
    final combined = base64Decode(encryptedData);
    final iv = encrypt.IV(Uint8List.fromList(combined.sublist(0, 16)));
    final ciphertext = combined.sublist(16);
    final encrypter = encrypt.Encryptor(encrypt.AES(
      encrypt.Key(keyBytes),
      mode: encrypt.AESMode.cbc,
    ));
    return encrypter.decrypt(encrypt.Encrypted(ciphertext), iv: iv);
  }

  /// Generates a SHA-256 hash of the input string.
  static String hash(String input) {
    final bytes = utf8.encode(input);
    final digest = sha256.convert(bytes);
    return digest.toString();
  }

  /// Generates a cryptographically secure random key.
  static String generateKey({int length = 32}) {
    final random = Random.secure();
    final bytes = List<int>.generate(length, (_) => random.nextInt(256));
    return base64Url.encode(bytes);
  }

  /// Derives a 32-byte key from a string password using SHA-256.
  static Uint8List _deriveKey(String key) {
    final bytes = utf8.encode(key);
    final digest = sha256.convert(bytes);
    return Uint8List.fromList(digest.bytes);
  }

  /// Generates a random 16-byte initialization vector.
  static encrypt.IV _generateIV() {
    final random = Random.secure();
    final bytes = List<int>.generate(16, (_) => random.nextInt(256));
    return encrypt.IV(Uint8List.fromList(bytes));
  }

  /// Generates a unique ID (can be used as encryption key name).
  static String generateUniqueId() {
    final random = Random.secure();
    final bytes = List<int>.generate(32, (_) => random.nextInt(256));
    return base64Url.encode(bytes).replaceAll('=', '');
  }
}
