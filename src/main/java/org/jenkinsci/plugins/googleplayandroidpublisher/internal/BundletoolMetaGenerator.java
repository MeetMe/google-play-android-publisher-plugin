package org.jenkinsci.plugins.googleplayandroidpublisher.internal;

import com.android.aapt.Resources;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.BundleInvalidZipException;
import com.android.tools.build.bundletool.model.exceptions.ValidationException;
import com.android.tools.build.bundletool.model.utils.xmlproto.XmlProtoNode;
import com.android.tools.build.bundletool.xml.XPathResolver;
import com.android.tools.build.bundletool.xml.XmlNamespaceContext;
import com.android.tools.build.bundletool.xml.XmlProtoToXmlConverter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.jenkinsci.plugins.googleplayandroidpublisher.BundleMeta;
import org.w3c.dom.Document;

public class BundletoolMetaGenerator implements BundleMetaGenerator {
    @Override
    public BundleMeta getBundleMeta(File aabFile) {
        // See com.android.tools.build.bundletool.commands.DumpManager.printManifest
        BundleModuleName moduleName = BundleModuleName.BASE_MODULE_NAME;
        Path bundlePath = aabFile.toPath();
        ZipPath manifestPath = ZipPath.create(moduleName.getName())
                .resolve(BundleModule.SpecialModuleEntry.ANDROID_MANIFEST.getPath());
        XmlProtoNode manifestProto = new XmlProtoNode(
                extractAndParse(bundlePath, manifestPath, Resources.XmlNode::parseFrom));
        Document document = XmlProtoToXmlConverter.convert(manifestProto);

        XPath xPath = XPathFactory.newInstance().newXPath();
        xPath.setNamespaceContext(new XmlNamespaceContext(manifestProto));

        long versionCode = getVersionCodeFrom(document, xPath);
        String packageName = getPackageName(document, xPath);
        return new BundleMeta(packageName, versionCode);
    }

    /* Sample output from bundletool:
     * $ java -jar ~/path/to/bundletool-all-0.10.2.jar dump manifest \
     *     --bundle=../path/to/app.aab \
     *     --xpath="/manifest/@package | /manifest/@*[namespace-uri() = 'http://schemas.android.com/apk/res/android' and local-name() = 'versionCode']"
     * "1234"
     * "org.jenkins.appId"
     */

    private static final String XPATH_PACKAGE_NAME = "/manifest/@package";
    private static final String XPATH_VERSION_CODE = "/manifest/@*" +
            // /@android:versionCode
            "[namespace-uri() = 'http://schemas.android.com/apk/res/android' and local-name() = 'versionCode']";

    private static String getPackageName(Document document, XPath xPath) {
        return getXpath(document, xPath, XPATH_PACKAGE_NAME);
    }

    private static long getVersionCodeFrom(Document document, XPath xPath) {
        String result = getXpath(document, xPath, XPATH_VERSION_CODE);
        return Long.parseLong(result);
    }

    private static String getXpath(Document document, XPath xPath, String xpathExpression) {
        try {
            XPathExpression compiledXPathExpression = xPath.compile(xpathExpression);
            XPathResolver.XPathResult xPathResult = XPathResolver.resolve(document, compiledXPathExpression);
            // TODO: no way to get the actual nodes?
            return xPathResult.toString();
        } catch (XPathExpressionException exc) {
            // FIXME: better error
            throw new RuntimeException(exc);
        }
    }

    private static <T> T extractAndParse(Path bundlePath, ZipPath filePath, ProtoParser<T> protoParser) {
        try {
            ZipFile zipFile = new ZipFile(bundlePath.toFile());
            return extractAndParse(zipFile, filePath, protoParser);
        } catch (ZipException exc) {
            throw new BundleInvalidZipException(exc);
        } catch (IOException exc) {
            throw new UncheckedIOException("Error occurred when trying to open the bundle.", exc);
        }
    }

    private static <T> T extractAndParse(ZipFile zipFile, ZipPath filePath, ProtoParser<T> protoParser) {
        ZipEntry fileEntry = zipFile.getEntry(filePath.toString());
        if (fileEntry == null) {
            throw ValidationException.builder().withMessage("File '%s' not found.", new Object[]{filePath}).build();
        } else {
            try {
                InputStream inputStream = zipFile.getInputStream(fileEntry);
                return protoParser.parse(inputStream);
            } catch (IOException e) {
                throw ValidationException.builder()
                        .withMessage("Error occurred when trying to read file '%s' from bundle.", filePath)
                        .withCause(e)
                        .build();
            }
        }
    }

    private interface ProtoParser<T> {
        T parse(InputStream var1) throws IOException;
    }
}
