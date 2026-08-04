package com.flower.buildlib;

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FlowerJavaBasePluginFunctionalTest {
    @TempDir
    Path projectDir;

    @Test
    void configuresJavaConventions() throws IOException {
        write("settings.gradle", "rootProject.name = 'java-base-smoke'\n");
        write(
                "build.gradle",
                """
                plugins {
                    id 'com.flower.java-base'
                }

                tasks.register('verifyJavaBase') {
                    doLast {
                        assert java.toolchain.languageVersion.get().asInt() == 17
                        assert tasks.compileJava.options.encoding == 'UTF-8'
                        assert tasks.compileJava.options.compilerArgs.contains('-parameters')
                    }
                }
                """);

        BuildResult result = runner().build();

        assertEquals(SUCCESS, Objects.requireNonNull(result.task(":verifyJavaBase")).getOutcome());
    }

    private GradleRunner runner() {
        return GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withArguments("verifyJavaBase")
                .withPluginClasspath();
    }

    private void write(String relativePath, String content) throws IOException {
        Path file = projectDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
