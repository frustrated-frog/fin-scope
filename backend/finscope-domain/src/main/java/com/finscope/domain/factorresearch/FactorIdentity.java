package com.finscope.domain.factorresearch;

import java.util.Objects;

/**
 * 因子的稳定版本身份。
 */
public final class FactorIdentity {
    private final String namespace;
    private final String code;
    private final String version;

    public FactorIdentity(String namespace, String code, String version) {
        this.namespace = required(namespace, "namespace");
        this.code = required(code, "code");
        this.version = required(version, "version");
    }

    public String getNamespace() {
        return namespace;
    }

    public String getCode() {
        return code;
    }

    public String getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) {
            return true;
        }
        if (!(value instanceof FactorIdentity)) {
            return false;
        }
        FactorIdentity that = (FactorIdentity) value;
        return namespace.equals(that.namespace)
                && code.equals(that.code)
                && version.equals(that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, code, version);
    }

    @Override
    public String toString() {
        return namespace + ":" + code + ":" + version;
    }

    private static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
