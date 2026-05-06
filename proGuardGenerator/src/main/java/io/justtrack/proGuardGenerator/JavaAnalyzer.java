package io.justtrack.proGuardGenerator;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.TypeParameter;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.utils.SourceRoot;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

/**
 * This class parses a directory containing Java files and can generate a ProGuard file containing all
 * the public methods, fields, and constructors from these files. It is used by our gradle scripts to
 * keep the proguard-rules.pro file of the SDK up-to-date automatically (which also means every public
 * method or interface needs to live in a Java file).
 * <p>
 * Limitations:
 * <ul>
 * <li> We don't support asterisk imports. They are just ignored. This is only needed when
 * <ol type="a">
 * <li> you have an exported method with that type
 * <li> AND your class doesn't export every public method anyway
 * </ol>
 * <li> Type parameters are always resolved to java.lang.Object
 * <li> Inner classes might cause some problems. Try to avoid them in public interfaces or don't nest
 * them more than one level.
 * <li> Any parse error will throw an exception. Other compile errors (like type mismatches, missing
 * methods, etc.) should be fine and will be ignored.
 * </ul>
 * <p>
 * The analyzer currently only looks at public methods (or any method defined in an interface) with
 * the following restrictions:
 * <ul>
 * <li> Methods marked with @Override are ignored as they are implicitly exported anyway.
 * <li> Public methods marked with @VisibleForTesting are ignored as they are not intended for
 * consumption by the user.
 * </ul>
 */
public class JavaAnalyzer {
    @Nonnull
    private final Map<String, ClassData> classes;
    @Nonnull
    private final Set<String> classesWithNonExportedPublicMethods;

    /**
     * Create a fresh analyzer instance which can be used to parse some files.
     */
    public JavaAnalyzer() {
        classes = new HashMap<>();
        classesWithNonExportedPublicMethods = new HashSet<>();
    }

    /**
     * Return the extracted data as a ProGuard definition file.
     *
     * @return A multi-line string containing the formatted ProGuard definitions.
     */
    @Nonnull
    public String getProguardDefinitions() {
        StringBuilder sb = new StringBuilder();

        List<Map.Entry<String, ClassData>> classesByName = new ArrayList<>(classes.entrySet());
        classesByName.sort(Map.Entry.comparingByKey());

        for (Map.Entry<String, ClassData> entry : classesByName) {
            String definition = entry.getValue().getProguardDefinition(entry.getKey());
            sb.append(definition).append('\n');
        }

        return sb.toString();
    }

