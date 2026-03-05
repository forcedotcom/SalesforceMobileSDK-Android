import 'package:flutter/material.dart';
import 'package:salesforce_sdk/salesforce_sdk.dart';

/// Example application demonstrating the Salesforce Flutter SDK.
///
/// This app shows how to:
/// - Initialize the SDK
/// - Authenticate with Salesforce
/// - Make REST API calls
/// - Use SmartStore for local storage
/// - Sync data with MobileSync
void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const SalesforceExampleApp());
}

class SalesforceExampleApp extends StatelessWidget {
  const SalesforceExampleApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Salesforce Flutter SDK Demo',
      theme: ThemeData(
        primarySwatch: Colors.blue,
        useMaterial3: true,
      ),
      home: const HomePage(),
    );
  }
}

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  bool _isInitialized = false;
  bool _isLoggedIn = false;
  String _status = 'Not initialized';
  List<Map<String, dynamic>> _accounts = [];

  @override
  void initState() {
    super.initState();
    _initSDK();
  }

  Future<void> _initSDK() async {
    try {
      await SalesforceSDKManager.instance.init(
        config: const BootConfig(
          // Replace with your connected app's consumer key
          clientId: 'YOUR_CONSUMER_KEY',
          callbackUrl: 'sfdc://oauth/callback',
          scopes: ['api', 'web', 'refresh_token'],
        ),
      );

      setState(() {
        _isInitialized = true;
        _isLoggedIn =
            SalesforceSDKManager.instance.userAccountManager.isLoggedIn;
        _status = _isLoggedIn ? 'Logged in' : 'Ready to login';
      });
    } catch (e) {
      setState(() {
        _status = 'Init failed: $e';
      });
    }
  }

  Future<void> _login() async {
    try {
      setState(() => _status = 'Logging in...');
      await SalesforceSDKManager.instance.login(context);
      setState(() {
        _isLoggedIn = true;
        _status = 'Logged in successfully';
      });
    } catch (e) {
      setState(() => _status = 'Login failed: $e');
    }
  }

  Future<void> _logout() async {
    try {
      await SalesforceSDKManager.instance.logout();
      setState(() {
        _isLoggedIn = false;
        _accounts = [];
        _status = 'Logged out';
      });
    } catch (e) {
      setState(() => _status = 'Logout failed: $e');
    }
  }

  Future<void> _fetchAccounts() async {
    final client = SalesforceSDKManager.instance.getRestClient();
    if (client == null) return;

    setState(() => _status = 'Fetching accounts...');

    try {
      final request = RestRequest.getRequestForQuery(
        '59.0',
        'SELECT Id, Name, Industry FROM Account LIMIT 10',
      );
      final response = await client.sendAsync(request);

      if (response.isSuccess) {
        final records = response.records ?? [];
        setState(() {
          _accounts = records.cast<Map<String, dynamic>>();
          _status = 'Fetched ${_accounts.length} accounts';
        });
      } else {
        setState(() => _status = 'Fetch failed: ${response.statusCode}');
      }
    } catch (e) {
      setState(() => _status = 'Error: $e');
    }
  }

  Future<void> _demoSmartStore() async {
    setState(() => _status = 'Running SmartStore demo...');

    try {
      final store = await SmartStore.getInstance();

      // Register a soup
      await store.registerSoup('Contacts', [
        IndexSpec.json1('FirstName'),
        IndexSpec.json1('LastName'),
        IndexSpec.json1('Email'),
      ]);

      // Create entries
      await store.create('Contacts', {
        'FirstName': 'John',
        'LastName': 'Doe',
        'Email': 'john.doe@example.com',
      });
      await store.create('Contacts', {
        'FirstName': 'Jane',
        'LastName': 'Smith',
        'Email': 'jane.smith@example.com',
      });

      // Query all
      final querySpec = QuerySpec.buildAllQuerySpec(
        'Contacts',
        orderPath: 'LastName',
        pageSize: 10,
      );
      final results = await store.query(querySpec, 0);

      // Count
      final count = await store.countQuery(querySpec);

      setState(() {
        _status = 'SmartStore: $count contacts stored. '
            'First: ${results.isNotEmpty ? results.first['FirstName'] : 'none'}';
      });

      // Cleanup
      await store.dropSoup('Contacts');
    } catch (e) {
      setState(() => _status = 'SmartStore error: $e');
    }
  }

  Future<void> _demoMobileSync() async {
    if (!_isLoggedIn) {
      setState(() => _status = 'Login first to demo MobileSync');
      return;
    }

    setState(() => _status = 'Running MobileSync demo...');

    try {
      final client = SalesforceSDKManager.instance.getRestClient()!;
      final store = await SmartStore.getInstance();

      // Register soup for accounts
      if (!(await store.hasSoup('AccountSync'))) {
        await store.registerSoup('AccountSync', [
          IndexSpec.json1('Id'),
          IndexSpec.json1('Name'),
          IndexSpec.json1('__local__'),
        ]);
      }

      final syncManager = await SyncManager.getInstance(
        smartStore: store,
        restClient: client,
      );

      // Sync down accounts
      final syncState = await syncManager.syncDown(
        target: SoqlSyncDownTarget(
          query: 'SELECT Id, Name, Industry FROM Account LIMIT 5',
        ),
        soupName: 'AccountSync',
        callback: (state) {
          if (mounted) {
            setState(() {
              _status =
                  'Syncing: ${state.progress}/${state.totalSize} - ${state.status.name}';
            });
          }
        },
      );

      setState(() {
        _status = 'MobileSync complete: '
            '${syncState.progress} records synced (${syncState.status.name})';
      });
    } catch (e) {
      setState(() => _status = 'MobileSync error: $e');
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Salesforce SDK Demo'),
      ),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16.0),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Status: $_status',
                        style: Theme.of(context).textTheme.bodyLarge),
                    const SizedBox(height: 8),
                    Text(
                      _isLoggedIn ? 'Authenticated' : 'Not authenticated',
                      style: TextStyle(
                        color: _isLoggedIn ? Colors.green : Colors.red,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 16),
            if (!_isLoggedIn)
              ElevatedButton(
                onPressed: _isInitialized ? _login : null,
                child: const Text('Login to Salesforce'),
              ),
            if (_isLoggedIn) ...[
              ElevatedButton(
                onPressed: _fetchAccounts,
                child: const Text('Fetch Accounts (REST)'),
              ),
              const SizedBox(height: 8),
              ElevatedButton(
                onPressed: _demoSmartStore,
                child: const Text('Demo SmartStore'),
              ),
              const SizedBox(height: 8),
              ElevatedButton(
                onPressed: _demoMobileSync,
                child: const Text('Demo MobileSync'),
              ),
              const SizedBox(height: 8),
              OutlinedButton(
                onPressed: _logout,
                child: const Text('Logout'),
              ),
            ],
            const SizedBox(height: 16),
            if (_accounts.isNotEmpty) ...[
              Text('Accounts:',
                  style: Theme.of(context).textTheme.titleMedium),
              const SizedBox(height: 8),
              Expanded(
                child: ListView.builder(
                  itemCount: _accounts.length,
                  itemBuilder: (context, index) {
                    final account = _accounts[index];
                    return ListTile(
                      title: Text(account['Name'] ?? 'Unknown'),
                      subtitle: Text(account['Industry'] ?? 'No industry'),
                      leading: const Icon(Icons.business),
                    );
                  },
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
