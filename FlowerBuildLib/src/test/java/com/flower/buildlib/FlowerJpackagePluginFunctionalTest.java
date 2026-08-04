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

class FlowerJpackagePluginFunctionalTest {
    @TempDir
    Path projectDir;

    @Test
    void appliesJpackageConventions() throws IOException {
        write("settings.gradle", "rootProject.name = 'jpackage-smoke'\n");
        write(
                "build.gradle",
                """
                plugins {
                    id 'com.flower.jpackage'
                }

                application {
                    mainClass = 'com.example.Main'
                }

                tasks.register('verifyJpackage') {
                    doLast {
                        assert pluginManager.hasPlugin('application')
                        assert pluginManager.hasPlugin('com.flower.java-base')
                        assert pluginManager.hasPlugin('org.beryx.runtime')
                        assert runtime.imageDir.get().asFile == layout.buildDirectory.dir('image').get().asFile
                        assert runtime.options.get() == [
                            '--strip-debug',
                            '--compress', '2',
                            '--no-header-files',
                            '--no-man-pages',
                            '--ignore-signing-information'
                        ]
                        assert runtime.launcherData.get().jvmArgs == ['-Dfile.encoding=UTF-8']
                        assert tasks.findByName('jpackageImage') != null
                        assert tasks.findByName('jpackage') != null
                    }
                }
                """);
        write(
                "src/main/java/com/example/Main.java",
                """
                package com.example;

                public final class Main {
                    private Main() {
                    }

                    public static void main(String[] args) {
                        System.out.println("Ready");
                    }
                }
                """);

        BuildResult result = runner().build();

        assertEquals(SUCCESS, Objects.requireNonNull(result.task(":compileJava")).getOutcome());
        assertEquals(SUCCESS, Objects.requireNonNull(result.task(":verifyJpackage")).getOutcome());
    }

    private GradleRunner runner() {
        return GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withArguments("compileJava", "verifyJpackage")
                .withPluginClasspath();
    }

    private void write(String relativePath, String content) throws IOException {
        Path file = projectDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
