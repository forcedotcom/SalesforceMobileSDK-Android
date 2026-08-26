/*
 * Copyright (c) 2018-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.app;

/**
 * Flags for ftr_ field in user agent
 */
public class Features {
    public static final String FEATURE_AILTN_ENABLED = "AI";
    public static final String FEATURE_APP_IS_IDP = "IP";
    public static final String FEATURE_APP_IS_SP = "SP";
    public static final String FEATURE_BROWSER_LOGIN = "BW";
    public static final String FEATURE_CERT_AUTH = "CT";
    public static final String FEATURE_LOCALHOST = "LH";
    public static final String FEATURE_MDM = "MM";
    public static final String FEATURE_MULTI_USERS = "MU";
    public static final String FEATURE_PUSH_NOTIFICATIONS = "PN";
    public static final String FEATURE_USER_AUTH = "UA";
    public static final String FEATURE_SCREEN_LOCK = "SL";
    public static final String FEATURE_BIOMETRIC_AUTH = "BA";
    public static final String FEATURE_NATIVE_LOGIN = "NL";
    public static final String FEATURE_QR_CODE_LOGIN = "QR";
    public static final String FEATURE_WELCOME_DISCOVERY_LOGIN = "WD";
    public static final String FEATURE_RTR = "RT";
    public static final String FEATURE_DPOP = "DP";
    public static final String FEATURE_APP_ATTESTATION = "AA";

    // "Why browser login was used" — registered per-user alongside FEATURE_BROWSER_LOGIN (BW)
    public static final String FEATURE_BROWSER_LOGIN_SERVER_AUTH_CONFIG = "B1";
    public static final String FEATURE_BROWSER_LOGIN_MDM               = "B2";
    public static final String FEATURE_BROWSER_LOGIN_FOR_ADMIN         = "B3";
    public static final String FEATURE_BROWSER_LOGIN_FORCE_FLAG        = "B4";

    // "Which login server type" — registered per-user on every auth-flow completion
    public static final String FEATURE_LOGIN_SERVER_PRODUCTION         = "L1";
    public static final String FEATURE_LOGIN_SERVER_SANDBOX            = "L2";
    public static final String FEATURE_LOGIN_SERVER_WELCOME_DISCOVERY  = "L3";
    public static final String FEATURE_LOGIN_SERVER_MY_DOMAIN          = "L4";
    public static final String FEATURE_LOGIN_SERVER_OTHER              = "L5";

    // "Which auth flow type" — registered as transient global, promoted per-user on auth completion
    public static final String FEATURE_AUTH_TYPE_WEB_SERVER_NON_HYBRID = "A1";
    public static final String FEATURE_AUTH_TYPE_WEB_SERVER_HYBRID     = "A2";
    public static final String FEATURE_AUTH_TYPE_USER_AGENT_NON_HYBRID = "A3";
    public static final String FEATURE_AUTH_TYPE_USER_AGENT_HYBRID     = "A4";
    public static final String FEATURE_AUTH_TYPE_NATIVE                = "A5";

    // Token lifecycle markers — registered per-user on auth completion
    public static final String FEATURE_TOKEN_MIGRATION                 = "TM";
    public static final String FEATURE_TOKEN_FORMAT_JWT                = "JT";
    public static final String FEATURE_TOKEN_FORMAT_OPAQUE             = "OT";
    public static final String FEATURE_BEACON                          = "BN";
}
