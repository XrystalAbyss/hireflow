package com.hireflow.api.config;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Normalizes cloud database URLs (e.g. from Render, Neon, Railway, Heroku)
 * that provide DATABASE_URL in standard postgresql:// URI format into
 * valid JDBC URLs and extracts credentials automatically.
 */
public class DatabaseUrlEnvironmentPostProcessor implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment env = event.getEnvironment();
        String rawUrl = env.getProperty("DATABASE_URL");
        if (rawUrl == null || rawUrl.isBlank()) {
            rawUrl = env.getProperty("HIREFLOW_DB_URL");
        }
        if (rawUrl == null || rawUrl.isBlank()) {
            return;
        }

        if (rawUrl.startsWith("postgres://")) {
            rawUrl = "postgresql://" + rawUrl.substring("postgres://".length());
        }

        if (rawUrl.startsWith("postgresql://")) {
            try {
                URI uri = URI.create(rawUrl);
                int port = uri.getPort() > 0 ? uri.getPort() : 5432;
                String query = uri.getQuery();
                String jdbcUrl = "jdbc:postgresql://" + uri.getHost() + ":" + port + uri.getPath();
                if (query != null && !query.isBlank()) {
                    jdbcUrl += "?" + query;
                }

                Map<String, Object> props = new HashMap<>();
                props.put("spring.datasource.url", jdbcUrl);
                props.put("hireflow.db.url", jdbcUrl);

                if (uri.getUserInfo() != null) {
                    String[] parts = uri.getUserInfo().split(":", 2);
                    props.put("spring.datasource.username", parts[0]);
                    props.put("hireflow.db.user", parts[0]);
                    if (parts.length > 1) {
                        props.put("spring.datasource.password", parts[1]);
                        props.put("hireflow.db.password", parts[1]);
                    }
                }

                env.getPropertySources().addFirst(new MapPropertySource("cloudDatabaseUrlSource", props));
            } catch (Exception ignored) {
            }
        }
    }
}
