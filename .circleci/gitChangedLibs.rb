require 'json'
require 'set'

libsTopoSorted = ["SalesforceAnalytics", "SalesforceSDK", "SmartStore", "SmartSync", "SalesforceHybrid", "SalesforceReact"]

if not ENV["CIRCLE_PULL_REQUEST"].nil?
  $GITPRAPI = "https://api.github.com/repos/%s/SalesforceMobileSDK-android/pulls/%s/files"

  prFilesAPI = $GITPRAPI % [ENV["CIRCLE_PROJECT_USERNAME"], ENV["CIRCLE_PR_NUMBER"]]
  pullfiles = `#{"curl %s" % [prFilesAPI]}`
  prfiles = JSON.parse(pullfiles)

  libsModified = Set.new
  for prfile in prfiles
    path = prfile["filename"]
    for lib in libsTopoSorted
      if path.include? lib
        libsModified.add(lib)
      end
    end
  end

  # Print so the bash in the CircleCI yml can get the Libs to run
  print libsModified.to_a.join(",")
else
  print libsTopoSorted.join(",")
end
