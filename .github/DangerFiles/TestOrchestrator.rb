#!/usr/bin/env ruby

# Warn when there is a big PR
warn("Big PR, try to keep changes smaller if you can.", sticky: true) if git.lines_of_code > 1000

# Redirect contributors to PR to dev.
fail("Please re-submit this PR to the dev branch, we may have already fixed your issue.", sticky: true) if github.branch_for_base != "dev"

# List of Android libraries for testing
LIBS = ['SalesforceAnalytics', 'SalesforceSDK', 'SmartStore', 'MobileSync', 'SalesforceHybrid', 'SalesforceReact']

modified_libs = Set[]
for file in (git.modified_files + git.added_files);
    scheme = file.split("libs/").last.split("/").first
    if LIBS.include?(scheme) 
        modified_libs.add(scheme)
    end
end

# If modified_libs is empty, add all LIBS
if modified_libs.empty?
  modified_libs.merge(LIBS)
end

# Set Github Job output so we know which tests to run
json_libs = modified_libs.map { |l| "'#{l}'"}.join(", ")
`echo "libs=[#{json_libs}]" >> $GITHUB_OUTPUT`
