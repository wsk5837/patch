package com.gazellio.platform.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Converts Render's postgresql:// connection string to Spring JDBC properties. */
public class RenderDatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String databaseUrl = environment.getProperty("DATABASE_URL");
        String explicit = environment.getProperty("SPRING_DATASOURCE_URL");
        if (databaseUrl == null || databaseUrl.isBlank() || (explicit != null && !explicit.isBlank())) {
            return;
        }
        try {
            URI uri = URI.create(databaseUrl);
            String[] userInfo = uri.getRawUserInfo() == null ? new String[0] : uri.getRawUserInfo().split(":", 2);
            String username = userInfo.length > 0 ? decode(userInfo[0]) : "";
            String password = userInfo.length > 1 ? decode(userInfo[1]) : "";
            int port = uri.getPort() > 0 ? uri.getPort() : 5432;
            String db = uri.getPath() == null ? "" : uri.getPath().replaceFirst("^/", "");
            String jdbc = "jdbc:postgresql://" + uri.getHost() + ":" + port + "/" + db;
            if (uri.getRawQuery() != null && !uri.getRawQuery().isBlank()) {
                jdbc += "?" + uri.getRawQuery();
            }
            Map<String, Object> properties = new HashMap<>();
            properties.put("spring.datasource.url", jdbc);
            properties.put("spring.datasource.username", username);
            properties.put("spring.datasource.password", password);
            environment.getPropertySources().addFirst(new MapPropertySource("renderDatabaseUrl", properties));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to parse DATABASE_URL", ex);
        }
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}
