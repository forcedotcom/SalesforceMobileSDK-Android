# VAPT Report — Salesforce Flutter SDK

**Assessment Date:** 2026-03-06
**Scope:** Full codebase at `flutter_sdk/lib/src/`
**Methodology:** Static code analysis (white-box), OWASP Mobile Top 10 mapping

---

## Executive Summary

| Severity | Count |
|----------|-------|
| Critical | 1 |
| High | 3 |
| Medium | 8 |
| Low | 5 |
| Info | 4 |
| **Total** | **21** |

---

## P1: Cryptography & Key Management

### VULN-01: Weak Key Derivation Function (Critical)
- **File:** `lib/src/core/security/encryptor.dart:55-58`
- **OWASP:** M5 (Insufficient Cryptography)
- **Finding:** `_deriveKey()` uses a single SHA-256 hash with **no salt and no iterations**. This is not a proper KDF.
  ```dart
  static Uint8List _deriveKey(String key) {
    final bytes = utf8.encode(key);
    final digest = sha256.convert(bytes);  // Single hash, no salt
    return Uint8List.fromList(digest.bytes);
  }
  ```
- **Impact:** Identical passwords always produce identical derived keys. Vulnerable to rainbow table attacks and brute-force. An attacker who obtains ciphertext can pre-compute keys for common passwords instantly.
- **Recommendation:** Use PBKDF2 with ≥100,000 iterations and a unique random salt per encryption, or Argon2id. Store salt alongside ciphertext.

### VULN-02: No PKCS7 Padding Validation on Decrypt (Medium)
- **File:** `lib/src/core/security/encryptor.dart:28-38`
- **Finding:** Decryption catches no padding oracle errors. The `encrypt` package uses PKCS7 by default, but malformed ciphertext could lead to unexpected behavior. The `_maybeDecrypt` in SmartStore silently falls back to plaintext on failure (line 478-481), which could mask data corruption.
- **Impact:** Silent data corruption or fallback to unencrypted data.
- **Recommendation:** Fail loudly on decryption errors rather than falling back to plaintext. Consider adding HMAC authentication (encrypt-then-MAC) to detect tampering.

### VULN-03: No Authenticated Encryption (High)
- **File:** `lib/src/core/security/encryptor.dart`
- **Finding:** Uses AES-CBC without any MAC/HMAC. Ciphertext is not integrity-protected.
- **Impact:** An attacker can modify ciphertext (bit-flipping attack on CBC) without detection, potentially corrupting stored data or manipulating decrypted values.
- **Recommendation:** Use AES-GCM (authenticated encryption) instead of AES-CBC, or add HMAC-SHA256 over (IV || ciphertext) and verify before decrypting.

### VULN-04: Encryption Key in Memory (Low)
- **File:** `lib/src/smartstore/store/key_value_encrypted_file_store.dart:23`
- **Finding:** `_encryptionKey` stored as a `String` field in the instance. During `changeEncryptionKey()`, both old and new keys exist in memory simultaneously, along with all decrypted data.
- **Impact:** Memory dump or debugger could extract encryption keys.
- **Recommendation:** Minimize key lifetime in memory. Use platform keystore APIs where possible.

---

## P2: SQL Injection & Data Store Security

### VULN-05: SQL Injection via SmartSQL Field Interpolation (High)
- **File:** `lib/src/smartstore/store/smart_store.dart:489-502`
- **Finding:** `_convertSmartSql()` uses regex `\{(\w+):(\w+)\}` to replace field references with `json_extract(soup, '$.$field')`. The field name is **not validated or sanitized** — it is directly interpolated into the SQL string.
  ```dart
  return "json_extract(soup, '\$.$field')";
  ```
  While the regex limits to `\w+` (alphanumeric + underscore), this still allows:
  - Injection via `buildSmartQuerySpec()` which accepts **raw SQL** at `query_spec.dart:256-269`
  - The `smartSql` parameter is passed directly to `db.rawQuery()` at `smart_store.dart:352`
- **Impact:** If application code passes user-controlled input to `buildSmartQuerySpec()`, full SQL injection is possible. Even with parameterized builder methods, soup names and paths are interpolated unsanitized into SmartSQL templates.
- **Recommendation:**
  - Validate soup names and field paths against a strict allowlist (alphanumeric, dots, underscores only)
  - Sanitize or escape values interpolated into `json_extract()` paths
  - Consider parameterizing field references where possible

