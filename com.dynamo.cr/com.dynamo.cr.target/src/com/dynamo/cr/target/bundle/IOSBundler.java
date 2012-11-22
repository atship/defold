package com.dynamo.cr.target.bundle;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.configuration.plist.XMLPropertyListConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dynamo.cr.common.util.Exec;
import com.dynamo.cr.editor.core.ProjectProperties;
import com.dynamo.cr.target.core.TargetPlugin;

public class IOSBundler {
    private static Logger logger = LoggerFactory.getLogger(IOSBundler.class);
    private ProjectProperties projectProperties;
    private String exe;
    private String projectRoot;
    private String contentRoot;
    private String title;
    private File appDir;
    private String identity;
    private String provisioningProfile;
    private File packageDir;

    private static List<PropertyAlias> propertyAliases = new ArrayList<IOSBundler.PropertyAlias>();

    static class PropertyAlias {
        String bundleProperty;
        String category;
        String key;
        String defaultValue;

        PropertyAlias(String bundleProperty, String category, String key,
                String defaultValue) {
            this.bundleProperty = bundleProperty;
            this.category = category;
            this.key = key;
            this.defaultValue = defaultValue;
        }
    }

    private static void addProperty(String bundleProperty, String category,
            String key, String defaultValue) {
        PropertyAlias alias = new PropertyAlias(bundleProperty, category, key,
                defaultValue);
        propertyAliases.add(alias);
    }

    static {
        addProperty("CFBundleIdentifier", "ios", "bundle_identifier", "Unnamed");
        addProperty("CFBundleShortVersionString", "project", "version", "1.0");
    }

    private void copyIcon(String name, String outName)
            throws IOException {
        String resource = projectProperties.getStringValue("ios", name);
        if (resource != null && resource.length() > 0) {
            File inFile = new File(projectRoot, resource);
            File outFile = new File(appDir, outName);
            FileUtils.copyFile(inFile, outFile);
        }
    }

    /**
     *
     * @param projectProperties
     *            corresponding game.project file
     * @param exe
     *            path to executable
     * @param projectRoot
     *            project root
     * @param contentRoot
     *            path to *compiled* content
     * @param outputDir
     *            output directory
     */
    public IOSBundler(String identity, String provisioningProfile,
            ProjectProperties projectProperties, String exe,
            String projectRoot, String contentRoot, String outputDir) {
        this.identity = identity;
        this.provisioningProfile = provisioningProfile;
        this.projectProperties = projectProperties;
        this.exe = exe;
        this.projectRoot = projectRoot;
        this.contentRoot = contentRoot;

        packageDir = new File(outputDir);
        this.title = projectProperties.getStringValue("project", "title",
                "Unnamed");
        appDir = new File(packageDir, title + ".app");
    }

