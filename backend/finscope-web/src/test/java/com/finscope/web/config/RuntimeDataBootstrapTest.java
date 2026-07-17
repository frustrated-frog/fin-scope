package com.finscope.web.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeDataBootstrapTest {
    @TempDir
    Path temp;

    @Test
    void resolvesTheSameExistingMainDatabaseFromProjectAndBackendDirectories() throws Exception {
        Path project = projectWithMainDatabase();

        RuntimeDataBootstrap.Configuration fromProject = resolve(project);
        RuntimeDataBootstrap.Configuration fromBackend = resolve(project.resolve("backend"));

        Path expected = project.getParent().resolve("data").toAbsolutePath().normalize();
        assertEquals(expected, fromProject.getDataRoot());
        assertEquals(expected, fromBackend.getDataRoot());
        assertEquals(fromProject.getJdbcUrl(), fromBackend.getJdbcUrl());
        assertEquals("jdbc:sqlite:" + expected.resolve("finance.db") + "?foreign_keys=on",
                fromProject.getJdbcUrl());
    }

    @Test
    void explicitDataRootOverridesProjectDiscovery() throws Exception {
        Path project = projectWithMainDatabase();
        Path explicit = temp.resolve("explicit-data");
        Files.createDirectories(explicit);
        Files.createFile(explicit.resolve("finance.db"));
        Map<String, String> environment = new HashMap<String, String>();
        environment.put("FINSCOPE_DATA_ROOT", explicit.toString());

        RuntimeDataBootstrap.Configuration result = RuntimeDataBootstrap.resolve(
                project.resolve("backend"), environment, new Properties(), new String[0]);

        assertEquals(explicit.toAbsolutePath().normalize(), result.getDataRoot());
    }

    @Test
    void refusesARelativeExplicitDataRoot() throws Exception {
        Path project = projectWithMainDatabase();
        Path accidental = project.resolve("data");
        Files.createDirectories(accidental);
        Files.createFile(accidental.resolve("finance.db"));
        Map<String, String> environment = new HashMap<String, String>();
        environment.put("FINSCOPE_DATA_ROOT", "../data");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> RuntimeDataBootstrap.resolve(project.resolve("backend"), environment,
                        new Properties(), new String[0]));

        assertTrue(error.getMessage().contains("绝对路径"), error.getMessage());
    }

    @Test
    void refusesToSilentlyCreateAMissingDatabase() throws Exception {
        Path project = createProject();

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> resolve(project.resolve("backend")));

        assertTrue(error.getMessage().contains("finance.db"), error.getMessage());
        assertTrue(error.getMessage().contains("拒绝创建空库"), error.getMessage());
    }

    @Test
    void refusesAConflictingDatasourceUrl() throws Exception {
        Path project = projectWithMainDatabase();
        Map<String, String> environment = new HashMap<String, String>();
        environment.put("SPRING_DATASOURCE_URL",
                "jdbc:sqlite:" + temp.resolve("other.db") + "?foreign_keys=on");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> RuntimeDataBootstrap.resolve(project, environment,
                        new Properties(), new String[0]));

        assertTrue(error.getMessage().contains("数据库配置冲突"), error.getMessage());
    }

    private RuntimeDataBootstrap.Configuration resolve(Path currentDirectory) {
        return RuntimeDataBootstrap.resolve(currentDirectory, Collections.emptyMap(),
                new Properties(), new String[0]);
    }

    private Path projectWithMainDatabase() throws Exception {
        Path project = createProject();
        Path data = project.getParent().resolve("data");
        Files.createDirectories(data);
        Files.createFile(data.resolve("finance.db"));
        return project;
    }

    private Path createProject() throws Exception {
        Path project = temp.resolve("workspace").resolve("fin-scope");
        Files.createDirectories(project.resolve("backend"));
        Files.createDirectories(project.resolve("frontend"));
        Files.createFile(project.resolve("backend").resolve("pom.xml"));
        Files.createFile(project.resolve("frontend").resolve("package.json"));
        return project;
    }
}
