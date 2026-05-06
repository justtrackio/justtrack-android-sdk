package io.justtrack.proGuardGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import javax.annotation.Nonnull;

class ClassData {
    private final boolean isPublic;
    @Nonnull
    private final ClassType classType;
    @Nonnull
    private final List<Field> fields;
    @Nonnull
    private final List<Constructor> constructors;
    @Nonnull
    private final List<Method> methods;
    private boolean allPublicMethodsExported;

    ClassData(boolean isPublic, @Nonnull ClassType classType) {
        this.isPublic = isPublic;
        this.classType = classType;
        this.fields = new ArrayList<>();
        this.constructors = new ArrayList<>();
        this.methods = new ArrayList<>();
        this.allPublicMethodsExported = true;
    }

    void addField(boolean isStatic, String name, TypeName type) {
        fields.add(new Field(isStatic, name, type));
    }

    void addConstructor(List<TypeName> parameters) {
        constructors.add(new Constructor(parameters));
    }

    void addMethod(boolean isStatic, TypeName returnType, String name, List<TypeName> parameters) {
        methods.add(new Method(isStatic, returnType, name, parameters));
    }

    void setNonExportedPublicMethod() {
        this.allPublicMethodsExported = false;
    }

    public String getProguardDefinition(String className) {
        StringBuilder sb = new StringBuilder();
        sb.append("-keep ");
        if (isPublic) {
            sb.append("public ");
        }
        sb.append(classType.getType()).append(' ').append(className).append(" {\n");
        if (classType == ClassType.ENUM) {
            sb.append("    <fields>;\n");
            sb.append("    #noinspection ShrinkerUnresolvedReference\n");
            sb.append("    public static **[] values();\n");
            sb.append("    public static ** valueOf(java.lang.String);\n");
        }
        writeFields(sb);
        if (allPublicMethodsExported && hasGenericParameter()) {
            writeConstructors(sb, "# ", TypeName::sourceType);
        } else {
            writeConstructors(sb, "  ", TypeName::javaType);
        }
        if (allPublicMethodsExported && hasGenericParameter()) {
            if (!methods.isEmpty() || !constructors.isEmpty()) {
                writeMethods(sb, "# ", TypeName::sourceType);
                sb.append("    public <methods>;\n");
            }
        } else {
            writeMethods(sb, "  ", TypeName::javaType);
        }
        sb.append("}");

        return sb.toString();
    }

    private void writeFields(@Nonnull StringBuilder sb) {
        for (Field f : fields) {
            sb.append("    public ");
            if (f.isStatic) {
                sb.append("static ");
            }
            sb.append(f.type.javaType()).append(' ').append(f.name).append(";\n");
        }
    }

    private void writeConstructors(@Nonnull StringBuilder sb, @Nonnull String prefix, @Nonnull Function<TypeName, String> getTypeName) {
        for (Constructor c : constructors) {
            sb.append(prefix);
            sb.append("  public <init>(");
            for (int i = 0; i < c.parameters.size(); i++) {
                String p = getTypeName.apply(c.parameters.get(i));
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(p);
            }
            sb.append(");\n");
        }
    }

    private void writeMethods(@Nonnull StringBuilder sb, @Nonnull String prefix, @Nonnull Function<TypeName, String> getTypeName) {
        for (Method m : methods) {
            sb.append(prefix);
            sb.append("  public ");
            if (m.isStatic) {
                sb.append("static ");
            }
            sb.append(m.returnType.javaType()).append(' ').append(m.name).append('(');
            for (int i = 0; i < m.parameters.size(); i++) {
                String p = getTypeName.apply(m.parameters.get(i));
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(p);
            }
            sb.append(");\n");
        }
    }

    private boolean hasGenericParameter() {
        for (Constructor constructor : constructors) {
            if (hasGenericParameter(constructor.parameters)) {
                return true;
            }
        }

        for (Method method : methods) {
            if (hasGenericParameter(method.parameters)) {
                return true;
            }

            if (method.returnType.isTypeVariable) {
                return true;
            }
        }

        return false;
    }

    private boolean hasGenericParameter(@Nonnull Iterable<TypeName> parameters) {
        for (TypeName parameter : parameters) {
            if (parameter.isTypeVariable) {
                return true;
            }
        }

        return false;
    }
}
