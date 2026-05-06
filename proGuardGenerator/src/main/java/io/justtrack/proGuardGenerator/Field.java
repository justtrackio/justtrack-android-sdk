package io.justtrack.proGuardGenerator;

import javax.annotation.Nonnull;

class Field {
    final boolean isStatic;
    @Nonnull
    final String name;
    @Nonnull
    final TypeName type;

    Field(boolean isStatic, @Nonnull String name, @Nonnull TypeName type) {
        this.isStatic = isStatic;
        this.name = name;
        this.type = type;
    }
}
