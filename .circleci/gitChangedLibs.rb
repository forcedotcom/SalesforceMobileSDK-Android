require 'json'
require 'set'

libsTopoSorted = ["SalesforceAnalytics", "SalesforceSDK", "SmartStore", "MobileSync", "SalesforceHybrid", "SalesforceReact", "RestExplorer"]

$GITPRAPI = "https://api.github.com/repos/%s/SalesforceMobileSDK-android/pulls/%s/files"

# No PR Number indicates that this PR is running in a CircleCI env linked to a fork, so force the url to forcedotcom project.
if ENV.has_key?('CIRCLE_PR_NUMBER')
  prFilesAPI = $GITPRAPI % [ENV['CIRCLE_PROJECT_USERNAME'], ENV['CIRCLE_PR_NUMBER']]
else
  prFilesAPI = $GITPRAPI % ['forcedotcom', ENV['CIRCLE_PULL_REQUEST'].split('/').last]
end

pullfiles = `#{"curl %s" % [prFilesAPI]}`
prfiles = JSON.parse(pullfiles)

libsModified = Set.new

for prfile in prfiles
  path = prfile["filename"]
  libsTopoSorted.each do |lib|
    if path.include?(lib)
      libsModified.merge(libsTopoSorted[libsTopoSorted.index(lib)..])
      break 
    end
  end
end

lib_arr = libsModified.to_a
# Print so the bash in the CircleCI yml can get the Libs to run
print (lib_arr.size == 1) ? lib_arr.first : lib_arr.join(",")
