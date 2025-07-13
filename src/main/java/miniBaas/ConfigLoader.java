package miniBaas;

import java.io.InputStream;
import java.util.Properties;

public class ConfigLoader {
    private final Properties props = new Properties();

    public ConfigLoader(String configFileName) {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(configFileName)) {
            if (input == null) {
                throw new IllegalArgumentException("Config file not found: " + configFileName);
            }
            props.load(input);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load config: " + configFileName, e);
        }
    }

    public String get(String key) {
        return props.getProperty(key);
    }

    // NEW: Load cert/key from classpath
    public InputStream getResourceAsStream(String resourcePath) {
        InputStream input = getClass().getClassLoader().getResourceAsStream(resourcePath);
        if (input == null) {
            throw new IllegalArgumentException("Resource not found: " + resourcePath);
        }
        return input;
    }
}
