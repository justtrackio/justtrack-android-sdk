package io.justtrack.proGuardGenerator;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * A context contains the imports and (nested) classes we are looking at as well as the current
 * package name.
 */
class Context {
    @Nonnull
    private final Map<String, String> imports;
    @Nonnull
    private final Map<String, Integer> anonymousClassIndexMap;
    @Nonnull
    private final List<ClassContext> currentClasses;
    @Nonnull
    private final String packageName;

    Context(@Nonnull String packageName) {
        this.imports = new HashMap<>();
        this.anonymousClassIndexMap = new HashMap<>();
        this.currentClasses = Collections.emptyList();
        this.packageName = packageName;

        // Java implicitly has imports for everything from java.lang, so we try to mimic this:
        loadDefaultClasses();
    }

    Context(@Nonnull Context parent, @Nonnull ClassOrInterfaceDeclaration currentClass) {
        this.imports = new HashMap<>(parent.imports);
        this.anonymousClassIndexMap = parent.anonymousClassIndexMap;
        this.currentClasses = new ArrayList<>(parent.currentClasses);
        this.packageName = parent.packageName;

        this.currentClasses.add(new ClassDeclarationContext(currentClass));
    }

    Context(@Nonnull Context parent, @Nonnull ClassOrInterfaceType anonymousClass, @Nonnull NodeList<BodyDeclaration<?>> classBody) {
        this.imports = new HashMap<>(parent.imports);
        this.anonymousClassIndexMap = parent.anonymousClassIndexMap;
        this.currentClasses = new ArrayList<>(parent.currentClasses);
        this.packageName = parent.packageName;

        int index = this.anonymousClassIndexMap.compute(getScope(), (key, value) -> value == null ? 1 : value + 1);

        this.currentClasses.add(new AnonymousClassDeclarationContext(parent.currentClasses, index, anonymousClass, classBody));
    }

    private String getScope() {
        StringBuilder s = new StringBuilder(packageName);

        for (ClassContext classContext : currentClasses) {
            s.append('$').append(classContext.getFullyQualifiedName().orElse(classContext.getNameAsString()));
        }

        return s.toString();
    }

    @Nonnull
    String getPackageName() {
        return packageName;
    }

    @Nullable
    ClassContext getActiveClass() {
        return currentClasses.isEmpty() ? null : currentClasses.get(currentClasses.size() - 1);
    }

    @Nonnull
    Iterable<ClassContext> getCurrentClasses() {
        return currentClasses;
    }

    @Nonnull
    String getClassName(@Nonnull ClassContext classDeclaration) {
        for (ClassContext currentClass : currentClasses) {
            if (!classDeclaration.equals(currentClass)) {
                return getClassNameSimple(currentClass) + "$" + classDeclaration.getNameAsString();
            }
        }

        return classDeclaration.getFullyQualifiedName().orElse(classDeclaration.getNameAsString());
    }

    @Nonnull
    private String getClassNameSimple(@Nonnull ClassContext classDeclaration) {
        return classDeclaration.getFullyQualifiedName().orElse(classDeclaration.getNameAsString());
    }

    void addImport(@Nonnull String simpleName, @Nonnull String fullQualifiedName) {
        imports.put(simpleName, fullQualifiedName);
    }

    void addImport(@Nonnull Class<?> clazz) {
        addImport(clazz.getSimpleName(), clazz.getCanonicalName());
    }

    @Nullable
    String lookupImport(@Nonnull String simpleName) {
        return imports.get(simpleName);
    }

    private void loadDefaultClasses() {
        addImport(Appendable.class);
        addImport(ArithmeticException.class);
        addImport(ArrayIndexOutOfBoundsException.class);
        addImport(ArrayStoreException.class);
        addImport(AutoCloseable.class);
        addImport(Boolean.class);
        addImport(Byte.class);
        addImport(Character.class);
        addImport(Character.Subset.class);
        addImport(Character.UnicodeBlock.class);
        addImport(Class.class);
        addImport(ClassCastException.class);
        addImport(ClassLoader.class);
        addImport(ClassNotFoundException.class);
        addImport(CloneNotSupportedException.class);
        addImport(Cloneable.class);
        addImport(Comparable.class);
        addImport(Deprecated.class);
        addImport(Double.class);
        addImport(Enum.class);
        addImport(EnumConstantNotPresentException.class);
        addImport(Error.class);
        addImport(Exception.class);
        addImport(ExceptionInInitializerError.class);
        addImport(Float.class);
        addImport(FunctionalInterface.class);
        addImport(IllegalAccessError.class);
        addImport(IllegalAccessException.class);
        addImport(IllegalArgumentException.class);
        addImport(IllegalMonitorStateException.class);
        addImport(IllegalStateException.class);
        addImport(IllegalThreadStateException.class);
        addImport(IndexOutOfBoundsException.class);
        addImport(InheritableThreadLocal.class);
        addImport(InstantiationError.class);
        addImport(InstantiationException.class);
        addImport(Integer.class);
        addImport(InterruptedException.class);
        addImport(Iterable.class);
        addImport(Long.class);
        addImport(Math.class);
        addImport(NegativeArraySizeException.class);
        addImport(NoSuchFieldError.class);
        addImport(NoSuchFieldException.class);
        addImport(NoSuchMethodError.class);
        addImport(NoSuchMethodException.class);
        addImport(NullPointerException.class);
        addImport(Number.class);
        addImport(NumberFormatException.class);
        addImport(Object.class);
        addImport(OutOfMemoryError.class);
        addImport(Override.class);
        addImport(Package.class);
        addImport(Process.class);
        addImport(ProcessBuilder.class);
        addImport(ProcessBuilder.Redirect.class);
        addImport(ProcessBuilder.Redirect.Type.class);
        addImport(RuntimeException.class);
        addImport(RuntimePermission.class);
        addImport(SecurityException.class);
        addImport(Short.class);
        addImport(StackOverflowError.class);
        addImport(StackTraceElement.class);
        addImport(StrictMath.class);
        addImport(String.class);
        addImport(StringBuffer.class);
        addImport(StringBuilder.class);
        addImport(StringIndexOutOfBoundsException.class);
        addImport(SuppressWarnings.class);
        addImport(System.class);
        addImport(Thread.class);
        addImport(ThreadGroup.class);
        addImport(ThreadLocal.class);
        addImport(Throwable.class);
        addImport(TypeNotPresentException.class);
        addImport(UnknownError.class);
        addImport(UnsatisfiedLinkError.class);
        addImport(UnsupportedClassVersionError.class);
        addImport(UnsupportedOperationException.class);
        addImport(VerifyError.class);
        addImport(VirtualMachineError.class);
        addImport(Void.class);
    }
}
