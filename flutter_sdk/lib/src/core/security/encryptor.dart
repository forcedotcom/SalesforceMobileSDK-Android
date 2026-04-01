import 'dart:convert';
import 'dart:math';
import 'dart:typed_data';
import 'package:crypto/crypto.dart';
import 'package:pointycastle/export.dart' as pc;

/// PBKDF2 iteration count for key derivation.
const int _pbkdf2Iterations = 100000;

/// Salt length in bytes.
const int _saltLength = 16;

/// GCM nonce length in bytes (96-bit per NIST recommendation).
const int _gcmNonceLength = 12;

/// GCM authentication tag length in bits.
const int _gcmTagLength = 128;

/// Format version byte for v2 (AES-GCM + PBKDF2).
const int _formatVersion = 2;

/// Provides encryption and decryption utilities for the SDK.
///
/// **Version 2 (current):** AES-256-GCM with PBKDF2-HMAC-SHA256 key derivation.
/// Wire format: `[version(1)][salt(16)][nonce(12)][ciphertext+GCM_tag]`
///
/// **Version 1 (legacy):** AES-256-CBC with bare SHA-256 key derivation.
/// Wire format: `[iv(16)][ciphertext]`
///
/// Decryption supports both formats for backward compatibility.
class Encryptor {
  /// Encrypts a string using AES-256-GCM with PBKDF2-derived key.
  ///
  /// Returns base64-encoded string containing version byte, salt, nonce,
  /// and authenticated ciphertext (with GCM tag appended).
  static String encryptString(String data, String key) {
    final salt = _generateRandomBytes(_saltLength);
    final nonce = _generateRandomBytes(_gcmNonceLength);
    final keyBytes = deriveKeyPbkdf2(key, salt);

    final cipher = pc.GCMBlockCipher(pc.AESEngine())
      ..init(
        true,
        pc.AEADParameters(
          pc.KeyParameter(keyBytes),
          _gcmTagLength,
          nonce,
          Uint8List(0),
        ),
      );

    final plainBytes = Uint8List.fromList(utf8.encode(data));
    final cipherBytes = cipher.process(plainBytes);

    // Wire format: version(1) + salt(16) + nonce(12) + ciphertext+tag
    final combined = Uint8List(1 + _saltLength + _gcmNonceLength + cipherBytes.length);
    combined[0] = _formatVersion;
    combined.setRange(1, 1 + _saltLength, salt);
    combined.setRange(1 + _saltLength, 1 + _saltLength + _gcmNonceLength, nonce);
    combined.setRange(1 + _saltLength + _gcmNonceLength, combined.length, cipherBytes);
    return base64Encode(combined);
  }

  /// Decrypts a string encrypted with [encryptString].
  ///
  /// Supports both v2 (AES-GCM + PBKDF2) and legacy v1 (AES-CBC + SHA-256)
  /// formats for backward compatibility.
  static String decryptString(String encryptedData, String key) {
    final combined = base64Decode(encryptedData);
    if (combined.isEmpty) {
      throw const FormatException('Empty ciphertext');
    }

    if (combined[0] == _formatVersion) {
      return _decryptV2(combined, key);
    } else {
      return _decryptV1(combined, key);
    }
  }

  /// Decrypts v2 format: [version(1)][salt(16)][nonce(12)][ciphertext+tag]
  static String _decryptV2(Uint8List combined, String key) {
    final minLen = 1 + _saltLength + _gcmNonceLength + 1;
    if (combined.length < minLen) {
      throw const FormatException('Ciphertext too short for v2 format');
    }

    final salt = Uint8List.fromList(combined.sublist(1, 1 + _saltLength));
    final nonce = Uint8List.fromList(
        combined.sublist(1 + _saltLength, 1 + _saltLength + _gcmNonceLength));
    final ciphertext = Uint8List.fromList(
        combined.sublist(1 + _saltLength + _gcmNonceLength));

    final keyBytes = deriveKeyPbkdf2(key, salt);

    final cipher = pc.GCMBlockCipher(pc.AESEngine())
      ..init(
        false,
        pc.AEADParameters(
          pc.KeyParameter(keyBytes),
          _gcmTagLength,
          nonce,
          Uint8List(0),
        ),
      );

    final plainBytes = cipher.process(ciphertext);
    return utf8.decode(plainBytes);
  }

  /// Decrypts legacy v1 format: [iv(16)][ciphertext] using AES-CBC + SHA-256.
  static String _decryptV1(Uint8List combined, String key) {
    if (combined.length < 17) {
      throw const FormatException('Ciphertext too short for v1 format');
    }

    final iv = Uint8List.fromList(combined.sublist(0, 16));
    final ciphertext = Uint8List.fromList(combined.sublist(16));
    final keyBytes = _deriveKeyLegacy(key);

    final cipher = pc.PaddedBlockCipherImpl(
      pc.PKCS7Padding(),
      pc.CBCBlockCipher(pc.AESEngine()),
    )..init(
        false,
        pc.PaddedBlockCipherParameters(
          pc.ParametersWithIV(pc.KeyParameter(keyBytes), iv),
          null,
        ),
      );

    final plainBytes = cipher.process(ciphertext);
    return utf8.decode(plainBytes);
  }

  /// Generates a SHA-256 hash of the input string.
  static String hash(String input) {
    final bytes = utf8.encode(input);
    final digest = sha256.convert(bytes);
    return digest.toString();
  }

  /// Generates a cryptographically secure random key.
  static String generateKey({int length = 32}) {
    final bytes = _generateRandomBytes(length);
    return base64Url.encode(bytes);
  }

  /// Derives a 32-byte key using PBKDF2-HMAC-SHA256.
  static Uint8List deriveKeyPbkdf2(String key, Uint8List salt,
      {int iterations = _pbkdf2Iterations}) {
    final derivator = pc.PBKDF2KeyDerivator(pc.HMac(pc.SHA256Digest(), 64))
      ..init(pc.Pbkdf2Parameters(salt, iterations, 32));
    return derivator.process(Uint8List.fromList(utf8.encode(key)));
  }

  /// Legacy key derivation (bare SHA-256) for v1 backward compatibility only.
  static Uint8List _deriveKeyLegacy(String key) {
    final bytes = utf8.encode(key);
    final digest = sha256.convert(bytes);
    return Uint8List.fromList(digest.bytes);
  }

  /// Generates cryptographically secure random bytes.
  static Uint8List _generateRandomBytes(int length) {
    final random = Random.secure();
    return Uint8List.fromList(
        List<int>.generate(length, (_) => random.nextInt(256)));
  }

  /// Generates a unique ID (can be used as encryption key name).
  static String generateUniqueId() {
    final bytes = _generateRandomBytes(32);
    return base64Url.encode(bytes).replaceAll('=', '');
  }

  /// Re-encrypts data from legacy v1 format to v2 format.
  ///
  /// Returns null if the data is already in v2 format or empty.
  static String? migrateToV2(String encryptedData, String key) {
    final combined = base64Decode(encryptedData);
    if (combined.isEmpty || combined[0] == _formatVersion) {
      return null;
    }
    final plaintext = _decryptV1(combined, key);
    return encryptString(plaintext, key);
  }
}