    /**
     * Parse the files from the given source root. Information for generating a ProGuard file is
     * collected and can be converted to a string with {@link JavaAnalyzer#getProguardDefinitions()}.
     *
     * @param path The relative or absolute path to the dictionary containing your code.
     * @throws IOException           If there is a problem reading the files.
     * @throws ParseProblemException If any parsed file contains a syntax error.
     */
    public void parseSourceRoot(@Nonnull String path) throws IOException {
        ParserConfiguration parserConfiguration = new ParserConfiguration();
        parserConfiguration.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
        
        SourceRoot sourceRoot = new SourceRoot(Paths.get(path), parserConfiguration);
        for (ParseResult<CompilationUnit> parseResult : sourceRoot.tryToParse()) {
            Optional<CompilationUnit> result = parseResult.getResult();
            if (!parseResult.isSuccessful() || result.isEmpty()) {
                throw new IOException("Failed to parse " + path, new ParseProblemException(parseResult.getProblems()));
            }

            CompilationUnit compilationUnit = result.get();
            // walk each node from the compilation unit (read: source file) recursively from outer
            // to inner nodes (so class definitions before method definitions before single lines of
            // code)
            compilationUnit.accept(new ModifierVisitor<>() {
                @Override
                public Visitable visit(@Nonnull PackageDeclaration n, @Nonnull Context context) {
                    // set the new package name should we see a package declaration
                    return super.visit(n, new Context(n.getNameAsString()));
                }

                @Override
                public Node visit(@Nonnull ImportDeclaration n, @Nonnull Context context) {
                    // remember all imports from a file - otherwise we can't resolve classes later.
                    // currently we just don't support asterisk imports (import foo.*)... if you need this,
                    // you have to implement it somehow
                    if (!n.isAsterisk()) {
                        String simpleName = n.getName().getIdentifier();
                        String fullQualifiedName = n.getNameAsString();
                        context.addImport(simpleName, fullQualifiedName);
                    }

                    return super.visit(n, context);
                }

                @Override
                public Visitable visit(@Nonnull EnumDeclaration n, @Nonnull Context context) {
                    // export public enums.
                    if (n.isPublic()) {
                        boolean isHidden = hasAnnotation("VisibleForTesting", n.getAnnotations()) || hasAnnotation("Hidden", n.getAnnotations());
                        if (isHidden) {
                            // mark it as false should we later come back to it
                            n.setPublic(false);
                        } else {
                            // set the class (or interface) to public - might be redundant, but makes some code later simpler
                            n.setPublic(true);
                            getClassData(new EnumDeclarationContext(n), context);
                        }
                    }

                    return super.visit(n, context);
                }

                @Override
                public Visitable visit(@Nonnull ClassOrInterfaceDeclaration n, @Nonnull Context context) {
                    // export public classes and interfaces. If the interface is a member of the current
                    // class, it is implicitly public, too.
                    if (n.isPublic() || (n.isInterface() && context.getActiveClass() != null && context.getActiveClass().isPublic())) {
                        boolean isHidden = hasAnnotation("VisibleForTesting", n.getAnnotations()) || hasAnnotation("Hidden", n.getAnnotations());
                        if (isHidden) {
                            // mark it as false should we later come back to it
                            n.setPublic(false);
                        } else {
                            // set the class (or interface) to public - might be redundant, but makes some code later simpler
                            n.setPublic(true);
                            getClassData(new ClassDeclarationContext(n), context);
                        }
                    }

                    return super.visit(n, new Context(context, n));
                }

                @Override
                public Visitable visit(ObjectCreationExpr n, Context context) {
                    Optional<NodeList<BodyDeclaration<?>>> anonymousClass = n.getAnonymousClassBody();
                    if (anonymousClass.isEmpty()) {
                        return super.visit(n, context);
                    }

                    return super.visit(n, new Context(context, n.getType(), anonymousClass.get()));
                }

                @Override
                public Visitable visit(@Nonnull MethodDeclaration n, @Nonnull Context context) {
                    // either the method is public, or the method is in an interface and therefore should
                    // also be considered exported (on source level, interfaces just don't say their
                    // methods are public)
                    if (context.getActiveClass() != null) {
                        boolean isOverride = hasAnnotation("Override", n.getAnnotations());
                        boolean isHidden = hasAnnotation("VisibleForTesting", n.getAnnotations()) || hasAnnotation("Hidden", n.getAnnotations());
                        if (context.getActiveClass().isInterface() && context.getActiveClass().isPublic()) {
                            //if the class is public interface keep all
                            keepMethod(n, context, context.getActiveClass());
                        } else if (context.getActiveClass().isPublic()) {
                            // public class & interface keep all public method that is not hidden
                            if (!isHidden && n.isPublic()) {
                                keepMethod(n, context, context.getActiveClass());
                            } else if (isHidden) {
                                markNonExportedMethodClass(context, context.getActiveClass());
                            }
                        } else {
                            //non-public class keep only method that is not hidden and not override
                            if (!isOverride && !isHidden && n.isPublic()) {
                                keepMethod(n, context, context.getActiveClass());
                            } else if (isHidden) {
                                markNonExportedMethodClass(context, context.getActiveClass());
                            }
                        }
                    }
                    return super.visit(n, context);
                }

                @Override
                public Visitable visit(@Nonnull ConstructorDeclaration n, @Nonnull Context context) {
                    // export public constructors
                    if (n.isPublic() && context.getActiveClass() != null) {
                        boolean isHidden = hasAnnotation("VisibleForTesting", n.getAnnotations()) || hasAnnotation("Hidden", n.getAnnotations());
                        // only mention methods which are actually exported and don't override anything
                        // (they stay the same anyway)
                        if (isHidden) {
                            // we didn't export the constructor (method), so mark its class as containing non-exported methods
                            String className = context.getClassName(context.getActiveClass());
                            ClassData data = classes.get(className);
                            if (data != null) {
                                data.setNonExportedPublicMethod();
                            } else {
                                // we didn't export something from the class yet (and the class itself is not exported),
                                // so add it to a list of classes with that disabled - getClassData will read that list
                                classesWithNonExportedPublicMethods.add(className);
                            }
                        } else {
                            ClassData data = getClassData(context.getActiveClass(), context);
                            List<TypeName> parameterTypes = getParameters(n.getParameters(), context);
                            data.addConstructor(parameterTypes);
                        }
                    }

                    return super.visit(n, context);
                }

                @Override
                public Visitable visit(@Nonnull FieldDeclaration n, @Nonnull Context context) {
                    // export public fields
                    if (n.isPublic() && context.getActiveClass() != null) {
                        boolean isHidden = hasAnnotation("VisibleForTesting", n.getAnnotations()) || hasAnnotation("Hidden", n.getAnnotations());
                        if (!isHidden) {
                            ClassData data = getClassData(context.getActiveClass(), context);
                            for (VariableDeclarator v : n.getVariables()) {
                                data.addField(n.isStatic(), v.getNameAsString(), typeToString(v.getType(), context));
                            }
                        }
                    }

                    return super.visit(n, context);
                }
            }, new Context(compilationUnit.getPackageDeclaration().map(NodeWithName::getNameAsString).orElse("")));
        }
    }

    private void keepMethod(
            @Nonnull MethodDeclaration n,
            @Nonnull Context context,
            @Nonnull ClassContext activeClass
    ) {
        ClassData data = getClassData(activeClass, context);
        List<TypeName> parameterTypes = getParameters(n.getParameters(), context);
        data.addMethod(n.isStatic(), typeToString(n.getType(), context), n.getNameAsString(), parameterTypes);
    }

