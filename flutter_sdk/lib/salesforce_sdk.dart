/// Salesforce Mobile SDK for Flutter.
///
/// A comprehensive Flutter SDK providing Salesforce integration with:
/// - OAuth2 authentication (User-Agent and Web Server flows with PKCE)
/// - REST API client with automatic token refresh
/// - SmartStore (local encrypted SQLite storage)
/// - MobileSync (offline data synchronization)
/// - Analytics instrumentation
/// - Security (encryption, biometric auth, screen lock)
/// - Multi-user account management
library salesforce_sdk;

// ===== Analytics =====
export 'src/analytics/logger/salesforce_logger.dart';
export 'src/analytics/manager/analytics_manager.dart';
export 'src/analytics/model/device_app_attributes.dart';
export 'src/analytics/model/instrumentation_event.dart';
export 'src/analytics/store/event_store_manager.dart';
export 'src/analytics/transform/transform.dart';

// ===== Core - Authentication =====
export 'src/core/auth/oauth2.dart';
export 'src/core/auth/auth_helper.dart';

// ===== Core - REST Client =====
export 'src/core/rest/rest_client.dart';
export 'src/core/rest/rest_request.dart';
export 'src/core/rest/rest_response.dart';
export 'src/core/rest/salesforce_error.dart';
export 'src/core/rest/http_client_factory.dart';

// ===== Core - Network =====
export 'src/core/network/connectivity_manager.dart';

// ===== Core - Accounts =====
export 'src/core/accounts/user_account.dart';
export 'src/core/accounts/user_account_manager.dart';

// ===== Core - App & Configuration =====
export 'src/core/app/salesforce_sdk_manager.dart';
export 'src/core/config/boot_config.dart';
export 'src/core/config/login_server_manager.dart';

// ===== Core - Security =====
export 'src/core/security/encryptor.dart';
export 'src/core/security/screen_lock_manager.dart';

// ===== SmartStore =====
export 'src/smartstore/store/smart_store.dart';
export 'src/smartstore/store/index_spec.dart';
export 'src/smartstore/store/query_spec.dart';
export 'src/smartstore/store/key_value_encrypted_file_store.dart';
export 'src/smartstore/store/smart_sql_validator.dart';

// ===== MobileSync =====
export 'src/mobilesync/manager/sync_manager.dart';
export 'src/mobilesync/model/sync_state.dart';
export 'src/mobilesync/target/sync_down_target.dart';
export 'src/mobilesync/target/sync_up_target.dart';
export 'src/mobilesync/manager/sync_scheduler.dart';

// ===== Push Notifications =====
export 'src/core/push/push_notification_manager.dart';

// ===== Analytics - Log Sanitizer =====
export 'src/analytics/logger/log_sanitizer.dart';