    public void bundleApplication() throws IOException, ConfigurationException {
        FileUtils.deleteDirectory(appDir);
        appDir.mkdirs();

        // Copy archive and game.projectc
        for (String name : Arrays.asList("game.projectc", "game.arc")) {
            FileUtils.copyFile(new File(contentRoot, name), new File(appDir, name));
        }

        // Create Info.plist
        InputStream infoIn = getClass().getResourceAsStream(
                "resources/ios/Info.plist");
        XMLPropertyListConfiguration info = new XMLPropertyListConfiguration();
        info.load(infoIn);
        infoIn.close();

        // Set properties from project file
        for (PropertyAlias alias : propertyAliases) {
            String value = projectProperties.getStringValue(alias.category, alias.key, alias.defaultValue);
            info.setProperty(alias.bundleProperty, value);
        }
        info.setProperty("CFBundleDisplayName", title);
        info.setProperty("CFBundleExecutable", FilenameUtils.getName(exe));

        // Copy ResourceRules.plist
        InputStream resourceRulesIn = getClass().getResourceAsStream(
                "resources/ios/ResourceRules.plist");
        File resourceRulesOutFile = new File(appDir, "ResourceRules.plist");
        FileUtils.copyInputStreamToFile(resourceRulesIn, resourceRulesOutFile);
        resourceRulesIn.close();

        // Copy icons
        copyIcon("app_icon_57x57", "ios_icon_57.png");
        copyIcon("app_icon_114x114", "ios_icon_114.png");
        copyIcon("app_icon_72x72", "ios_icon_72.png");
        copyIcon("app_icon_144x144", "ios_icon_144.png");

        // Copy launch images
        copyIcon("launch_image_320x480", "Default.png");
        copyIcon("launch_image_640x960", "Default@2x.png");
        copyIcon("launch_image_640x1136", "Default-568h@2x.png");
        copyIcon("launch_image_768x1004", "Default-Portrait~ipad.png");
        copyIcon("launch_image_1536x2008", "Default-Portrait@2x~ipad.png");
        copyIcon("launch_image_1024x748", "Default-Landscape~ipad.png");
        copyIcon("launch_image_2048x1496", "Default-Landscape@2x~ipad.png");

        SubnodeConfiguration primaryIcon = info.configurationAt(
                "CFBundleIcons", true).configurationAt("CFBundlePrimaryIcon",
                true);

        // NOTE: We don't set CFBundleIconFiles here
        // Instead we copy icons to pre-set names, ios_icon_X.png, due to a bug
        // in XMLPropertyListConfiguration
        // see https://issues.apache.org/jira/browse/CONFIGURATION-427?page=com.atlassian.jira.plugin.system.issuetabpanels:all-tabpanel
        primaryIcon.setProperty("UIPrerenderedIcon", projectProperties
                .getBooleanValue("ios", "pre_renderered_icons", false));

        // Save updated Info.plist
        File infoFile = new File(appDir, "Info.plist");
        info.save(infoFile);

        // Copy Provisioning Profile
        FileUtils.copyFile(new File(provisioningProfile), new File(appDir,
                "embedded.mobileprovision"));

        // Copy Executable
        FileUtils.copyFile(new File(exe),
                new File(appDir, FilenameUtils.getName(exe)));

        // Sign
        if (identity != null && provisioningProfile != null) {
            ProcessBuilder processBuilder = new ProcessBuilder("codesign",
                    "-f", "-s", identity, "--resource-rules="
                            + resourceRulesOutFile.getAbsolutePath(),
                    appDir.getAbsolutePath());
            processBuilder.environment().put("EMBEDDED_PROFILE_NAME",
                    "embedded.mobileprovision");
            processBuilder.environment().put("CODESIGN_ALLOCATE", TargetPlugin.getDefault().getCodeSignAllocatePath());

            Process process = processBuilder.start();

            try {
                InputStream errorIn = process.getErrorStream();
                ByteArrayOutputStream errorOut = new ByteArrayOutputStream();
                IOUtils.copy(errorIn, errorOut);
                errorIn.close();
                String errorMessage = new String(errorOut.toByteArray());

                int ret = process.waitFor();
                if (ret != 0) {
                    logger.error(errorMessage);
                    throw new IOException(errorMessage);
                }
            } catch (InterruptedException e1) {
                throw new RuntimeException(e1);
            }
        }

        File zipFile = new File(this.packageDir, this.title + ".ipa");
        ZipOutputStream zipStream = new ZipOutputStream(new FileOutputStream(zipFile));

        Collection<File> files = FileUtils.listFiles(appDir, null, true);
        String root = FilenameUtils.normalize(packageDir.getPath(), true);
        for (File f : files) {
            String p = FilenameUtils.normalize(f.getPath(), true);
            String rel = p.substring(root.length());

            // NOTE: The path to Payload is relative, i.e. not /Payload
            // If rooted iTunes complains about invalid package
            zipStream.putNextEntry(new ZipEntry("Payload" + rel));

            FileInputStream input = new FileInputStream(f);
            IOUtils.copy(input, zipStream);
            input.close();
            zipStream.closeEntry();
        }

        zipStream.close();
    }

}
