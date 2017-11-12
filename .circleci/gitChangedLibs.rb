require 'json'
require 'set'

$GITPRAPI = "https://api.github.com/repos/%s/SalesforceMobileSDK-android/pulls/%s/files"
$libs = ["SalesforceAnalytics", "SalesforceHybridSDK", "SalesforceReact", "SalesforceSDK", "SmartStore", "SmartSync"]

prFilesAPI = $GITPRAPI % [ENV["CIRCLE_PROJECT_USERNAME"], ENV["CIRCLE_PR_NUMBER"]]
pullfiles = `#{"curl %s" % [prFilesAPI]}`
prfiles = JSON.parse(pullfiles)

libs = Set.new
for prfile in prfiles
  path = prfile["filename"]
  for lib in $libs
    if path.include? lib
      libs = libs.add(lib)
    end
  end
end

libs_ar = libs.to_a()
if !libs_ar.empty?() && libs_ar.include?("SalesforceSDK")
  libs_ar = $libs.to_a()

elsif libs_ar.include?("SmartStore")
      libs_ar.push("SmartSync")
      libs_ar.push("SalesforceHybridSDK")
      libs_ar.push("SalesforceReact")

elsif libs_ar.include?("SmartSync")
      libs_ar.push("SalesforceHybridSDK")
      libs_ar.push("SalesforceReact")
end

print libs_ar.uniq.join(", ")