### VULN-06: Unvalidated Soup Names (Medium)
- **File:** `lib/src/smartstore/store/smart_store.dart:73-126`
- **Finding:** `registerSoup()` accepts any string as `soupName`. The table name is derived via `hashCode` (`TABLE_${soupName.hashCode.abs()}`), but the soup name itself is inserted into `soup_attrs` and used in SmartSQL interpolation without validation.
- **Impact:** Hash collisions could cause soup conflicts. Malicious soup names could be crafted for injection via SmartSQL.
- **Recommendation:** Validate soup names (alphanumeric + underscores, max length).

### VULN-07: Encryption Is Optional for SmartStore (Medium)
- **File:** `lib/src/smartstore/store/smart_store.dart:54-65`
- **Finding:** `getInstance()` accepts `encryptionKey` as optional. If omitted, all data is stored as plaintext JSON in SQLite. There is no warning or enforcement.
- **Impact:** Developer may forget to provide encryption key, storing sensitive Salesforce data unencrypted on device.
- **Recommendation:** Log a warning when encryption is not enabled. Consider making encryption mandatory or opt-out with explicit flag.

### VULN-08: SmartStore Decryption Fallback to Plaintext (Medium)
- **File:** `lib/src/smartstore/store/smart_store.dart:476-485`
- **Finding:** `_maybeDecrypt()` catches all exceptions and returns the raw data on failure:
  ```dart
  } catch (_) {
    return data; // Might be unencrypted
  }
  ```
- **Impact:** If decryption fails due to key change, tampering, or corruption, the raw (potentially garbled) data is returned and parsed as JSON. This silently masks security failures.
- **Recommendation:** Fail explicitly on decryption errors. Separate handling for migration vs. corruption.

---

## P3: Authentication & OAuth2

### VULN-09: WebView JavaScript Unrestricted (High)
- **File:** `lib/src/core/auth/auth_helper.dart:158`
- **Finding:** Login WebView uses `JavaScriptMode.unrestricted`:
  ```dart
  ..setJavaScriptMode(JavaScriptMode.unrestricted)
  ```
  No URL allowlisting is enforced — the WebView navigates wherever the OAuth flow goes. JavaScript can execute on any loaded page.
- **Impact:** If a phishing page or XSS payload is loaded within the WebView (e.g., via open redirect on the login server), it has full JS access. Could steal credentials or intercept tokens from the callback URL.
- **Recommendation:**
  - Validate navigation URLs against Salesforce domains
  - Restrict JavaScript if possible or use `ShouldOverrideUrlLoading` for tighter control
  - Consider using Custom Tabs / ASWebAuthenticationSession instead of in-app WebView (more secure per OWASP)

### VULN-10: Tokens Serialized as Plaintext JSON (Medium)
- **File:** `lib/src/core/accounts/user_account.dart:207-233`
- **Finding:** `toJson()` includes `authToken` and `refreshToken` as plaintext:
  ```dart
  'authToken': authToken,
  'refreshToken': refreshToken,
  ```
  These are persisted via `UserAccountManager._saveAccountsToStorage()` → `FlutterSecureStorage.write()`. While flutter_secure_storage uses platform keystore, the tokens exist as plaintext strings in Dart memory during serialization/deserialization.
- **Impact:** Memory inspection, heap dumps, or debug builds could expose tokens.
- **Recommendation:** Encrypt tokens before serialization. Consider storing tokens separately in secure storage rather than in a JSON blob.

### VULN-11: No Token Expiration Tracking (Medium)
- **File:** `lib/src/core/auth/oauth2.dart`, `lib/src/core/rest/rest_client.dart`
- **Finding:** The SDK does not parse or track `expires_in` from the token endpoint response. Token refresh is triggered reactively on HTTP 401, not proactively before expiration.
- **Impact:** Tokens may be sent after expiration, creating unnecessary failed requests. No way to warn users about impending session expiration.
- **Recommendation:** Parse `expires_in`, track expiration time, proactively refresh before expiry.

### VULN-12: Current Account ID in Plaintext SharedPreferences (Low)
- **File:** `lib/src/core/accounts/user_account_manager.dart:178-179`
- **Finding:** The current account's unique ID (`orgId_userId`) is stored in `SharedPreferences` (unencrypted):
  ```dart
  final currentId = prefs.getString(_currentAccountKey);
  ```
- **Impact:** Reveals which Salesforce org/user is active to any app with device access.
- **Recommendation:** Store in secure storage instead of SharedPreferences.

---

## P4: Network Security

### VULN-13: No Certificate Pinning (Medium)
- **File:** `lib/src/core/rest/rest_client.dart`, `lib/src/core/auth/auth_helper.dart:127`
- **Finding:** Both `RestClient` and `LoginManager` use `http.Client()` with no certificate pinning:
  ```dart
  static http.Client _createHttpClient() => http.Client();
  ```
