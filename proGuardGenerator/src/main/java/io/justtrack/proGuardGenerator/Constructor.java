package io.justtrack.proGuardGenerator;

import java.util.List;

import javax.annotation.Nonnull;

class Constructor {
    @Nonnull
    final List<TypeName> parameters;

    Constructor(@Nonnull List<TypeName> parameters) {
        this.parameters = parameters;
    }
}
