boot_configs = %w(libs/test/SalesforceAnalyticsTest/res/values/bootconfig.xml
                  libs/test/SalesforceSDKTest/res/values/bootconfig.xml
                  libs/test/SmartStoreTest/res/values/bootconfig.xml
                  libs/test/SmartSyncTest/res/values/bootconfig.xml
                  libs/test/SalesforceHybridTest/src/com/salesforce/androidsdk/phonegap/ui/SalesforceHybridTestActivity.java
                  libs/test/SalesforceReactTest/src/com/salesforce/androidsdk/reactnative/util/ReactTestActivity.java)

for config in boot_configs do
    file_contents = File.read(config)
    file_contents.gsub!('__CONSUMER_KEY__', ENV['CONSUMER_KEY'])
    file_contents.gsub!('__REDIRECT_URI__', ENV['REDIRECT_URI'])
    file_contents.gsub!('__ACCOUNT_NAME__', ENV['ACCOUNT_NAME'])
    file_contents.gsub!('__INSTANCE_URL__', ENV['INSTANCE_URL'])
    file_contents.gsub!('__COMMUNITY_URL__', ENV['COMMUNITY_URL'])
    file_contents.gsub!('__IDENTITY_URL__', ENV['IDENTITY_URL'])
    file_contents.gsub!('__LOGIN_URL__',     ENV['LOGIN_URL'])
    file_contents.gsub!('__REFRESH_TOKEN__', ENV['REFRESH_TOKEN'])
    file_contents.gsub!('__ORG_ID__', ENV['ORG_ID'])
    file_contents.gsub!('__USER_ID__', ENV['USER_ID'])
    file_contents.gsub!('__USER_NAME__', ENV['USER_NAME'])
    file_contents.gsub!('__PHOTO_URL__', ENV['PHOTO_URL'])
    File.open(config, 'w+').write(file_contents)
end

