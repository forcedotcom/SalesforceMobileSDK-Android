# Test Shards

This directory contains shard configurations and local runners for Android instrumentation tests. The library configurations are consumed by `.github/workflows/reusable-lib-workflow.yaml` to submit parallel Firebase Test Lab shards in CI. The scripts below let contributors run the same configuration locally.

Configured libraries are `SalesforceSDK`, `MobileSync`, and `SalesforceHybrid`. A library without a JSON file has no defined shard layout and must use its normal Gradle test task.

## Benefits

- Because shards run on separate devices, they reduce failures caused by environment pollution. If a test fails to clean up after itself, it will not affect other shards.
- Tests must still clean up after themselves. Sharding is not a substitute for deterministic tests.
- Faster PR _and_ Nightly runs.

## Pitfalls

- Because shards run in parallel, they need to be grouped intelligently. Many of our "unit tests" are actually integration tests. Sharding might save us from database contention, but tests in separate shards that manipulate the same org data concurrently can still fail.
- It is now possible for tests to run more than once or be skipped if we are not careful.

## Maintenance

Each configuration file defines targets using the `class` keyword. To ensure all tests run exactly once, each config has a `remaining` shard that **only** uses the `notClass` keyword. New test classes are automatically included in `remaining`.

Classes added to a named shard must also be added to `remaining` as `notClass` exclusions, preventing duplicate execution. Conversely, remove those exclusions when removing a named shard target, so tests are not skipped. CI validates this invariant.

## Local runners

Run these commands from the repository root. Both runners also locate the repository root themselves and reject an unknown library or invalid shard configuration.

### Connected device

`run-local-sharded-tests.sh` runs every configured shard sequentially against one connected device. Use it to reproduce a CI shard locally while preserving its test selection; it does not run shards in parallel.

```bash
# Uses ANDROID_SERIAL, or the only ready connected device.
.github/test-shards/run-local-sharded-tests.sh SalesforceSDK

# Targets a specific emulator or device.
.github/test-shards/run-local-sharded-tests.sh MobileSync emulator-5554
```

Prerequisites:

- Run `./install.sh` and provide `shared/test/test_credentials.json`.
- Start and unlock one API 31+ device that can reach the configured test org.
- Install the Cordova CLI when running `SalesforceHybrid`.

The runner uses [`jq`](https://jqlang.org/), a command-line JSON processor, to validate the shard configuration and construct test selections.

The runner saves each shard's Gradle log, copied connected-test XML, and HTML report under `build/connected-shard-results/<library>-<UTC timestamp>/`. It continues after a failed shard so all shard results are collected, then exits nonzero if any shard failed or produced no test result.

### Firebase Test Lab

`run-firebase-sharded-tests.sh` builds a library test APK and RestExplorer, then submits all configured shards to Firebase Test Lab for one API level.

```bash
.github/test-shards/run-firebase-sharded-tests.sh SalesforceSDK 35
.github/test-shards/run-firebase-sharded-tests.sh SalesforceHybrid 36
```

Prerequisites:

- Run `./install.sh` and provide `shared/test/test_credentials.json`.
- Install `jq`, a command-line JSON processor. No local device or `adb` is required.
- Install the Cordova CLI when running `SalesforceHybrid`.
- Authenticate `gcloud` to the `mobile-apps-firebase-test` project and install its beta component.
- Use only least-privileged, disposable credentials: the test APK embeds `shared/test/test_credentials.json` and is uploaded to Firebase Test Lab.

The accepted API levels are 31 through 37. API 36 and later use the 16 KB-page-size Firebase device model. Firebase result directories use `<library>-api-<level>-local-<UTC timestamp>-<random suffix>`.

The CI workflow does not call this script directly. It builds equivalent shard arguments from the same JSON configuration in `.github/workflows/reusable-lib-workflow.yaml`; keep the script and workflow behavior aligned when changing shard semantics.

### AuthFlowTester Firebase groups

`run-authflowtester-firebase-tests.sh` is the CI runner for AuthFlowTester groups. It requires the GitHub Actions environment variables `FULL_API_RANGE`, `PS16K_MIN_API_VERSION`, and `RUN_NUMBER`, plus built AuthFlowTester app and test APKs. It supports these groups:

| Group | Test selection | Firebase timeout |
| --- | --- | --- |
| `login` | All AuthFlowTester tests except the explicitly isolated groups | 60 minutes |
| `welcome` | Welcome and Advanced Auth Beacon tests | 10 minutes |
| `community` | Community login tests | 45 minutes |
| `native-login` | Native Login tests | 45 minutes |
| `token-lifecycle` | DPoP, restart, RTR, and refresh-token-migration tests | 60 minutes |
| `multi-user` | Multi-user login tests | 60 minutes |

The script runs every requested group, reports all failed group names, and exits nonzero after the final group. Invoke it through `.github/workflows/reusable-ui-test-group.yaml`; its required UI-test configuration and secrets are intentionally not documented as a local command.

For the AuthFlowTester test suite and CI model, see [`native/NativeSampleApps/AuthFlowTester/README.md`](../../native/NativeSampleApps/AuthFlowTester/README.md).
