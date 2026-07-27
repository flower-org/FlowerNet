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

class FlowerJavaFxPluginFunctionalTest {
    @TempDir
    Path projectDir;

    @Test
    void compilesJavaFxCodeWithDefaultConventions() throws IOException {
        write("settings.gradle", "rootProject.name = 'javafx-smoke'\n");
        write(
                "build.gradle",
                """
                plugins {
                    id 'com.flower.java-base'
                    id 'com.flower.javafx'
                }

                tasks.register('verifyJavaFx') {
                    doLast {
                        assert javafx.version == '21.0.2'
                        assert javafx.modules == [
                            'javafx.controls',
                            'javafx.fxml',
                            'javafx.graphics',
                            'javafx.media'
                        ]
                    }
                }
                """);
        write(
                "src/main/java/com/example/JavaFxCode.java",
                """
                package com.example;

                import javafx.scene.control.Button;

                public final class JavaFxCode {
                    private JavaFxCode() {
                    }

                    public static Button createButton() {
                        return new Button("Ready");
                    }
                }
                """);

        BuildResult result = runner().build();

        assertEquals(SUCCESS, Objects.requireNonNull(result.task(":compileJava")).getOutcome());
        assertEquals(SUCCESS, Objects.requireNonNull(result.task(":verifyJavaFx")).getOutcome());
    }

    private GradleRunner runner() {
        return GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withArguments("compileJava", "verifyJavaFx")
                .withPluginClasspath();
    }

    private void write(String relativePath, String content) throws IOException {
        Path file = projectDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
