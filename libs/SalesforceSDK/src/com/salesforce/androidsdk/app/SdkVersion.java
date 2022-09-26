/*
 * Copyright (c) 2022-present, salesforce.com, inc.
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

import androidx.annotation.NonNull;

import java.util.regex.Pattern;

public final class SdkVersion implements Comparable<SdkVersion> {
    private final int major;
    private final int minor;
    private final int patch;
    private final boolean isDev;

    public SdkVersion(int major, int minor, int patch, boolean isDev) throws IllegalArgumentException {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("Invalid version number combination: major=" + major + ", minor=" + minor + ", patch=" + patch);
        }

        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.isDev = isDev;
    }

    public int getMajor() {
        return major;
    }

    public int getMinor() {
        return minor;
    }

    public int getPatch() {
        return patch;
    }

    public boolean isDev() {
        return isDev;
    }

    public boolean isGreaterThan(final SdkVersion o) {
        return this.compareTo(o) > 0;
    }

    public boolean isGreaterThanOrEqualTo(final SdkVersion o) {
        return this.compareTo(o) >= 0;
    }

    public boolean isLessThan(final SdkVersion o) {
        return this.compareTo(o) < 0;
    }

    public boolean isLessThanOrEqualTo(final SdkVersion o) {
        return this.compareTo(o) <= 0;
    }

    /**
     * Matches version strings in the form of XX.YY.ZZ[.dev], where each version number can be 1-9 digits long.
     */
    private static final Pattern VERSION_STR_PATTERN = Pattern.compile("^\\d{1,9}\\.\\d{1,9}\\.\\d{1,9}(\\.dev)?$");

    public static SdkVersion parseFromString(@NonNull final String versionStr) throws IllegalArgumentException {
        final String trimmed = versionStr.trim();
        if (!VERSION_STR_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Version string \"" + trimmed + "\" did not match expected pattern of XX.YY.ZZ[.dev]");
        }

        final String[] parts = trimmed.split("\\.");
        return new SdkVersion(
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]),
                parts.length == 4 // If we pass regex matching then we know the final part is "dev"
        );
    }

    @Override
    public int compareTo(SdkVersion o) {
        if (o == null) {
            // non-null is before all null values in natural order
            return -1;
        }
        if (o == this) { // reference compare
            return 0;
        }
        if (this.major != o.major) {
            return Integer.compare(this.major, o.major);
        }
        if (this.minor != o.minor) {
            return Integer.compare(this.minor, o.minor);
        }
        if (this.patch != o.patch) {
            return Integer.compare(this.patch, o.patch);
        }
        if (this.isDev && !o.isDev) {
            return -1;
        }
        if (!this.isDev && o.isDev) {
            return 1;
        }

        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SdkVersion that = (SdkVersion) o;

        if (major != that.major) return false;
        if (minor != that.minor) return false;
        if (patch != that.patch) return false;
        return isDev == that.isDev;
    }

    @Override
    public int hashCode() {
        int result = major;
        result = 31 * result + minor;
        result = 31 * result + patch;
        result = 31 * result + (isDev ? 1 : 0);
        return result;
    }

    @NonNull
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder()
                .append(major)
                .append('.')
                .append(minor)
                .append('.')
                .append(patch);
        if (isDev) {
            builder.append(".dev");
        }
        return builder.toString();
    }
}
