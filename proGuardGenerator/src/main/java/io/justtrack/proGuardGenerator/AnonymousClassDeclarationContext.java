package io.justtrack.proGuardGenerator;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.TypeParameter;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Optional;

class AnonymousClassDeclarationContext implements ClassContext {
    @Nonnull
    private final List<ClassContext> parentClasses;
    private final int index;
    @Nonnull
    private final ClassOrInterfaceType type;
    @Nonnull
    private final NodeList<BodyDeclaration<?>> body;

    AnonymousClassDeclarationContext(@Nonnull List<ClassContext> parentClasses, int index, @Nonnull ClassOrInterfaceType type, @Nonnull NodeList<BodyDeclaration<?>> body) {
        this.parentClasses = parentClasses;
        this.index = index;
        this.type = type;
        this.body = body;
    }

    @Override
    public boolean isPublic() {
        return false;
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
        return body;
    }

    @Nonnull
    @Override
    public String getNameAsString() {
        StringBuilder s = new StringBuilder();

        for (ClassContext parentClass : parentClasses) {
            s.append(parentClass.getNameAsString()).append('$');
        }

        s.append(index);

        return s.toString();
    }

    @Nonnull
    @Override
    public Optional<String> getFullyQualifiedName() {
        return Optional.empty();
    }

    @Nonnull
    @Override
    public ClassType getClassType() {
        return ClassType.CLASS;
    }
}
