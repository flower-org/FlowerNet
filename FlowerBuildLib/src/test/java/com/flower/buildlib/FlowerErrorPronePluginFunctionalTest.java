package com.flower.buildlib;

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FlowerErrorPronePluginFunctionalTest {
    @TempDir
    Path projectDir;

    @BeforeEach
    void createSettings() throws IOException {
        write("settings.gradle", "rootProject.name = 'errorprone-smoke'\n");
    }

    @Test
    void compilesValidCode() throws IOException {
        writeConfiguredBuild("");
        write(
                "src/main/java/com/example/ValidCode.java",
                """
                package com.example;

                public final class ValidCode {
                    private ValidCode() {
                    }

                    public static int length(String value) {
                        return value.length();
                    }
                }
                """);

        BuildResult result = runner("compileJava").build();

        assertEquals(SUCCESS, Objects.requireNonNull(result.task(":compileJava")).getOutcome());
    }

    @Test
    void requiresAnnotatedPackages() throws IOException {
        write(
                "build.gradle",
                """
                plugins {
                    id 'com.flower.java-base'
                    id 'com.flower.errorprone'
                }
                """);

        BuildResult result = runner("help").buildAndFail();

        assertTrue(result.getOutput().contains(
                "flowerErrorProne.annotatedPackages"));
    }

    @Test
    void nullAwayRejectsUnsafeDereference() throws IOException {
        writeConfiguredBuild("");
        writeNullableDereference(
                "src/main/java/com/example/UnsafeCode.java",
                "UnsafeCode");

        BuildResult result = runner("compileJava").buildAndFail();

        assertTrue(result.getOutput().contains("NullAway"));
    }

    @Test
    void generatedSourcesAreExcluded() throws IOException {
        writeConfiguredBuild(
                """

                sourceSets.main.java.srcDir(
                    layout.buildDirectory.dir('generated/sources/smoke/java')
                )
                """);
        writeNullableDereference(
                "build/generated/sources/smoke/java/com/example/GeneratedCode.java",
                "GeneratedCode");

        BuildResult result = runner("compileJava").build();

        assertEquals(SUCCESS, Objects.requireNonNull(result.task(":compileJava")).getOutcome());
    }

    private void writeConfiguredBuild(String additionalConfiguration)
            throws IOException {
        write(
                "build.gradle",
                """
                plugins {
                    id 'com.flower.java-base'
                    id 'com.flower.errorprone'
                }

                flowerErrorProne {
                    annotatedPackages = ['com.example']
                }
                """ + additionalConfiguration);
    }

    private void writeNullableDereference(String path, String className)
            throws IOException {
        write(
                path,
                """
                package com.example;

                import javax.annotation.Nullable;

                public final class %s {
                    private %s() {
                    }

                    public static int length(@Nullable String value) {
                        return value.length();
                    }
                }
                """.formatted(className, className));
    }

    private GradleRunner runner(String... arguments) {
        return GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withArguments(arguments)
                .withPluginClasspath();
    }

    private void write(String relativePath, String content) throws IOException {
        Path file = projectDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
