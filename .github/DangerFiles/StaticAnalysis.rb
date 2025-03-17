#!/usr/bin/env ruby

report_file_path = "libs/#{ENV['LIB']}/build/reports/lint-results-debug.xml"
if File.file?(report_file_path)
    android_lint.skip_gradle_task = true
    android_lint.report_file = report_file_path
    android_lint.filtering = true
    android_lint.lint(inline_mode: true)
else
    fail("No Lint Results.")
end