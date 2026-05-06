package io.justtrack.proGuardGenerator;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.type.TypeParameter;

import javax.annotation.Nonnull;
import java.util.Optional;

interface ClassContext {
    boolean isPublic();

    boolean isInterface();

    @Nonnull
    NodeList<TypeParameter> getTypeParameters();

    @Nonnull
    NodeList<BodyDeclaration<?>> getMembers();

    @Nonnull
    String getNameAsString();

    @Nonnull
    Optional<String> getFullyQualifiedName();

    @Nonnull
    ClassType getClassType();
}
