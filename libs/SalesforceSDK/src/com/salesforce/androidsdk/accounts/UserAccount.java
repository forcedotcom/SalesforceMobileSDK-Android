/*
 * Copyright (c) 2014-present, salesforce.com, inc.
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

import android.app.DownloadManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;

import com.salesforce.androidsdk.app.Features;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.util.MapUtil;
import com.salesforce.androidsdk.util.SalesforceSDKLogger;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.Map;

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
	public static final String COMMUNITY_ID = "communityId";
	public static final String COMMUNITY_URL = "communityUrl";
	public static final String INTERNAL_COMMUNITY_ID = "000000000000000AAA";
	public static final String INTERNAL_COMMUNITY_PATH = "internal";
    public static final String EMAIL = "email";
    public static final String FIRST_NAME = "first_name";
	public static final String DISPLAY_NAME = "display_name";
	public static final String LAST_NAME = "last_name";
    public static final String PHOTO_URL = "photoUrl";
    public static final String THUMBNAIL_URL = "thumbnailUrl";
	public static final String LIGHTNING_DOMAIN = "lightningDomain";
	public static final String LIGHTNING_SID = "lightningSid";
	public static final String VF_DOMAIN = "vfDomain";
	public static final String VF_SID = "vfSid";
	public static final String CONTENT_DOMAIN = "contentDomain";
	public static final String CONTENT_SID = "contentSid";
	public static final String CSRF_TOKEN = "csrfToken";

	private static final String TAG = "UserAccount";
	private static final String FORWARD_SLASH = "/";
	private static final String UNDERSCORE = "_";
	private static final String PROFILE_PHOTO_PATH_PREFIX = "profile_photo_";
    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER = "Bearer ";
    private static final String JPG = ".jpg";

	private String authToken;
	private String refreshToken;
	private String loginServer;
	private String idUrl;
	private String instanceServer;
	private String orgId;
	private String userId;
	private String username;
	private String accountName;
	private String communityId;
	private String communityUrl;
    private String firstName;
    private String lastName;
	private String displayName;
	private String email;
    private String photoUrl;
    private String thumbnailUrl;
	private String lightningDomain;
	private String lightningSid;
	private String vfDomain;
	private String vfSid;
	private String contentDomain;
	private String contentSid;
	private String csrfToken;
    private Map<String, String> additionalOauthValues;

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
	 * @param communityId Community ID.
	 * @param communityUrl Community URL.
	 * @param firstName First Name.
	 * @param lastName Last Name.
	 * @param displayName Display Name.
	 * @param email Email.
	 * @param photoUrl Photo URL.
	 * @param thumbnailUrl Thumbnail URL.
	 * @param additionalOauthValues Additional OAuth values.
	 * @param lightningDomain Lightning domain.
	 * @param lightningSid Lightning SID.
	 * @param vfDomain VF domain.
	 * @param vfSid VF SID.
	 * @param contentDomain Content domain.
	 * @param contentSid Content SID.
	 */
	UserAccount(String authToken, String refreshToken,
					   String loginServer, String idUrl, String instanceServer,
					   String orgId, String userId, String username, String accountName,
					   String communityId, String communityUrl, String firstName, String lastName,
                       String displayName, String email, String photoUrl,
					   String thumbnailUrl, Map<String, String> additionalOauthValues,
					   String lightningDomain, String lightningSid, String vfDomain, String vfSid,
					   String  contentDomain, String contentSid, String csrfToken) {
		this.authToken = authToken;
		this.refreshToken = refreshToken;
		this.loginServer = loginServer;
		this.idUrl = idUrl;
		this.instanceServer = instanceServer;
		this.orgId = orgId;
		this.userId = userId;
		this.username = username;
		this.accountName = accountName;
		this.communityId = communityId;
		this.communityUrl = communityUrl;
		this.firstName = firstName;
		this.lastName = lastName;
		this.displayName = displayName;
		this.email = email;
		this.photoUrl = photoUrl;
		this.thumbnailUrl = thumbnailUrl;
		this.additionalOauthValues = additionalOauthValues;
		this.lightningDomain = lightningDomain;
		this.lightningSid = lightningSid;
		this.vfDomain = vfDomain;
		this.vfSid = vfSid;
		this.contentDomain = contentDomain;
		this.contentSid = contentSid;
		this.csrfToken = csrfToken;
		SalesforceSDKManager.getInstance().registerUsedAppFeature(Features.FEATURE_USER_AUTH);
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
			if (!TextUtils.isEmpty(username) && !TextUtils.isEmpty(instanceServer)) {
				accountName = String.format("%s (%s) (%s)", username, instanceServer,
						SalesforceSDKManager.getInstance().getApplicationName());
			}
			communityId = object.optString(COMMUNITY_ID, null);
			communityUrl = object.optString(COMMUNITY_URL, null);
            firstName = object.optString(FIRST_NAME, null);
            lastName = object.optString(LAST_NAME, null);
			displayName = object.optString(DISPLAY_NAME, null);
			email = object.optString(EMAIL, null);
            photoUrl = object.optString(PHOTO_URL, null);
			thumbnailUrl = object.optString(THUMBNAIL_URL, null);
			lightningDomain = object.optString(LIGHTNING_DOMAIN, null);
			lightningSid = object.optString(LIGHTNING_SID, null);
			vfDomain = object.optString(VF_DOMAIN, null);
			vfSid = object.optString(VF_SID, null);
			contentDomain = object.optString(CONTENT_DOMAIN, null);
			contentSid = object.optString(CONTENT_SID, null);
			csrfToken = object.optString(CSRF_TOKEN, null);
            additionalOauthValues = MapUtil.addJSONObjectToMap(object,
                    SalesforceSDKManager.getInstance().getAdditionalOauthKeys(), additionalOauthValues);
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
			accountName = bundle.getString(ACCOUNT_NAME);
			communityId = bundle.getString(COMMUNITY_ID);
			communityUrl = bundle.getString(COMMUNITY_URL);
            firstName = bundle.getString(FIRST_NAME);
            lastName = bundle.getString(LAST_NAME);
			displayName = bundle.getString(DISPLAY_NAME);
			email = bundle.getString(EMAIL);
            photoUrl = bundle.getString(PHOTO_URL);
            thumbnailUrl = bundle.getString(THUMBNAIL_URL);
			lightningDomain = bundle.getString(LIGHTNING_DOMAIN);
			lightningSid = bundle.getString(LIGHTNING_SID);
			vfDomain = bundle.getString(VF_DOMAIN);
			vfSid = bundle.getString(VF_SID);
			contentDomain = bundle.getString(CONTENT_DOMAIN);
			contentSid = bundle.getString(CONTENT_SID);
			csrfToken = bundle.getString(CSRF_TOKEN);
            additionalOauthValues = MapUtil.addBundleToMap(bundle,
					SalesforceSDKManager.getInstance().getAdditionalOauthKeys(), additionalOauthValues);
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
     * Returns the first name for this user account.
     *
     * @return First Name.
     */
    public String getFirstName() {
        return firstName;
    }

	/**
	 * Returns the Display name for this user account.
	 *
	 * @return Display Name.
	 */
	public String getDisplayName() {
		return displayName;
	}

	/**
     * Returns the last name for this user account.
     *
     * @return Last Name.
     */
    public String getLastName() {
        return lastName;
    }

    /**
     * Returns the email for this user account.
     *
     * @return Email.
     */
    public String getEmail() {
        return email;
    }

    /**
     * Returns the photo url for this user.
     *
     * @return Photo URL.
     */
    public String getPhotoUrl() {
        return photoUrl;
    }

    /**
     * Returns the thumbnail for this user.
     *
     * @return Thumbnail.
     */
    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

	/**
	 * Returns the Lightning domain for this user.
	 *
	 * @return Lightning domain.
	 */
	public String getLightningDomain() {
		return lightningDomain;
	}

	/**
	 * Returns the Lightning SID for this user.
	 *
	 * @return Lightning SID.
	 */
	public String getLightningSid() {
		return lightningSid;
	}

	/**
	 * Returns the VF domain for this user.
	 *
	 * @return VF domain.
	 */
	public String getVFDomain() {
		return vfDomain;
	}

	/**
	 * Returns the VF SID for this user.
	 *
	 * @return VF SID.
	 */
	public String getVFSid() {
		return vfSid;
	}

	/**
	 * Returns the content domain for this user.
	 *
	 * @return Content domain.
	 */
	public String getContentDomain() {
		return contentDomain;
	}

	/**
	 * Returns the content SID for this user.
	 *
	 * @return Content SID.
	 */
	public String getContentSid() {
		return contentSid;
	}

	/**
	 * Returns the CSRF token for this user.
	 *
	 * @return CSRF token.
	 */
	public String getCSRFToken() {
		return csrfToken;
	}

    /**
     * Returns the additional OAuth values for this user.
     *
     * @return Additional OAuth values.
     */
    public Map<String, String> getAdditionalOauthValues() {
        return additionalOauthValues;
    }

    /**
     * Fetches this user's profile photo from the cache.
     *
     * @return User's profile photo.
     */
    public Bitmap getProfilePhoto() {
        final File file = getProfilePhotoFile();
        if (file == null) {
            return null;
        }
        final BitmapFactory.Options bitmapOptions = new BitmapFactory.Options();
        bitmapOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), bitmapOptions);
    }

    /**
     * Fetches this user's profile photo from the server and stores it in the cache.
     */
    public void downloadProfilePhoto() {
        final File file = getProfilePhotoFile();
        if (photoUrl == null || file == null) {
            return;
        }
        final Uri srcUri = Uri.parse(photoUrl);
        final Uri destUri = Uri.fromFile(file);
        if (srcUri == null || destUri == null) {
        	return;
		}

        // Checks if DownloadManager is enabled on the device, to ensure it doesn't crash.
        final PackageManager pm = SalesforceSDKManager.getInstance().getAppContext().getPackageManager();
        int state = pm.getApplicationEnabledSetting("com.android.providers.downloads");
        if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
			final DownloadManager.Request downloadReq = new DownloadManager.Request(srcUri);
			downloadReq.setDestinationUri(destUri);
			downloadReq.addRequestHeader(AUTHORIZATION, BEARER + authToken);
			downloadReq.setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN);
			downloadReq.setVisibleInDownloadsUi(false);
			final DownloadManager downloadManager = (DownloadManager) SalesforceSDKManager.getInstance().getAppContext().getSystemService(Context.DOWNLOAD_SERVICE);
			if (downloadManager != null) {
				downloadManager.enqueue(downloadReq);
			}
		}
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
        if (!(object instanceof UserAccount)) {
            return false;
        }
        final UserAccount userAccount = (UserAccount) object;
        if (userId == null || orgId == null || userAccount.getUserId() == null
        		|| userAccount.getOrgId() == null) {
        	return false;
        }
		return (userAccount.getUserId().equals(userId) && userAccount.getOrgId().equals(orgId));
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
    	JSONObject object = new JSONObject();
    	try {
        	object.put(AUTH_TOKEN, authToken);
        	object.put(REFRESH_TOKEN, refreshToken);
        	object.put(LOGIN_SERVER, loginServer);
        	object.put(ID_URL, idUrl);
        	object.put(INSTANCE_SERVER, instanceServer);
        	object.put(ORG_ID, orgId);
        	object.put(USER_ID, userId);
        	object.put(USERNAME, username);
        	object.put(COMMUNITY_ID, communityId);
        	object.put(COMMUNITY_URL, communityUrl);
            object.put(FIRST_NAME, firstName);
            object.put(LAST_NAME, lastName);
			object.put(DISPLAY_NAME, displayName);
			object.put(EMAIL, email);
            object.put(PHOTO_URL, photoUrl);
            object.put(THUMBNAIL_URL, thumbnailUrl);
            object.put(LIGHTNING_DOMAIN, lightningDomain);
            object.put(LIGHTNING_SID, lightningSid);
            object.put(VF_DOMAIN, vfDomain);
            object.put(VF_SID, vfSid);
            object.put(CONTENT_DOMAIN, contentDomain);
            object.put(CONTENT_SID, contentSid);
            object.put(CSRF_TOKEN, csrfToken);
            object = MapUtil.addMapToJSONObject(additionalOauthValues,
                    SalesforceSDKManager.getInstance().getAdditionalOauthKeys(), object);
    	} catch (JSONException e) {
			SalesforceSDKLogger.e(TAG, "Unable to convert to JSON", e);
    	}
    	return object;
    }

    /**
     * Returns a representation of this instance in a bundle.
     *
     * @return Bundle instance.
     */
    public Bundle toBundle() {
    	Bundle object = new Bundle();
        object.putString(AUTH_TOKEN, authToken);
        object.putString(REFRESH_TOKEN, refreshToken);
        object.putString(LOGIN_SERVER, loginServer);
        object.putString(ID_URL, idUrl);
        object.putString(INSTANCE_SERVER, instanceServer);
        object.putString(ORG_ID, orgId);
        object.putString(USER_ID, userId);
        object.putString(USERNAME, username);
        object.putString(ACCOUNT_NAME, accountName);
        object.putString(COMMUNITY_ID, communityId);
        object.putString(COMMUNITY_URL, communityUrl);
        object.putString(FIRST_NAME, firstName);
        object.putString(LAST_NAME, lastName);
		object.putString(DISPLAY_NAME, displayName);
		object.putString(EMAIL, email);
        object.putString(PHOTO_URL, photoUrl);
        object.putString(THUMBNAIL_URL, thumbnailUrl);
		object.putString(LIGHTNING_DOMAIN, lightningDomain);
		object.putString(LIGHTNING_SID, lightningSid);
		object.putString(VF_DOMAIN, vfDomain);
		object.putString(VF_SID, vfSid);
		object.putString(CONTENT_DOMAIN, contentDomain);
		object.putString(CONTENT_SID, contentSid);
		object.putString(CSRF_TOKEN, csrfToken);
        object = MapUtil.addMapToBundle(additionalOauthValues,
                SalesforceSDKManager.getInstance().getAdditionalOauthKeys(), object);
    	return object;
    }

    private File getProfilePhotoFile() {
        final String filename = PROFILE_PHOTO_PATH_PREFIX + getUserLevelFilenameSuffix() + JPG;
        File baseDir = SalesforceSDKManager.getInstance().getAppContext().getExternalCacheDir();
        return baseDir != null ? new File(baseDir, filename) : null;
    }
}