- **Impact:** MITM attacks via compromised CA or ARP poisoning on untrusted networks can intercept OAuth tokens and API data.
- **Recommendation:** Implement certificate pinning for Salesforce domains. Consider using `http_certificate_pinning` or custom `SecurityContext`.

### VULN-14: No HTTP Request Timeouts (Medium)
- **File:** `lib/src/core/rest/rest_client.dart:246-287`
- **Finding:** HTTP requests have no timeout configuration. A slow or unresponsive server will block indefinitely.
- **Impact:** Denial of service — app hangs, resources exhausted.
- **Recommendation:** Set reasonable timeouts (e.g., 30s connect, 60s read).

### VULN-15: getJsonCredentials() Exposes All Tokens (Medium)
- **File:** `lib/src/core/rest/rest_client.dart:327-337`
- **Finding:** `getJsonCredentials()` returns a map with plaintext `accessToken` and `refreshToken`. This method's purpose is passing credentials to WebView/components, but there's no restriction on who calls it.
- **Impact:** Any code with a reference to RestClient can extract all credentials.
- **Recommendation:** Consider limiting access, adding audit logging, or requiring explicit opt-in.

### VULN-16: No HTTPS Enforcement (Low)
- **File:** `lib/src/core/rest/rest_client.dart:64-68`
- **Finding:** `ClientInfo.resolveUrl()` doesn't enforce HTTPS:
  ```dart
  if (path.startsWith('http')) return Uri.parse(path);
  ```
  If an absolute HTTP URL is passed, it will be used as-is.
- **Recommendation:** Reject non-HTTPS URLs or auto-upgrade.

---

## P5: Sensitive Data Exposure & Logging

### VULN-17: No Sensitive Data Filtering in Logger (Medium)
- **File:** `lib/src/analytics/logger/salesforce_logger.dart:63-76`
- **Finding:** Logger outputs messages to both `package:logging` and `dart:developer` without any filtering. Error objects passed to logger could contain tokens, passwords, or PII.
  - `rest_client.dart:303` logs "Access token refreshed successfully" — benign, but error objects in catch blocks could contain token data.
  - `salesforce_sdk_manager.dart:236` logs `user.username` during logout.
- **Impact:** Tokens, credentials, or PII could appear in system logs, crash reports, or developer console.
- **Recommendation:** Add a sanitization filter that redacts known sensitive patterns (Bearer tokens, refresh tokens) before logging.

### VULN-18: Analytics Event IDs in Plaintext SharedPreferences (Low)
- **File:** `lib/src/analytics/store/event_store_manager.dart:120-123`
- **Finding:** Event ID lists stored in `SharedPreferences` (unencrypted). Event content is in secure storage, but the ID list reveals analytics event metadata.
- **Impact:** Low — reveals event timing/volume but not content.
- **Recommendation:** Move event ID tracking to secure storage.

### VULN-19: Unused encryptionKey Parameter (Info)
- **File:** `lib/src/analytics/store/event_store_manager.dart:19`
- **Finding:** Constructor accepts `encryptionKey` but never uses it. Events are stored via `FlutterSecureStorage` (which has its own encryption), making the parameter misleading.
- **Impact:** Developer may believe they're providing additional encryption when they're not.
- **Recommendation:** Remove unused parameter or implement double-encryption.

---

## P6: Input Validation & Injection

### VULN-20: No Validation on LoginServer Custom URLs (Low)
- **File:** `lib/src/core/config/login_server_manager.dart:65-68`
- **Finding:** `addCustomServer()` accepts any `Uri` with no validation:
  ```dart
  Future<void> addCustomServer(String name, Uri url) async {
    final server = LoginServer(name: name, url: url, isCustom: true);
  ```
  HTTP URLs, IP addresses, or arbitrary hosts are accepted.
- **Impact:** Could redirect OAuth flow to a malicious server.
- **Recommendation:** Validate custom server URLs (require HTTPS, validate domain format).

### VULN-21: Path Traversal in REST Requests (Info)
- **File:** `lib/src/core/rest/rest_request.dart`
- **Finding:** Factory methods like `getRequestForRetrieve` interpolate `objectType`, `objectId` directly into URL paths without encoding:
  ```dart
  path: '$servicesData/v$apiVersion/sobjects/$objectType/$objectId?fields=$fields'
  ```
  If `objectType` contains path separators, it could manipulate the URL.
- **Impact:** Low — these are typically developer-provided constants. But in dynamic use, could lead to SSRF.
- **Recommendation:** URI-encode path segments.

