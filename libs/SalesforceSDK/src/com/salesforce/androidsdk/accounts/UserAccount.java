/*
 * Copyright (c) 2014, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use of this software in source and binary forms, with or
 * without modification, are permitted provided that the following conditions
 * are met:
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * - Neither the name of salesforce.com, inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission of salesforce.com, inc.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.androidsdk.accounts;

import org.json.JSONException;
import org.json.JSONObject;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import com.salesforce.androidsdk.app.SalesforceSDKManager;

/**
 * This class represents a single user account that is currently
 * logged in against a Salesforce endpoint. It encapsulates data
 * that is used to uniquely identify a single user account.
 *
 * @author bhariharan
 */
public class UserAccount {

	public static final String AUTH_TOKEN = "authToken";
	public static final String REFRESH_TOKEN = "refreshToken";
	public static final String LOGIN_SERVER = "loginServer";
	public static final String ID_URL = "idUrl";
	public static final String INSTANCE_SERVER = "instanceServer";
	public static final String ORG_ID = "orgId";
	public static final String USER_ID = "userId";
	public static final String USERNAME = "username";
	public static final String ACCOUNT_NAME = "accountName";
	public static final String CLIENT_ID = "clientId";
	public static final String COMMUNITY_ID = "communityId";
	public static final String COMMUNITY_URL = "communityUrl";
	public static final String INTERNAL_COMMUNITY_ID = "000000000000000000";
	public static final String INTERNAL_COMMUNITY_PATH = "internal";

	private static final String TAG = "UserAccount";
	private static final String FORWARD_SLASH = "/";
	private static final String UNDERSCORE = "_";

	private String authToken;
	private String refreshToken;
	private String loginServer;
	private String idUrl;
	private String instanceServer;
	private String orgId;
	private String userId;
	private String username;
	private String accountName;
	private String clientId;
	private String communityId;
	private String communityUrl;

	/**
	 * Parameterized constructor.
	 *
	 * @param authToken Auth token.
	 * @param refreshToken Refresh token.
	 * @param loginServer Login server.
	 * @param idUrl Identity URL.
	 * @param instanceServer Instance server.
	 * @param orgId Org ID.
	 * @param userId User ID.
	 * @param username Username.
	 * @param accountName Account name.
	 * @param clientId Client ID.
	 * @param communityId Community ID.
	 * @param communityUrl Community URL.
	 */
	public UserAccount(String authToken, String refreshToken,
			String loginServer, String idUrl, String instanceServer,
			String orgId, String userId, String username, String accountName,
			String clientId, String communityId, String communityUrl) {
		this.authToken = authToken;
		this.refreshToken = refreshToken;
		this.loginServer = loginServer;
		this.idUrl = idUrl;
		this.instanceServer = instanceServer;
		this.orgId = orgId;
		this.userId = userId;
		this.username = username;
		this.accountName = accountName;
		this.clientId = clientId;
		this.communityId = communityId;
		this.communityUrl = communityUrl;
	}

	/**
	 * Parameterized constructor.
	 *
	 * @param object JSON object.
	 */
	public UserAccount(JSONObject object) {
		if (object != null) {
			authToken = object.optString(AUTH_TOKEN, null);
			refreshToken = object.optString(REFRESH_TOKEN, null);
			loginServer = object.optString(LOGIN_SERVER, null);
			idUrl = object.optString(ID_URL, null);
			instanceServer = object.optString(INSTANCE_SERVER, null);
			orgId = object.optString(ORG_ID, null);
			userId = object.optString(USER_ID, null);
			username = object.optString(USERNAME, null);
			clientId = object.optString(CLIENT_ID, null);
			if (!TextUtils.isEmpty(username) && !TextUtils.isEmpty(instanceServer)) {
				accountName = String.format("%s (%s) (%s)", username, instanceServer,
						SalesforceSDKManager.getInstance().getApplicationName());
			}
			communityId = object.optString(communityId, null);
			communityUrl = object.optString(communityUrl, null);
		}
	}

	/**
	 * Parameterized constructor.
	 *
	 * @param bundle Bundle.
	 */
	public UserAccount(Bundle bundle) {
		if (bundle != null) {
			authToken = bundle.getString(AUTH_TOKEN);
			refreshToken = bundle.getString(REFRESH_TOKEN);
			loginServer = bundle.getString(LOGIN_SERVER);
			idUrl = bundle.getString(ID_URL);
			instanceServer = bundle.getString(INSTANCE_SERVER);
			orgId = bundle.getString(ORG_ID);
			userId = bundle.getString(USER_ID);
			username = bundle.getString(USERNAME);
			clientId = bundle.getString(CLIENT_ID);
			accountName = bundle.getString(ACCOUNT_NAME);
			communityId = bundle.getString(COMMUNITY_ID);
			communityUrl = bundle.getString(COMMUNITY_URL);
		}
	}

	/**
	 * Returns the auth token for this user account.
	 *
	 * @return Auth token.
	 */
	public String getAuthToken() {
		return authToken;
	}

