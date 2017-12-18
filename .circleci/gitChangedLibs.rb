require 'json'
require 'set'

$GITPRAPI = "https://api.github.com/repos/%s/SalesforceMobileSDK-android/pulls/%s/files"
libsTopoSorted = ["SalesforceAnalytics", "SalesforceSDK", "SmartStore", "SmartSync", "SalesforceHybrid", "SalesforceReact"]

prFilesAPI = $GITPRAPI % [ENV["CIRCLE_PROJECT_USERNAME"], ENV["CIRCLE_PR_NUMBER"]]
pullfiles = `#{"curl %s" % [prFilesAPI]}`
prfiles = JSON.parse(pullfiles)

libsModified = Set.new
for prfile in prfiles
  path = prfile["filename"]
  for lib in libsTopoSorted
    if path.include? lib
      libsModified = libsModified.add(lib)
    end
  end
end

# Each Lib in libsTopoSorted depends on the lib that preceeds it.  Find the lowest dependency and take everything after it.
libsToTest = libsTopoSorted.slice!(libsModified.to_a().map{|l| libsTopoSorted.find_index(l)}.min() .. (libsTopoSorted.length - 1))

# SaleforceReact doesn't depend on SalesforceHybridSDK
if !libsModified.include?("SalesforceReact") && libsToTest.first.eql?("SalesforceHybrid")
  libsToTest.pop()
end

# Print so the bash in the CircleCI yml can get the Libs to run
print libsToTest.join(",")
