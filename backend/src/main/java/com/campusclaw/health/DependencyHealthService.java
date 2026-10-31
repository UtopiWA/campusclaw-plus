package com.campusclaw.health;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.stereotype.Service;

@Service
public class DependencyHealthService {
    private final DataSource database;
    private final QdrantHealthProbe qdrant;

    public DependencyHealthService(DataSource database, QdrantHealthProbe qdrant) {
        this.database = database;
        this.qdrant = qdrant;
    }

    public Map<String, String> snapshot() {
        Map<String, String> statuses = new LinkedHashMap<>();
        statuses.put("database", databaseReady() ? "up" : "down");
        statuses.put("qdrant", qdrant.isReady() ? "up" : "down");
        for (String name : new String[]{"chat", "embedding", "mcp"}) {
            statuses.put(name, "not_checked");
        }
        return statuses;
    }

    private boolean databaseReady() {
        try (Connection connection = database.getConnection()) {
            return connection.isValid(1);
        } catch (SQLException | RuntimeException exception) {
            return false;
        }
    }
}
