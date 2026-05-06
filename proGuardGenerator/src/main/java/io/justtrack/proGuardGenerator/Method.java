package io.justtrack.proGuardGenerator;

import java.util.List;

import javax.annotation.Nonnull;

class Method {
    final boolean isStatic;
    @Nonnull
    final TypeName returnType;
    @Nonnull
    final String name;
    @Nonnull
    final List<TypeName> parameters;

    Method(boolean isStatic, @Nonnull TypeName returnType, @Nonnull String name, @Nonnull List<TypeName> parameters) {
        this.isStatic = isStatic;
        this.returnType = returnType;
        this.name = name;
        this.parameters = parameters;
    }
}