	/**
	 * Returns the refresh token for this user account.
	 *
	 * @return Refresh token.
	 */
	public String getRefreshToken() {
		return refreshToken;
	}

	/**
	 * Returns the login server for this user account.
	 *
	 * @return Login server.
	 */
	public String getLoginServer() {
		return loginServer;
	}

	/**
	 * Returns the identity URL for this user account.
	 *
	 * @return Identity URL.
	 */
	public String getIdUrl() {
		return idUrl;
	}

	/**
	 * Returns the instance server for this user account.
	 *
	 * @return Instance server.
	 */
	public String getInstanceServer() {
		return instanceServer;
	}

	/**
	 * Returns the org ID for this user account.
	 *
	 * @return Org ID.
	 */
	public String getOrgId() {
		return orgId;
	}

	/**
	 * Returns the user ID for this user account.
	 *
	 * @return User ID.
	 */
	public String getUserId() {
		return userId;
	}

	/**
	 * Returns the username for this user account.
	 *
	 * @return Username.
	 */
	public String getUsername() {
		return username;
	}

	/**
	 * Returns the account name for this user account.
	 *
	 * @return Account name.
	 */
	public String getAccountName() {
		return accountName;
	}

	/**
	 * Returns the client ID for this user account.
	 *
	 * @return Client ID.
	 */
	public String getClientId() {
		return clientId;
	}

	/**
	 * Returns the community ID for this user account.
	 *
	 * @return Community ID.
	 */
	public String getCommunityId() {
		return communityId;
	}

	/**
	 * Returns the community URL for this user account.
	 *
	 * @return Community URL.
	 */
	public String getCommunityUrl() {
		return communityUrl;
	}

	/**
	 * Returns the org level storage path for this user account, relative to
	 * the higher level directory of app data. The higher level directory
	 * could be 'files'. The output is of the format '/{orgID}/'.
	 * This storage path is meant for data that can be shared
	 * across multiple users of the same org.
	 *
	 * @return File storage path.
	 */
	public String getOrgLevelStoragePath() {
		final StringBuffer sb = new StringBuffer(FORWARD_SLASH);
		sb.append(orgId);
		sb.append(FORWARD_SLASH);
		return sb.toString();
	}

	/**
	 * Returns the user level storage path for this user account, relative to
	 * the higher level directory of app data. The higher level directory
	 * could be 'files'. The output is of the format '/{orgID}/{userId}/'.
	 * This storage path is meant for data that is unique to a particular
	 * user in an org, but common across all the communities that the
	 * user is a member of within that org.
	 *
	 * @return File storage path.
	 */
	public String getUserLevelStoragePath() {
		final StringBuffer sb = new StringBuffer(FORWARD_SLASH);
		sb.append(orgId);
		sb.append(FORWARD_SLASH);
		sb.append(userId);
		sb.append(FORWARD_SLASH);
		return sb.toString();
	}

	/**
	 * Returns the storage path for this user account, relative to the higher
	 * level directory of app data. The higher level directory could be 'files'.
	 * The output is of the format '/{orgID}/{userID}/{communityID}/'.
	 * If 'communityID' is null or the internal community ID, then the output
	 * would be '/{orgID}/{userID}/internal/'. This storage path is meant for
	 * data that is unique to a particular user in a specific community.
	 *
	 * @return File storage path.
	 */
	public String getCommunityLevelStoragePath() {
		String leafDir = INTERNAL_COMMUNITY_PATH;
		if (!TextUtils.isEmpty(communityId) && !communityId.equals(INTERNAL_COMMUNITY_ID)) {
			leafDir = communityId;
		}
		return getCommunityLevelStoragePath(leafDir);
	}

	/**
	 * Returns the storage path for this user account, relative to the higher
	 * level directory of app data. The higher level directory could be 'files'.
	 * The output is of the format '/{orgID}/{userID}/{communityID}/'.
	 * If 'communityID' is null or the internal community ID, then the output
	 * would be '/{orgID}/{userID}/internal/'. This storage path is meant for
	 * data that is unique to a particular user in a specific community.
	 *
	 * @param communityId Community ID. Pass 'null' for internal community.
	 * @return File storage path.
	 */
	public String getCommunityLevelStoragePath(String communityId) {
		final StringBuffer sb = new StringBuffer(FORWARD_SLASH);
		sb.append(orgId);
		sb.append(FORWARD_SLASH);
		sb.append(userId);
		sb.append(FORWARD_SLASH);
		String leafDir = INTERNAL_COMMUNITY_PATH;
		if (!TextUtils.isEmpty(communityId) && !communityId.equals(INTERNAL_COMMUNITY_ID)) {
			leafDir = communityId;
		}
		sb.append(leafDir);
		sb.append(FORWARD_SLASH);
		return sb.toString();
	}

