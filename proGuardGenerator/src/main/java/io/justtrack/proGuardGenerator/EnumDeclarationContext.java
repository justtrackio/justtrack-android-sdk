package io.justtrack.proGuardGenerator;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.type.TypeParameter;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.Optional;

class EnumDeclarationContext implements ClassContext {
    @Nonnull
    private final EnumDeclaration declaration;

    EnumDeclarationContext(@Nonnull EnumDeclaration declaration) {
        this.declaration = declaration;
    }

    @Override
    public boolean isPublic() {
        return declaration.isPublic();
    }

    @Override
    public boolean isInterface() {
        return false;
    }

    @Nonnull
    @Override
    public NodeList<TypeParameter> getTypeParameters() {
        return new NodeList<>();
    }

    @Nonnull
    @Override
    public NodeList<BodyDeclaration<?>> getMembers() {
        return declaration.getMembers();
    }

    @Nonnull
    @Override
    public String getNameAsString() {
        return declaration.getNameAsString();
    }

    @Nonnull
    @Override
    public Optional<String> getFullyQualifiedName() {
        return declaration.getFullyQualifiedName();
    }

    @Nonnull
    @Override
    public ClassType getClassType() {
        return ClassType.from(declaration);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof EnumDeclarationContext)) {
            return false;
        }

        EnumDeclarationContext that = (EnumDeclarationContext) o;

        return Objects.equals(declaration, that.declaration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(declaration);
    }
}
