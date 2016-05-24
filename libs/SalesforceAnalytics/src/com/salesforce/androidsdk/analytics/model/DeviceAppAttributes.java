/*
 * Copyright (c) 2016, salesforce.com, inc.
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
package com.salesforce.androidsdk.analytics.model;

/**
 * Represents common attributes specific to this app and device. These
 * attributes will be appended to the events stored using this library.
 *
 * @author bhariharan
 */
public class DeviceAppAttributes {

    private String appVersion;
    private String appName;
    private String osVersion;
    private String osName;
    private String nativeAppType;
    private String mobileSdkVersion;
    private String deviceModel;
    private String deviceId;
    private String connectionType;

    /**
     * Parameterized constructor.
     *
     * @param appVersion App version.
     * @param appName App name.
     * @param osVersion OS version.
     * @param osName OS name.
     * @param nativeAppType App type.
     * @param mobileSdkVersion Mobile SDK version.
     * @param deviceModel Device model.
     * @param deviceId Device ID.
     * @param connectionType Connection type.
     */
    public DeviceAppAttributes(String appVersion, String appName, String osVersion, String osName,
                               String nativeAppType, String mobileSdkVersion, String deviceModel,
                               String deviceId, String connectionType) {
        this.appVersion = appVersion;
        this.appName = appName;
        this.osVersion = osVersion;
        this.osName = osName;
        this.nativeAppType = nativeAppType;
        this.mobileSdkVersion = mobileSdkVersion;
        this.deviceModel = deviceModel;
        this.deviceId = deviceId;
        this.connectionType = connectionType;
    }

    /**
     * Returns app version.
     *
     * @return App version.
     */
    public String getAppVersion() {
        return appVersion;
    }

    /**
     * Returns app name.
     *
     * @return App name.
     */
    public String getAppName() {
        return appName;
    }

    /**
     * Returns OS version.
     *
     * @return OS version.
     */
    public String getOsVersion() {
        return osVersion;
    }

    /**
     * Returns OS name.
     *
     * @return OS name.
     */
    public String getOsName() {
        return osName;
    }

    /**
     * Returns app type.
     *
     * @return App type.
     */
    public String getNativeAppType() {
        return nativeAppType;
    }

    /**
     * Returns Mobile SDK version.
     *
     * @return Mobile SDK version.
     */
    public String getMobileSdkVersion() {
        return mobileSdkVersion;
    }

    /**
     * Returns device model.
     *
     * @return Device model.
     */
    public String getDeviceModel() {
        return deviceModel;
    }

    /**
     * Returns device ID.
     *
     * @return Device ID.
     */
    public String getDeviceId() {
        return deviceId;
    }

    /**
     * Returns connection type.
     *
     * @return Connection type.
     */
    public String getConnectionType() {
        return connectionType;
    }
}
