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

    public boolean isGreaterThan(@NonNull final SdkVersion o) {
        return this.compareTo(o) > 0;
    }

    public boolean isGreaterThanOrEqualTo(@NonNull final SdkVersion o) {
        return this.compareTo(o) >= 0;
    }

    public boolean isLessThan(@NonNull final SdkVersion o) {
        return this.compareTo(o) < 0;
    }

    public boolean isLessThanOrEqualTo(@NonNull final SdkVersion o) {
        return this.compareTo(o) <= 0;
    }

    /**
     * Matches version strings in the form of XX.YY.ZZ[.dev], where each version number can be 1-9 digits long.
     */
    private static final Pattern VERSION_STR_PATTERN = Pattern.compile("^\\d{1,9}\\.\\d{1,9}\\.\\d{1,9}(\\.dev)?$");

    public static SdkVersion parseFromString(@NonNull final String versionStr) throws IllegalArgumentException {
        if (!VERSION_STR_PATTERN.matcher(versionStr).matches()) {
            throw new IllegalArgumentException("Version string \"" + versionStr + "\" did not match expected pattern of XX.YY.ZZ[.dev]");
        }

        final String[] parts = versionStr.split("\\.");
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
        if (this.major < o.major) {
            return -1;
        }
        if (this.major > o.major) {
            return 1;
        }
        if (this.minor < o.minor) {
            return -1;
        }
        if (this.minor > o.minor) {
            return 1;
        }
        if (this.patch < o.patch) {
            return -1;
        }
        if (this.patch > o.patch) {
            return 1;
        }

        // TODO Is 1.1.1.dev before or after 1.1.1? I'm not sure atm...
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
        return "SdkVersion{" +
                "major=" + major +
                ", minor=" + minor +
                ", patch=" + patch +
                ", isDev=" + isDev +
                '}';
    }
}
