#!/usr/bin/env ruby

# Warn when there is a big PR
warn("Big PR, try to keep changes smaller if you can.", sticky: true) if git.lines_of_code > 1000

# Redirect contributors to PR to dev.
# dpop is a temporary exception for the multi-PR DPoP rollout. Remove once DPoP merges back to dev.
fail("Please re-submit this PR to the dev branch, we may have already fixed your issue.", sticky: true) if !["dev", "dpop"].include?(github.branch_for_base)

# List of Android libraries for testing
LIBS = ['SalesforceAnalytics', 'SalesforceSDK', 'SmartStore', 'MobileSync', 'SalesforceHybrid']

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

# Detect AuthFlowTester changes — if any, run the full UI suite instead of the fixed PR subset
authflowtester_changed = (git.modified_files + git.added_files).any? { |f|
  f.start_with?("native/NativeSampleApps/AuthFlowTester/")
}
`echo "run_all_ui_tests=#{authflowtester_changed}" >> $GITHUB_OUTPUT`
