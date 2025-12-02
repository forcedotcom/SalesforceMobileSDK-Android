# Test Shards

This directory contains JSON configuration files that define test shards for individual libraries.  The overarching goal of sharding is to reduce the time it takes to run tests for PR feedback.  Shards run in parallel, which comes with benefits, new potential pitfalls and a small amount of potential maintenance overhead.

## Benefits

- Because shards run on seperate devices, this should reduce the unnecessary failures caused by environment pollution.  If a test fails to cleanup after itself, it will not affect other shards.
    - Please continue to write tests that cleanup after themselves. I do not want to see this become a crutch for flaky tests.
- Faster PR _and_ Nightly runs.

## Pitfalls

-  Because shards run in parallel, they need to be grouped intelligently.  Many of our "unit tests"   are actually integration tests.  Sharding might save us from database contention, but if tests in seperate shards make API calls to manipulate data in the same org simultaneously this will cause failures.
- It is now possible for tests to run more than once or be skipped if we are not careful.

## Maintenance

Each confiruration file defines targets using the `class` keyword.  To ensure all tests are run, each config has a "remaining" shard that **only** uses the `notClass` keyword.  New test classes will automatically be included in the "remaining" shard.

However, it is very important that classes added to shards are also added to the "remaining" shard to prevent them from running more than once.  Likewise, classes that are removed from shards need to also be removed from the "remaining" shard so they are not skipped.  CI will validate this.
