# Android Test Shards

Use this guide when changing Android instrumentation-test shard configuration, shard runners, or their GitHub Actions integration.

## Source Of Truth

- `.github/test-shards/README.md` documents shard semantics, supported libraries, local runners, prerequisites, output locations, and AuthFlowTester group ownership.
- `.github/test-shards/<library>.json` defines named `class` shards and the `remaining` `notClass` shard for `SalesforceSDK`, `MobileSync`, and `SalesforceHybrid`.
- `.github/workflows/reusable-lib-workflow.yaml` builds Firebase Test Lab shard arguments from those JSON files in CI.
- `.github/workflows/reusable-ui-test-group.yaml` invokes `run-authflowtester-firebase-tests.sh` for credential-backed AuthFlowTester groups.

## Required Invariants

- Every shard config has exactly one `remaining` shard.
- Every named-shard `class` target appears exactly once as a `notClass` target in `remaining`.
- Preserve intentional isolation documented in the JSON `comment` fields; do not combine network, UI, WebView, or MockK-sensitive classes merely to balance shard duration.
- Do not commit `shared/test/test_credentials.json` or `shared/test/ui_test_config.json`. Firebase uploads test APKs containing the applicable test configuration, so use only disposable least-privileged test accounts.
- Library shard runners are local developer tools. Keep their validation and selection behavior aligned with `reusable-lib-workflow.yaml`; do not replace CI workflow steps with them without separately reviewing CI permissions, authentication, artifacts, and result reporting.

## Validation

When modifying a JSON shard configuration or runner, validate the affected configuration with `jq`, run `bash -n` on changed shell scripts, and inspect the corresponding workflow's target construction. When a connected API 31+ emulator and test credentials are available, use `run-local-sharded-tests.sh <library> <serial>` for behavioral validation.