    private void markNonExportedMethodClass(
            @Nonnull Context context,
            @Nonnull ClassContext activeClass
    ) {
        // we didn't export the method, so mark its class as containing non-exported methods
        String className = context.getClassName(activeClass);
        ClassData data = classes.get(className);
        if (data != null) {
            data.setNonExportedPublicMethod();
        } else {
            // we didn't export something from the class yet (and the class itself is not exported),
            // so add it to a list of classes with that disabled - getClassData will read that list
            classesWithNonExportedPublicMethods.add(className);
        }
    }

    private boolean hasAnnotation(@Nonnull String annotationName, @Nonnull Iterable<AnnotationExpr> annotations) {
        for (AnnotationExpr annotation : annotations) {
            if (annotation.getNameAsString().equals(annotationName)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Find the data we remembered for a class. If we didn't store any information yet, start with that.
     * If the class was marked as containing non-exported public methods, configure this as well.
     *
     * @param classDeclaration The definition of the class we look at.
     * @param context          The current analysis context.
     * @return The analyzed class data.
     */
    @Nonnull
    private ClassData getClassData(@Nonnull ClassContext classDeclaration, @Nonnull Context context) {
        String className = context.getClassName(classDeclaration);
        ClassData classData = classes.computeIfAbsent(className, missing -> new ClassData(classDeclaration.isPublic(), classDeclaration.getClassType()));
        if (classesWithNonExportedPublicMethods.contains(className)) {
            classesWithNonExportedPublicMethods.remove(className);
            classData.setNonExportedPublicMethod();
        }

        return classData;
    }

    /**
     * Analyze the parameters of a method and return a list of their fully qualified type names.
     *
     * @param parameters Some parameters to look at.
     * @param context    The current analysis context.
     * @return The fully qualified type names for all parameters.
     */
    @Nonnull
    private List<TypeName> getParameters(@Nonnull Iterable<Parameter> parameters, @Nonnull Context context) {
        List<TypeName> parameterTypes = new ArrayList<>();
        for (Parameter parameter : parameters) {
            TypeName typeName = typeToString(parameter.getType(), context);
            // if the parameter is a var-args parameter, it is compiled as an array
            if (parameter.isVarArgs()) {
                typeName = typeName.asArray();
            }
            parameterTypes.add(typeName);
        }

        return parameterTypes;
    }

    @Nonnull
    private TypeName typeToString(@Nonnull Type type, @Nonnull Context context) {
        if (type.isVoidType()) {
            return new TypeName(type.asVoidType().asString());
        }

        if (type.isPrimitiveType()) {
            return new TypeName(type.asPrimitiveType().asString());
        }

        if (type.isArrayType()) {
            return typeToString(type.asArrayType().getComponentType(), context).asArray();
        }

        if (type.isTypeParameter()) {
            if (type.asTypeParameter().getTypeBound() != null && type.asTypeParameter().getTypeBound().isNonEmpty()) {
                throw new RuntimeException("Bound type parameters not implemented for " + type);
            }

            return new TypeName(type.asTypeParameter().getNameAsString(), "java.lang.Object", true);
        }

        if (type.isClassOrInterfaceType()) {
            ClassOrInterfaceType classType = type.asClassOrInterfaceType();
            if (classType.getScope().isEmpty()) {
                String resolved = context.lookupImport(classType.getNameWithScope());

                // if we fail to look up the type in the imports, maybe the class itself somehow defined the type
                if (resolved == null) {
                    // try to find it in the type parameters of the class (if it has any)
                    if (context.getActiveClass() != null) {
                        for (TypeParameter typeParameter : context.getActiveClass().getTypeParameters()) {
                            if (typeParameter.getNameAsString().equals(classType.asString())) {
                                if (typeParameter.getTypeBound() != null && typeParameter.getTypeBound().isNonEmpty()) {
                                    throw new RuntimeException("Bound type parameters not implemented for " + typeParameter.getTypeBound());
                                }

                                return new TypeName(typeParameter.getNameAsString(), "java.lang.Object", true);
                            }
                        }
                    }

                    // try to find an inner class with that name
                    for (ClassContext currentClass : context.getCurrentClasses()) {
                        for (BodyDeclaration<?> member : currentClass.getMembers()) {
                            if (member.isClassOrInterfaceDeclaration()) {
                                ClassOrInterfaceDeclaration subClass = member.asClassOrInterfaceDeclaration();
                                if (subClass.getNameAsString().equals(classType.getNameAsString())) {
                                    return new TypeName(context.getPackageName() + "." + currentClass.getNameAsString() + "$" + subClass.getNameAsString());
                                }
                            }
                        }
                    }

                    // assume it is from the same package and therefore needs no import
                    return new TypeName(context.getPackageName() + "." + classType.getNameWithScope());
                }

                return new TypeName(resolved);
            }

            return new TypeName(classType.getNameWithScope());
        }

        throw new RuntimeException("Unknown type " + type);
    }

}