	/**
	 * Returns a unique suffix for this user account, that can be appended
	 * to a file to uniquely identify this account, at an org level.
	 * The output is of the format '_{orgID}'. This suffix is meant
	 * for data that can be shared across multiple users of the same org.
	 *
	 * @return Filename suffix.
	 */
	public String getOrgLevelFilenameSuffix() {
		final StringBuffer sb = new StringBuffer(UNDERSCORE);
		sb.append(orgId);
		return sb.toString();
	}

	/**
	 * Returns a unique suffix for this user account, that can be appended
	 * to a file to uniquely identify this account, at a user level.
	 * The output is of the format '_{orgID}_{userID}'. This suffix
	 * is meant for data that is unique to a particular user in an org,
	 * but common across all the communities that the user is a member
	 * of within that org.
	 *
	 * @return Filename suffix.
	 */
	public String getUserLevelFilenameSuffix() {
		final StringBuffer sb = new StringBuffer(UNDERSCORE);
		sb.append(orgId);
		sb.append(UNDERSCORE);
		sb.append(userId);
		return sb.toString();
	}

	/**
	 * Returns a unique suffix for this user account, that can be appended
	 * to a file to uniquely identify this account, at a community level.
	 * The output is of the format '_{orgID}_{userID}_{communityID}'.
	 * If 'communityID' is null or the internal community ID, then the output
	 * would be '_{orgID}_{userID}_internal'. This storage path is meant for
	 * data that is unique to a particular user in a specific community.
	 *
	 * @return Filename suffix.
	 */
	public String getCommunityLevelFilenameSuffix() {
		String leafDir = INTERNAL_COMMUNITY_PATH;
		if (!TextUtils.isEmpty(communityId) && !communityId.equals(INTERNAL_COMMUNITY_ID)) {
			leafDir = communityId;
		}
		return getCommunityLevelFilenameSuffix(leafDir);
	}

	/**
	 * Returns a unique suffix for this user account, that can be appended
	 * to a file to uniquely identify this account, at a community level.
	 * The output is of the format '_{orgID}_{userID}_{communityID}'.
	 * If 'communityID' is null or the internal community ID, then the output
	 * would be '_{orgID}_{userID}_internal'. This storage path is meant for
	 * data that is unique to a particular user in a specific community.
	 *
	 * @param communityId Community ID. Pass 'null' for internal community.
	 * @return Filename suffix.
	 */
	public String getCommunityLevelFilenameSuffix(String communityId) {
		final StringBuffer sb = new StringBuffer(UNDERSCORE);
		sb.append(orgId);
		sb.append(UNDERSCORE);
		sb.append(userId);
		sb.append(UNDERSCORE);
		String leafDir = INTERNAL_COMMUNITY_PATH;
		if (!TextUtils.isEmpty(communityId) && !communityId.equals(INTERNAL_COMMUNITY_ID)) {
			leafDir = communityId;
		}
		sb.append(leafDir);
		return sb.toString();
	}

    @Override
    public boolean equals(Object object) {
        if (object == null || !(object instanceof UserAccount)) {
            return false;
        }
        final UserAccount userAccount = (UserAccount) object;
        if (userId == null || orgId == null || userAccount.getUserId() == null
        		|| userAccount.getOrgId() == null) {
        	return false;
        }
        if (userAccount.getUserId().equals(userId) && userAccount.getOrgId().equals(orgId)) {
        	return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        int result = userId.hashCode();
        result ^= orgId.hashCode() + result * 37;
        return result;
    }

    /**
     * Returns a JSON representation of this instance.
     *
     * @return JSONObject instance.
     */
    public JSONObject toJson() {
    	final JSONObject object = new JSONObject();
    	try {
        	object.put(AUTH_TOKEN, authToken);
        	object.put(REFRESH_TOKEN, refreshToken);
        	object.put(LOGIN_SERVER, loginServer);
        	object.put(ID_URL, idUrl);
        	object.put(INSTANCE_SERVER, instanceServer);
        	object.put(ORG_ID, orgId);
        	object.put(USER_ID, userId);
        	object.put(USERNAME, username);
        	object.put(CLIENT_ID, clientId);
        	object.put(COMMUNITY_ID, communityId);
        	object.put(COMMUNITY_URL, communityUrl);
    	} catch (JSONException e) {
    		Log.e(TAG, "Unable to convert to JSON");
    	}
    	return object;
    }

    /**
     * Returns a representation of this instance in a bundle.
     *
     * @return Bundle instance.
     */
    public Bundle toBundle() {
    	final Bundle object = new Bundle();
        object.putString(AUTH_TOKEN, authToken);
        object.putString(REFRESH_TOKEN, refreshToken);
        object.putString(LOGIN_SERVER, loginServer);
        object.putString(ID_URL, idUrl);
        object.putString(INSTANCE_SERVER, instanceServer);
        object.putString(ORG_ID, orgId);
        object.putString(USER_ID, userId);
        object.putString(USERNAME, username);
        object.putString(CLIENT_ID, clientId);
        object.putString(ACCOUNT_NAME, accountName);
        object.putString(COMMUNITY_ID, communityId);
        object.putString(COMMUNITY_URL, communityUrl);
    	return object;
    }
}
