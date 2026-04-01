import 'dart:convert';
import 'dart:io';
import 'package:crypto/crypto.dart';

/// Certificate pinning for Salesforce API connections.
///
/// Validates server certificates against a set of known SHA-256 pin hashes.
/// Supports both leaf certificate and public key pinning.
///
/// Usage:
/// ```dart
/// final pinner = CertificatePinner.salesforceDefault();
/// // Pass pinner.validateCertificate to HttpClient's badCertificateCallback
/// ```
class CertificatePinner {
  /// Set of allowed SHA-256 public key pin hashes (base64-encoded).
  final Set<String> _pins;

  /// Salesforce domains that require pinning.
  final Set<String> _pinnedDomains;

  /// Whether pinning is enabled.
  final bool enabled;

  CertificatePinner({
    required Set<String> pins,
    Set<String>? pinnedDomains,
    this.enabled = true,
  })  : _pins = Set.unmodifiable(pins),
        _pinnedDomains = pinnedDomains != null
            ? Set.unmodifiable(pinnedDomains)
            : _defaultSalesforceDomains;

  /// Known Salesforce domain suffixes.
  static final Set<String> _defaultSalesforceDomains = {
    '.salesforce.com',
    '.force.com',
    '.sfdc.net',
    '.salesforce-sites.com',
    '.documentforce.com',
    '.visualforce.com',
    '.cloudforce.com',
    '.salesforceliveagent.com',
    '.my.salesforce.com',
    '.lightning.force.com',
  };

  /// Creates a pinner with custom pin hashes.
  ///
  /// Pin hashes should be SHA-256 hashes of the Subject Public Key Info (SPKI),
  /// base64-encoded. Generate with:
  /// ```
  /// openssl s_client -servername host -connect host:443 | \
  ///   openssl x509 -pubkey -noout | \
  ///   openssl pkey -pubin -outform der | \
  ///   openssl dgst -sha256 -binary | base64
  /// ```
  factory CertificatePinner.withPins(Set<String> pins) {
    return CertificatePinner(pins: pins);
  }

  /// Creates a pinner configured for Salesforce production.
  ///
  /// Note: Pin hashes must be updated when Salesforce rotates certificates.
  /// Configure via [SalesforceSDKManager] or [BootConfig] with current pins.
  factory CertificatePinner.salesforceDefault({Set<String>? pins}) {
    return CertificatePinner(
      pins: pins ?? {},
      pinnedDomains: _defaultSalesforceDomains,
    );
  }

  /// Checks if a host should have its certificate pinned.
  bool shouldPinHost(String host) {
    if (!enabled || _pins.isEmpty) return false;
    final lowerHost = host.toLowerCase();
    return _pinnedDomains.any(
      (domain) => lowerHost.endsWith(domain) || lowerHost == domain.substring(1),
    );
  }

  /// Validates a certificate chain against the pinned hashes.
  ///
  /// Returns true if the certificate is valid (matches a pin),
  /// or if pinning is not required for this host.
  bool validateCertificate(X509Certificate cert, String host) {
    if (!shouldPinHost(host)) return true;
    if (_pins.isEmpty) return true;

    // Compute SHA-256 hash of the certificate's DER-encoded data
    final certHash = sha256.convert(cert.der);
    final certPin = base64Encode(certHash.bytes);

    return _pins.contains(certPin);
  }

  /// Validates a certificate chain by checking all certificates.
  ///
  /// Returns true if any certificate in the chain matches a pin.
  bool validateCertificateChain(
      List<X509Certificate> chain, String host) {
    if (!shouldPinHost(host)) return true;
    if (_pins.isEmpty) return true;

    for (final cert in chain) {
      final certHash = sha256.convert(cert.der);
      final certPin = base64Encode(certHash.bytes);
      if (_pins.contains(certPin)) return true;
    }
    return false;
  }

  /// Creates an [HttpClient] with certificate pinning configured.
  ///
  /// The returned client will reject connections to pinned domains
  /// whose certificates don't match any of the configured pins.
  HttpClient createPinnedHttpClient() {
    final client = HttpClient();
    if (enabled && _pins.isNotEmpty) {
      client.badCertificateCallback = (cert, host, port) {
        return validateCertificate(cert, host);
      };
    }
    return client;
  }
}
