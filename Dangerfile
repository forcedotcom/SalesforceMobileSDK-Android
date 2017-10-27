junit.parse "merged-test-results.xml"
junit.show_skipped_tests = true
junit.report

android_lint.report_file = "libs/SalesforceAnalytics/build/reports/lint-results.xml"
android_lint.severity = "Error"
android_lint.filtering = true
android_lint.lint(inline_mode: true)
android_lint.lint
android_lint.report_file = "libs/SalesforceSDK/build/reports/lint-results.xml"
android_lint.severity = "Error"
android_lint.filtering = true
android_lint.lint(inline_mode: true)
android_lint.lint
android_lint.report_file = "libs/SalesforceHybrid/build/reports/lint-results.xml"
android_lint.severity = "Error"
android_lint.filtering = true
android_lint.lint(inline_mode: true)
android_lint.lint
android_lint.report_file = "libs/SmartSync/build/reports/lint-results.xml"
android_lint.severity = "Error"
android_lint.filtering = true
android_lint.lint(inline_mode: true)
android_lint.lint
android_lint.report_file = "libs/SmartStore/build/reports/lint-results.xml"
android_lint.severity = "Error"
android_lint.filtering = true
android_lint.lint(inline_mode: true)
android_lint.lint
android_lint.report_file = "libs/SalesforceReact/build/reports/lint-results.xml"
android_lint.severity = "Error"
android_lint.filtering = true
android_lint.lint(inline_mode: true)
android_lint.lint

# Warn when there is a big PR
warn("Big PR, try to keep changes smaller if you can") if git.lines_of_code > 500

# Stop skipping some manual testing
warn("Needs testing on a Phone if change is non-trivial") if git.lines_of_code > 50 && !github.pr_title.include?("📱")

# Mainly to encourage writing up some reasoning about the PR, rather than
# just leaving a title
if github.pr_body.length < 3
  warn "Please provide a summary in the Pull Request description"
end

# Make it more obvious that a PR is a work in progress and shouldn't be merged yet.
has_wip_label = github.pr_labels.any? { |label| label.include? "WIP" }
has_wip_title = github.pr_title.include? "[WIP]"
has_dnm_label = github.pr_labels.any? { |label| label.include? "DO NOT MERGE" }
has_dnm_title = github.pr_title.include? "[DO NOT MERGE]"
if has_wip_label || has_wip_title
  warn("PR is classed as Work in Progress")
end
if has_dnm_label || has_dnm_title
  warn("At the authors request please DO NOT MERGE this PR")
end

fail "Please re-submit this PR to dev, we may have already fixed your issue." if github.branch_for_base != "dev"
