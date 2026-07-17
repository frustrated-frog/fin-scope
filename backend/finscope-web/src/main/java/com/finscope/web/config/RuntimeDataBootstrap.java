package com.finscope.web.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Properties;

public final class RuntimeDataBootstrap {
    private static final Logger LOG = LoggerFactory.getLogger(RuntimeDataBootstrap.class);
    private static final String DATA_ROOT_PROPERTY = "finscope.data-root";
    private static final String DATASOURCE_PROPERTY = "spring.datasource.url";
    private static final String DATA_ROOT_ENV = "FINSCOPE_DATA_ROOT";
    private static final String DATASOURCE_ENV = "SPRING_DATASOURCE_URL";

    private RuntimeDataBootstrap() {
    }

    public static void configure(String[] args) {
        Configuration resolved = resolve(Paths.get(System.getProperty("user.dir")),
                System.getenv(), System.getProperties(), args);
        System.setProperty(DATA_ROOT_PROPERTY, resolved.getDataRoot().toString());
        System.setProperty(DATASOURCE_PROPERTY, resolved.getJdbcUrl());
        LOG.info("FinScope 主数据库：{}", resolved.getDatabasePath());
    }

    static Configuration resolve(Path currentDirectory, Map<String, String> environment,
                                 Properties systemProperties, String[] args) {
        Path current = currentDirectory.toAbsolutePath().normalize();
        String configuredRoot = firstNonBlank(argument(args, "--" + DATA_ROOT_PROPERTY + "="),
                systemProperties.getProperty(DATA_ROOT_PROPERTY), environment.get(DATA_ROOT_ENV));
        Path dataRoot = configuredRoot == null
                ? discoverDefaultDataRoot(current)
                : absolute(current, configuredRoot);
        Path database = dataRoot.resolve("finance.db").toAbsolutePath().normalize();
        if (!Files.isDirectory(dataRoot) || !Files.isRegularFile(database)) {
            throw new IllegalStateException("FinScope 主数据库不存在：" + database
                    + "；为避免数据分叉，拒绝创建空库。请设置 " + DATA_ROOT_ENV + " 指向现有数据目录。");
        }

        String expectedUrl = "jdbc:sqlite:" + database + "?foreign_keys=on";
        String configuredUrl = firstNonBlank(argument(args, "--" + DATASOURCE_PROPERTY + "="),
                systemProperties.getProperty(DATASOURCE_PROPERTY), environment.get(DATASOURCE_ENV));
        if (configuredUrl != null && !sameDatabase(current, configuredUrl, database)) {
            throw new IllegalStateException("数据库配置冲突：" + DATA_ROOT_ENV + " 指向 " + database
                    + "，但 " + DATASOURCE_ENV + " 指向其他位置。数据库路径必须由同一个 data root 派生。");
        }
        return new Configuration(dataRoot, database, expectedUrl);
    }

    private static Path discoverDefaultDataRoot(Path currentDirectory) {
        Path cursor = currentDirectory;
        while (cursor != null) {
            if (Files.isRegularFile(cursor.resolve("backend").resolve("pom.xml"))
                    && Files.isRegularFile(cursor.resolve("frontend").resolve("package.json"))) {
                Path parent = cursor.getParent();
                if (parent == null) break;
                return parent.resolve("data").toAbsolutePath().normalize();
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("无法从启动目录定位 FinScope 项目：" + currentDirectory
                + "。请设置 " + DATA_ROOT_ENV + " 指向现有数据目录。");
    }

    private static boolean sameDatabase(Path currentDirectory, String jdbcUrl, Path expected) {
        if (!jdbcUrl.startsWith("jdbc:sqlite:")) return false;
        String value = jdbcUrl.substring("jdbc:sqlite:".length());
        int query = value.indexOf('?');
        if (query >= 0) value = value.substring(0, query);
        if (value.trim().isEmpty() || value.startsWith("file:")) return false;
        return absolute(currentDirectory, value).equals(expected);
    }

    private static Path absolute(Path currentDirectory, String value) {
        Path path = Paths.get(value.trim());
        return (path.isAbsolute() ? path : currentDirectory.resolve(path))
                .toAbsolutePath().normalize();
    }

    private static String argument(String[] args, String prefix) {
        if (args == null) return null;
        for (String arg : args) {
            if (arg != null && arg.startsWith(prefix)) return arg.substring(prefix.length());
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return null;
    }

    public static final class Configuration {
        private final Path dataRoot;
        private final Path databasePath;
        private final String jdbcUrl;

        Configuration(Path dataRoot, Path databasePath, String jdbcUrl) {
            this.dataRoot = dataRoot;
            this.databasePath = databasePath;
            this.jdbcUrl = jdbcUrl;
        }

        public Path getDataRoot() {
            return dataRoot;
        }

        public Path getDatabasePath() {
            return databasePath;
        }

        public String getJdbcUrl() {
            return jdbcUrl;
        }
    }
}
