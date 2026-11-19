package com.sun.javacard.cinpo;

import java.nio.file.Path;
import java.util.Objects;

/** Result of wrapperless classic CAP generation. */
public record OracleClassicCapResult(Path capFile, Path stagedCapFile) {
    public OracleClassicCapResult {
        Objects.requireNonNull(capFile, "capFile");
        Objects.requireNonNull(stagedCapFile, "stagedCapFile");
    }
}
