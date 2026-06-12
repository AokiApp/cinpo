package app.aoki.cinpo.config;

import java.util.Arrays;
import java.util.Objects;

/**
 * Individual applet entry within a Java Card package.
 *
 * @param id          Applet identifier used by CINPO task configuration
 * @param className   Fully-qualified applet class name
 * @param classAid    Applet class AID
 * @param instanceAid Applet instance AID (for installation)
 * @param privileges  Applet privileges byte array
 */
public record AppletEntry(
        String id,
        String className,
        byte[] classAid,
        byte[] instanceAid,
        byte[] privileges
) {
    public AppletEntry {
        Objects.requireNonNull(id);
        Objects.requireNonNull(className);
        Objects.requireNonNull(classAid);
        Objects.requireNonNull(instanceAid);
        Objects.requireNonNull(privileges);

        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (className.isBlank()) {
            throw new IllegalArgumentException("className must not be blank");
        }
        if (classAid.length == 0) {
            throw new IllegalArgumentException("classAid must not be empty");
        }
        if (instanceAid.length == 0) {
            throw new IllegalArgumentException("instanceAid must not be empty");
        }
        if (privileges.length == 0) {
            throw new IllegalArgumentException("privileges must not be empty");
        }

        // Defensive copies
        classAid = Arrays.copyOf(classAid, classAid.length);
        instanceAid = Arrays.copyOf(instanceAid, instanceAid.length);
        privileges = Arrays.copyOf(privileges, privileges.length);
    }

    /**
     * Returns a defensive copy of the class AID.
     */
    @Override
    public byte[] classAid() {
        return Arrays.copyOf(classAid, classAid.length);
    }

    /**
     * Returns a defensive copy of the instance AID.
     */
    @Override
    public byte[] instanceAid() {
        return Arrays.copyOf(instanceAid, instanceAid.length);
    }

    /**
     * Returns a defensive copy of the privileges.
     */
    @Override
    public byte[] privileges() {
        return Arrays.copyOf(privileges, privileges.length);
    }

}
