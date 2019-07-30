package org.jenkinsci.plugins.googleplayandroidpublisher;

public class BundleMeta {
    public final String packageName;
    public final long versionCode;

    public BundleMeta(String packageName, long versionCode) {
        this.packageName = packageName;
        this.versionCode = versionCode;
    }
}