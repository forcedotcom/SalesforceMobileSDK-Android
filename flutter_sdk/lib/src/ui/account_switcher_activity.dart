import 'package:flutter/material.dart';
import '../core/accounts/user_account.dart';

/// A Flutter widget that displays the account switcher UI.
///
/// Shows a list of authenticated user accounts and allows the user
/// to switch between them or add a new account.
class AccountSwitcherActivity extends StatelessWidget {
  /// The list of authenticated user accounts.
  final List<UserAccount> accounts;

  /// The currently active account.
  final UserAccount? currentAccount;

  /// Callback when a user selects a different account.
  final void Function(UserAccount account)? onAccountSelected;

  /// Callback when the user wants to add a new account.
  final VoidCallback? onAddNewAccount;

  /// Callback when the user wants to log out of an account.
  final void Function(UserAccount account)? onLogout;

  const AccountSwitcherActivity({
    super.key,
    required this.accounts,
    this.currentAccount,
    this.onAccountSelected,
    this.onAddNewAccount,
    this.onLogout,
  });

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Switch Account')),
      body: Column(
        children: [
          Expanded(
            child: accounts.isEmpty
                ? const Center(child: Text('No accounts'))
                : ListView.builder(
                    itemCount: accounts.length,
                    itemBuilder: (context, index) {
                      final account = accounts[index];
                      final isCurrent =
                          currentAccount?.userId == account.userId;
                      return UserAccountListItem(
                        account: account,
                        isCurrent: isCurrent,
                        onTap: () => onAccountSelected?.call(account),
                        onLogout: () => onLogout?.call(account),
                      );
                    },
                  ),
          ),
          if (onAddNewAccount != null)
            SafeArea(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: SizedBox(
                  width: double.infinity,
                  child: ElevatedButton.icon(
                    onPressed: onAddNewAccount,
                    icon: const Icon(Icons.add),
                    label: const Text('Add New Account'),
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

/// Displays a single user account in the account switcher list.
class UserAccountListItem extends StatelessWidget {
  final UserAccount account;
  final bool isCurrent;
  final VoidCallback? onTap;
  final VoidCallback? onLogout;

  const UserAccountListItem({
    super.key,
    required this.account,
    this.isCurrent = false,
    this.onTap,
    this.onLogout,
  });

  @override
  Widget build(BuildContext context) {
    return ListTile(
      leading: CircleAvatar(
        backgroundColor: isCurrent
            ? Theme.of(context).colorScheme.primary
            : Theme.of(context).colorScheme.surfaceContainerHighest,
        child: Text(
          (account.username?.isNotEmpty == true
                  ? account.username![0]
                  : account.userId[0])
              .toUpperCase(),
          style: TextStyle(
            color: isCurrent
                ? Theme.of(context).colorScheme.onPrimary
                : Theme.of(context).colorScheme.onSurface,
          ),
        ),
      ),
      title: Text(account.username ?? account.userId),
      subtitle: Text(account.instanceUrl),
      trailing: isCurrent
          ? const Icon(Icons.check_circle, color: Colors.green)
          : onLogout != null
              ? IconButton(
                  icon: const Icon(Icons.logout),
                  onPressed: onLogout,
                  tooltip: 'Log out',
                )
              : null,
      onTap: isCurrent ? null : onTap,
    );
  }
}

/// Shows the account switcher as a bottom sheet.
Future<UserAccount?> showAccountSwitcher(
  BuildContext context, {
  required List<UserAccount> accounts,
  UserAccount? currentAccount,
  VoidCallback? onAddNewAccount,
  void Function(UserAccount)? onLogout,
}) {
  return showModalBottomSheet<UserAccount>(
    context: context,
    isScrollControlled: true,
    builder: (context) {
      return DraggableScrollableSheet(
        initialChildSize: 0.5,
        maxChildSize: 0.9,
        minChildSize: 0.3,
        expand: false,
        builder: (context, scrollController) {
          return Column(
            children: [
              Padding(
                padding: const EdgeInsets.all(16),
                child: Text(
                  'Switch Account',
                  style: Theme.of(context).textTheme.titleLarge,
                ),
              ),
              Expanded(
                child: ListView.builder(
                  controller: scrollController,
                  itemCount: accounts.length,
                  itemBuilder: (context, index) {
                    final account = accounts[index];
                    final isCurrent =
                        currentAccount?.userId == account.userId;
                    return UserAccountListItem(
                      account: account,
                      isCurrent: isCurrent,
                      onTap: () => Navigator.pop(context, account),
                      onLogout: onLogout != null
                          ? () => onLogout(account)
                          : null,
                    );
                  },
                ),
              ),
              if (onAddNewAccount != null)
                Padding(
                  padding: const EdgeInsets.all(16),
                  child: SizedBox(
                    width: double.infinity,
                    child: OutlinedButton.icon(
                      onPressed: () {
                        Navigator.pop(context);
                        onAddNewAccount();
                      },
                      icon: const Icon(Icons.add),
                      label: const Text('Add New Account'),
                    ),
                  ),
                ),
            ],
          );
        },
      );
    },
  );
}
