package io.justtrack.proGuardGenerator;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.type.TypeParameter;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.Optional;

class ClassDeclarationContext implements ClassContext {
    @Nonnull
    private final ClassOrInterfaceDeclaration declaration;

    ClassDeclarationContext(@Nonnull ClassOrInterfaceDeclaration declaration) {
        this.declaration = declaration;
    }

    @Override
    public boolean isPublic() {
        return declaration.isPublic();
    }

    @Override
    public boolean isInterface() {
        return declaration.isInterface();
    }

    @Nonnull
    @Override
    public NodeList<TypeParameter> getTypeParameters() {
        return declaration.getTypeParameters();
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
        if (!(o instanceof ClassDeclarationContext)) {
            return false;
        }

        ClassDeclarationContext that = (ClassDeclarationContext) o;

        return Objects.equals(declaration, that.declaration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(declaration);
    }
}
