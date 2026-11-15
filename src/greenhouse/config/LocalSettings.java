package greenhouse.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** 本机配置不进入共享源码；JVM参数、环境变量优先于本机配置文件。 */
public final class LocalSettings {
    private static final Properties SETTINGS = load();

    private LocalSettings() {}

    public static String get(String property, String environment, String fallback) {
        String value = System.getProperty(property);
        if (value == null) value = System.getenv(environment);
        if (value == null) value = SETTINGS.getProperty(property);
        return value == null ? fallback : value;
    }

    private static Properties load() {
        Properties properties = new Properties();
        String configured = System.getProperty("greenhouse.configFile");
        if (configured == null || configured.isBlank()) {
            configured = System.getenv("GH_CONFIG_FILE");
        }
        try {
            Path file;
            if (configured != null && !configured.isBlank()) {
                file = Path.of(configured).toAbsolutePath().normalize();
                if (!Files.isRegularFile(file)) {
                    throw new IllegalStateException("指定的本机配置文件不存在");
                }
            } else {
                // IDEA的模块输出和Tomcat部署输出均为WEB-INF/classes。
                Path classes = Path.of(LocalSettings.class.getProtectionDomain()
                        .getCodeSource().getLocation().toURI());
                file = classes.getParent().resolve("greenhouse.local.properties");
                if (!Files.isRegularFile(file)) return properties;
            }
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
            return properties;
        } catch (IOException | java.net.URISyntaxException e) {
            throw new IllegalStateException("读取本机配置失败，请检查路径和文件编码", e);
        }
    }
}
