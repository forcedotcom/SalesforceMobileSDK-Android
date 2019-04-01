require 'json'
credentials = {
    'instance_url' => ENV['INSTANCE_URL'],
    'test_client_id' => ENV['TEST_CLIENT_ID'],
    'test_redirect_uri' => ENV['TEST_REDIRECT_URI'],
    'refresh_token' => ENV['REFRESH_TOKEN'],
    'identity_url' => ENV['IDENTITY_URL'],
    'community_url' => ENV['COMMUNITY_URL'],
    'test_login_domain' => ENV['TEST_LOGIN_DOMAIN'],
    'access_token' => ENV['ACCESS_TOKEN'],
    'organization_id' => ENV['ORG_ID'],
    'username' => ENV['USER_NAME'],
    'user_id' => ENV['USER_ID'],
    'photo_url' => ENV['PHOTO_URL']
}

File.open('shared/test/test_credentials.json', 'w+').write(credentials.to_json)

