#!/usr/bin/env ruby

test_results = "libs/#{ENV['CURRENT_LIB']}/build/outputs/androidTest-results/connected/test-results.xml"

if File.file?(test_results)
  junit.parse test_results
  junit.show_skipped_tests = true
  junit.report
else
  warn("Tests did not run to completion.")
end

if File.file?("libs/#{ENV['CURRENT_LIB']}/build/reports/lint-results.xml")
  android_lint.report_file = "libs/#{ENV['CURRENT_LIB']}/build/reports/lint-results.xml"
  android_lint.filtering = true
  android_lint.lint(inline_mode: true)
else
  warn("No Lint Results.")
end

# State what Library the test failures are for (or don't post at all).
markdown "# Tests results for #{ENV['CURRENT_LIB']}" unless status_report[:errors].empty? && status_report[:warnings].empty?