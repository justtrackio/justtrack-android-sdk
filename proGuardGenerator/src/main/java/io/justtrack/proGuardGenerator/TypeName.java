package io.justtrack.proGuardGenerator;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

class TypeName {
    @Nonnull
    final String type;
    @Nullable
    final String resolvedType;
    final boolean isTypeVariable;

    TypeName(@Nonnull String type) {
        this(type, null, false);
    }

    TypeName(@Nonnull String type, @Nullable String resolvedType, boolean isTypeVariable) {
        this.type = type;
        this.resolvedType = resolvedType;
        this.isTypeVariable = isTypeVariable;
    }

    @Nonnull
    String sourceType() {
        return type;
    }

    @Nonnull
    String javaType() {
        if (resolvedType != null) {
            return resolvedType;
        }

        return type;
    }

    @Nonnull
    TypeName asArray() {
        return new TypeName(type + "[]", resolvedType == null ? null : resolvedType + "[]", isTypeVariable);
    }
}
