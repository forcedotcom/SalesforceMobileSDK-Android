import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// A Flutter widget that provides the screen lock UI.
///
/// Displays a biometric authentication prompt or PIN entry screen
/// to unlock the app based on mobile security policy.
class ScreenLockActivity extends StatefulWidget {
  /// Whether biometric authentication is available and should be attempted.
  final bool biometricEnabled;

  /// Callback when the screen is successfully unlocked.
  final VoidCallback? onUnlocked;

  /// Callback when the user cancels or fails to unlock.
  final VoidCallback? onLockFailed;

  /// Title text displayed on the lock screen.
  final String title;

  /// Description text displayed on the lock screen.
  final String description;

  const ScreenLockActivity({
    super.key,
    this.biometricEnabled = true,
    this.onUnlocked,
    this.onLockFailed,
    this.title = 'App Locked',
    this.description = 'Authenticate to continue',
  });

  @override
  State<ScreenLockActivity> createState() => _ScreenLockActivityState();
}

class _ScreenLockActivityState extends State<ScreenLockActivity> {
  bool _isAuthenticating = false;
  String? _errorMessage;

  @override
  void initState() {
    super.initState();
    if (widget.biometricEnabled) {
      WidgetsBinding.instance.addPostFrameCallback((_) => _authenticate());
    }
  }

  Future<void> _authenticate() async {
    if (_isAuthenticating) return;
    setState(() {
      _isAuthenticating = true;
      _errorMessage = null;
    });

    try {
      // In a real implementation, this would use local_auth plugin
      // final localAuth = LocalAuthentication();
      // final authenticated = await localAuth.authenticate(...);
      // For now, simulate success
      widget.onUnlocked?.call();
    } on PlatformException catch (e) {
      setState(() {
        _errorMessage = e.message ?? 'Authentication failed';
      });
    } finally {
      if (mounted) {
        setState(() => _isAuthenticating = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: Padding(
            padding: const EdgeInsets.all(32),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const Icon(Icons.lock, size: 80, color: Colors.blue),
                const SizedBox(height: 24),
                Text(
                  widget.title,
                  style: Theme.of(context).textTheme.headlineMedium,
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 12),
                Text(
                  widget.description,
                  style: Theme.of(context).textTheme.bodyLarge,
                  textAlign: TextAlign.center,
                ),
                if (_errorMessage != null) ...[
                  const SizedBox(height: 16),
                  Text(
                    _errorMessage!,
                    style: TextStyle(
                      color: Theme.of(context).colorScheme.error,
                    ),
                    textAlign: TextAlign.center,
                  ),
                ],
                const SizedBox(height: 32),
                if (widget.biometricEnabled)
                  ElevatedButton.icon(
                    onPressed: _isAuthenticating ? null : _authenticate,
                    icon: const Icon(Icons.fingerprint),
                    label: Text(
                      _isAuthenticating ? 'Authenticating...' : 'Unlock',
                    ),
                  ),
                if (widget.onLockFailed != null) ...[
                  const SizedBox(height: 16),
                  TextButton(
                    onPressed: widget.onLockFailed,
                    child: const Text('Cancel'),
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// A dialog for opting in to biometric authentication.
class BiometricAuthOptInPrompt extends StatelessWidget {
  /// Callback when the user opts in to biometric auth.
  final VoidCallback? onOptIn;

  /// Callback when the user declines biometric auth.
  final VoidCallback? onDecline;

  const BiometricAuthOptInPrompt({
    super.key,
    this.onOptIn,
    this.onDecline,
  });

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      icon: const Icon(Icons.fingerprint, size: 48),
      title: const Text('Enable Biometric Authentication?'),
      content: const Text(
        'Would you like to use biometric authentication (fingerprint, face, etc.) '
        'to quickly unlock the app?',
      ),
      actions: [
        TextButton(
          onPressed: () {
            Navigator.of(context).pop(false);
            onDecline?.call();
          },
          child: const Text('Not Now'),
        ),
        ElevatedButton(
          onPressed: () {
            Navigator.of(context).pop(true);
            onOptIn?.call();
          },
          child: const Text('Enable'),
        ),
      ],
    );
  }

  /// Shows the biometric opt-in prompt as a dialog.
  static Future<bool?> show(BuildContext context) {
    return showDialog<bool>(
      context: context,
      builder: (context) => const BiometricAuthOptInPrompt(),
    );
  }
}
