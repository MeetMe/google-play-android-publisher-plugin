package org.jenkinsci.plugins.googleplayandroidpublisher.internal;

import java.io.File;
import org.jenkinsci.plugins.googleplayandroidpublisher.BundleMeta;

public interface BundleMetaGenerator {
    BundleMeta getBundleMeta(File bundleFile);
}
