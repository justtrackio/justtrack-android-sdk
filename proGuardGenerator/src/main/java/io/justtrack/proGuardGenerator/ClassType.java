package io.justtrack.proGuardGenerator;

import com.github.javaparser.ast.body.TypeDeclaration;

import javax.annotation.Nonnull;

enum ClassType {
    CLASS("class"),
    INTERFACE("interface"),
    ENUM("enum");

    @Nonnull
    private final String type;

    ClassType(@Nonnull String type) {
        this.type = type;
    }

    @Nonnull
    public String getType() {
        return type;
    }

    @Nonnull
    public static <T extends TypeDeclaration<?>> ClassType from(@Nonnull TypeDeclaration<T> classDeclaration) {
        if (classDeclaration.isClassOrInterfaceDeclaration() && classDeclaration.asClassOrInterfaceDeclaration().isInterface()) {
            return INTERFACE;
        }

        if (classDeclaration.isEnumDeclaration()) {
            return ENUM;
        }

        return CLASS;
    }
}
