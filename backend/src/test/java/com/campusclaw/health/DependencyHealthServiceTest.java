package com.campusclaw.health;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class DependencyHealthServiceTest {
    @Test void onlyChecksDatabaseAndQdrantAndClosesConnection() throws Exception {
        var db = mock(DataSource.class);
        var connection = mock(Connection.class);
        var qdrant = mock(QdrantHealthProbe.class);
        when(db.getConnection()).thenReturn(connection);
        when(connection.isValid(1)).thenReturn(true);
        when(qdrant.isReady()).thenReturn(true);
        var states = new DependencyHealthService(db, qdrant).snapshot();
        assertThat(states).containsEntry("database", "up").containsEntry("qdrant", "up")
                .containsEntry("chat", "not_checked").containsEntry("embedding", "not_checked")
                .containsEntry("mcp", "not_checked").hasSize(5);
        verify(connection).isValid(1);
        verify(connection).close();
        verifyNoMoreInteractions(connection);
    }

    @Test void reportsEachFailureWithoutLeakingException() throws Exception {
        var db = mock(DataSource.class);
        var qdrant = mock(QdrantHealthProbe.class);
        when(db.getConnection()).thenThrow(new SQLException("private-marker"));
        assertThat(new DependencyHealthService(db, qdrant).snapshot())
                .containsEntry("database", "down").containsEntry("qdrant", "down");
        verify(qdrant).isReady();
    }
}