### VULN-22: KeyValueEncryptedFileStore Name Validation (Info — Positive Finding)
- **File:** `lib/src/smartstore/store/key_value_encrypted_file_store.dart:52-55`
- **Finding:** Store name validation is properly implemented:
  ```dart
  static bool isValidStoreName(String name) {
    if (name.isEmpty || name.length > maxStoreNameLength) return false;
    return RegExp(r'^[a-zA-Z0-9_\-]+$').hasMatch(name);
  }
  ```
  File keys are URL-encoded via `Uri.encodeComponent()` to prevent path traversal.
- **Impact:** None — this is a positive finding.

---

## P7: Biometric & Device Security

### VULN-23: Lock Policy Stored in Plaintext SharedPreferences (Low)
- **File:** `lib/src/core/security/screen_lock_manager.dart:49-51, 101-104`
- **Finding:** Screen lock enabled/disabled state, timeout values, and biometric opt-in are stored in unencrypted `SharedPreferences`.
- **Impact:** An attacker with device access could disable screen lock or modify timeout to bypass security policy.
- **Recommendation:** Store lock policy in secure storage.

### VULN-24: No Biometric Attestation (Info)
- **File:** `lib/src/core/security/screen_lock_manager.dart:123-138`
- **Finding:** Biometric auth uses `local_auth` with `biometricOnly: true` but no attestation or challenge-response. The result is a simple boolean.
- **Impact:** On rooted/jailbroken devices, the biometric result could be spoofed.
- **Recommendation:** Consider platform-specific attestation if high-security scenarios require it.

---

## Positive Findings

| Area | Detail |
|------|--------|
| PKCE | Correctly implemented with `Random.secure()`, 64-byte verifier, SHA-256 S256 challenge |
| IV Generation | Random 16-byte IV per encryption using `Random.secure()` |
| Algorithm | AES-256-CBC (correct key size) |
| Token Storage Backend | `FlutterSecureStorage` (Android Keystore / iOS Keychain) |
| Parameterized Queries | CRUD operations use `whereArgs` properly |
| Token Refresh Throttling | 30-second minimum between refresh attempts |
| toString() Safety | `UserAccount.toString()` does NOT expose tokens |
| File Store Validation | Store names validated, file keys URL-encoded |

---

## Risk Matrix

| ID | Title | Severity | OWASP Mobile | Exploitability | Impact |
|----|-------|----------|-------------|----------------|--------|
| VULN-01 | Weak Key Derivation | Critical | M5 | High | High |
| VULN-03 | No Authenticated Encryption | High | M5 | Medium | High |
| VULN-05 | SQL Injection via SmartSQL | High | M7 | Medium | High |
| VULN-09 | WebView JS Unrestricted | High | M1 | Medium | High |
| VULN-02 | No Padding Validation | Medium | M5 | Low | Medium |
| VULN-06 | Unvalidated Soup Names | Medium | M7 | Low | Medium |
| VULN-07 | Optional Encryption | Medium | M2 | High | Medium |
| VULN-08 | Decrypt Fallback to Plaintext | Medium | M5 | Low | Medium |
| VULN-10 | Tokens in Plaintext JSON | Medium | M2 | Low | Medium |
| VULN-11 | No Token Expiration | Medium | M3 | Low | Low |
| VULN-13 | No Certificate Pinning | Medium | M3 | Medium | High |
| VULN-14 | No Request Timeouts | Medium | M1 | Medium | Low |
| VULN-15 | getJsonCredentials Exposure | Medium | M2 | Low | Medium |
| VULN-17 | No Log Filtering | Medium | M2 | Low | Medium |
| VULN-04 | Key in Memory | Low | M5 | Low | Low |
| VULN-12 | Account ID in SharedPrefs | Low | M2 | Low | Low |
| VULN-16 | No HTTPS Enforcement | Low | M3 | Low | Medium |
| VULN-18 | Event IDs in SharedPrefs | Low | M2 | Low | Low |
| VULN-20 | No Custom URL Validation | Low | M3 | Low | Medium |
| VULN-23 | Lock Policy in SharedPrefs | Low | M2 | Low | Low |
| VULN-19 | Unused encryptionKey Param | Info | — | — | — |
| VULN-21 | Path Traversal in REST | Info | M7 | Low | Low |
| VULN-22 | Store Name Validation (OK) | Info+ | — | — | — |
| VULN-24 | No Biometric Attestation | Info | M1 | Low | Low |

---

## Recommended Remediation Priority

1. **Immediate (Critical/High):** VULN-01, VULN-03, VULN-05, VULN-09
2. **Short-term (Medium):** VULN-07, VULN-08, VULN-10, VULN-13, VULN-14, VULN-17
3. **Long-term (Low/Info):** VULN-04, VULN-11, VULN-12, VULN-16, VULN-18, VULN-20, VULN-23
