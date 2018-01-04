#!/usr/bin/env ruby

message("Tests results for #{ENV['CURRENT_LIB']}")

if File.file?("test-results.xml")
  junit.parse "test-results.xml"
  junit.show_skipped_tests = true
  junit.report
else
  fail("Tests did not run to completion.", sticky: true)
end

if File.file?("libs/#{ENV['CURRENT_LIB']}/build/reports/lint-results.xml")
  android_lint.report_file = "libs/#{ENV['CURRENT_LIB']}/build/reports/lint-results.xml"
  # UNCOMMENT THIS - only commented for testing
  #android_lint.filtering = true
  android_lint.lint(inline_mode: true)
else
  fail("No Lint Results.", sticky: true)
end