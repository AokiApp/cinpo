package app.aoki.cinpo.gp.cap;

import app.aoki.cinpo.util.Util;
import java.util.Arrays;
import java.util.List;

/**
 * Immutable value object holding all data derived from a parsed CAP archive.
 *
 * <p>A CAP file is a ZIP archive containing Java Card VM component files, prepared for
 * GlobalPlatform LOAD commands per GlobalPlatform Card Specification (GPCS) §11.6.
 *
 * @param packageAid   AID of the Java Card package, extracted from {@code Header.cap}
 *                     (field {@code package_info.aid}); {@code null} if parsing failed.
 * @param majorVersion Major version of the package (from {@code Header.cap}
 *                     {@code package_info.major_version}); {@code -1} if unavailable.
 * @param minorVersion Minor version of the package (from {@code Header.cap}
 *                     {@code package_info.minor_version}); {@code -1} if unavailable.
 * @param appletAids   List of applet AIDs declared in {@code Applet.cap}; empty if
 *                     the component is absent or could not be parsed.
 * @param loadFileData The complete {@code 'C4'}-wrapped Load File Data Block bytes,
 *                     per GPCS §11.6.2.3, Table 11-58. This is the full payload before
 *                     segmentation.
 * @param loadBlocks   The {@code loadFileData} split into fixed-size chunks ready
 *                     to transmit as individual GP LOAD commands (GPCS §11.6.1).
 */
public record CapPackage(
        byte[] packageAid,
        int majorVersion,
        int minorVersion,
        List<byte[]> appletAids,
        byte[] loadFileData,
        List<byte[]> loadBlocks) {

    /**
     * Compact canonical constructor with defensive copying.
     */
    public CapPackage {
        packageAid = packageAid == null ? null : Arrays.copyOf(packageAid, packageAid.length);
        appletAids = Util.copyByteArrayList(appletAids);
        loadFileData = loadFileData == null ? null : Arrays.copyOf(loadFileData, loadFileData.length);
        loadBlocks = Util.copyByteArrayList(loadBlocks);
    }

    /**
     * Returns a defensive copy of the package AID.
     */
    @Override
    public byte[] packageAid() {
        return packageAid == null ? null : Arrays.copyOf(packageAid, packageAid.length);
    }

    /**
     * Returns a defensive copy of the applet AIDs list.
     */
    @Override
    public List<byte[]> appletAids() {
        return Util.copyByteArrayList(appletAids);
    }

    /**
     * Returns a defensive copy of the load file data.
     */
    @Override
    public byte[] loadFileData() {
        return loadFileData == null ? null : Arrays.copyOf(loadFileData, loadFileData.length);
    }

    /**
     * Returns a defensive copy of the load blocks list.
     */
    @Override
    public List<byte[]> loadBlocks() {
        return Util.copyByteArrayList(loadBlocks);
    }

    /**
     * Returns {@code true} if this package has a valid package AID.
     */
    public boolean hasPackageAid() {
        return packageAid != null && packageAid.length > 0;
    }
}
