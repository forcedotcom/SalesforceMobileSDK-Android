/// Salesforce Mobile SDK for Flutter.
///
/// A comprehensive Flutter SDK providing Salesforce integration with:
/// - OAuth2 authentication (User-Agent and Web Server flows with PKCE)
/// - Native/headless login and IDP/SP enterprise SSO
/// - REST API client with automatic token refresh
/// - Chatter Files API support
/// - Composite API with typed responses
/// - SmartStore (local encrypted SQLite storage with FTS)
/// - MobileSync (offline data synchronization)
/// - Analytics instrumentation and publishing
/// - Security (encryption, biometric auth, screen lock, cert pinning)
/// - Multi-user account management with account switcher UI
/// - Push notification support with encrypted payload decryption
/// - Einstein Platform (SFAP) API client
/// - Login, screen lock, and developer info UI components
/// - SDK lifecycle management and upgrade support
library salesforce_sdk;

// ===== Analytics =====
export 'src/analytics/logger/salesforce_logger.dart';
export 'src/analytics/logger/log_sanitizer.dart';
export 'src/analytics/logger/file_logger.dart';
export 'src/analytics/logger/log_receiver.dart';
export 'src/analytics/manager/analytics_manager.dart';
export 'src/analytics/model/device_app_attributes.dart';
export 'src/analytics/model/instrumentation_event.dart';
export 'src/analytics/store/event_store_manager.dart';
export 'src/analytics/transform/transform.dart';
export 'src/analytics/publisher/analytics_publisher.dart';

// ===== Core - Authentication =====
export 'src/core/auth/oauth2.dart';
export 'src/core/auth/auth_helper.dart';
export 'src/core/auth/jwt_access_token.dart';
export 'src/core/auth/native_login_manager.dart';
export 'src/core/auth/idp_sp_flow.dart';

// ===== Core - REST Client =====
export 'src/core/rest/rest_client.dart';
export 'src/core/rest/rest_request.dart';
export 'src/core/rest/rest_response.dart';
export 'src/core/rest/salesforce_error.dart';
export 'src/core/rest/http_client_factory.dart';
export 'src/core/rest/batch_response.dart';
export 'src/core/rest/collection_response.dart';
export 'src/core/rest/composite_response.dart';
export 'src/core/rest/priming_records_response.dart';
export 'src/core/rest/notifications_api_client.dart';
export 'src/core/rest/sfap_api_client.dart';
export 'src/core/rest/file_requests.dart';

// ===== Core - Network =====
export 'src/core/network/connectivity_manager.dart';

// ===== Core - Accounts =====
export 'src/core/accounts/user_account.dart';
export 'src/core/accounts/user_account_manager.dart';

// ===== Core - App & Configuration =====
export 'src/core/app/salesforce_sdk_manager.dart';
export 'src/core/app/features.dart';
export 'src/core/app/sdk_upgrade_manager.dart';
export 'src/core/config/boot_config.dart';
export 'src/core/config/login_server_manager.dart';
export 'src/core/config/runtime_config.dart';
export 'src/core/config/admin_prefs_manager.dart';

// ===== Core - Security =====
export 'src/core/security/encryptor.dart';
export 'src/core/security/screen_lock_manager.dart';
export 'src/core/security/certificate_pinner.dart';

// ===== Core - Push Notifications =====
export 'src/core/push/push_notification_manager.dart';
export 'src/core/push/push_notification_decryptor.dart';

// ===== Core - Utilities =====
export 'src/core/util/events_observable.dart';

// ===== Core - Platform =====
export 'src/core/platform/salesforce_activity.dart';

// ===== SmartStore =====
export 'src/smartstore/store/smart_store.dart';
export 'src/smartstore/store/index_spec.dart';
export 'src/smartstore/store/query_spec.dart';
export 'src/smartstore/store/key_value_encrypted_file_store.dart';
export 'src/smartstore/store/smart_sql_validator.dart';
export 'src/smartstore/store/schema_migration.dart';
export 'src/smartstore/store/mem_cached_key_value_store.dart';
export 'src/smartstore/store/smart_store_inspector.dart';
export 'src/smartstore/config/store_config.dart';

// ===== MobileSync =====
export 'src/mobilesync/manager/sync_manager.dart';
export 'src/mobilesync/manager/sync_scheduler.dart';
export 'src/mobilesync/manager/clean_sync_ghosts.dart';
export 'src/mobilesync/manager/metadata_sync_manager.dart';
export 'src/mobilesync/manager/layout_sync_manager.dart';
export 'src/mobilesync/model/sync_state.dart';
export 'src/mobilesync/target/sync_down_target.dart';
export 'src/mobilesync/target/sync_up_target.dart';
export 'src/mobilesync/target/batch_sync_up_target.dart';
export 'src/mobilesync/target/collection_sync_up_target.dart';
export 'src/mobilesync/target/mru_sync_down_target.dart';
export 'src/mobilesync/target/briefcase_sync_down_target.dart';
export 'src/mobilesync/target/parent_children_sync_target.dart';
export 'src/mobilesync/config/sync_config.dart';

// ===== UI Components =====
export 'src/ui/login_activity.dart';
export 'src/ui/account_switcher_activity.dart';
export 'src/ui/screen_lock_activity.dart';
export 'src/ui/dev_info_activity.dart';
