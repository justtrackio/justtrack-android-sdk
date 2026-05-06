package io.justtrack.proGuardGenerator;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class JavaAnalyzerTest {
    @Test
    public void selfTest() throws IOException {
        JavaAnalyzer analyzer = new JavaAnalyzer();
        String cwd = System.getProperty("user.dir");
        analyzer.parseSourceRoot(cwd + "/src/main/java/io/justtrack/proGuardGenerator");
        String def = analyzer.getProguardDefinitions();

        String expected = "-keep class io.justtrack.proGuardGenerator.ClassData {\n"+
                "    public java.lang.String getProguardDefinition(java.lang.String);\n"+
                "}\n" +
                "-keep public class io.justtrack.proGuardGenerator.JavaAnalyzer {\n" +
                "    public <init>();\n" +
                "    public java.lang.String getProguardDefinitions();\n" +
                "    public void parseSourceRoot(java.lang.String);\n" +
                "}\n";
        Assertions.assertEquals(expected, def);
    }
}
