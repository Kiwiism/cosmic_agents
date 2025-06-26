package database;

import liquibase.Liquibase;
import liquibase.UpdateSummaryOutputEnum;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import tools.DatabaseConnection;

import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseMigrations {

    public static void runDatabaseMigrations() {
        suppressLiquibaseLogs();
        runLiquibaseUpdate();
    }

    private static void suppressLiquibaseLogs() {
        java.util.logging.Logger liquibaseLogger = java.util.logging.Logger.getLogger("liquibase");
        liquibaseLogger.setLevel(java.util.logging.Level.WARNING);
    }

    private static void runLiquibaseUpdate() {
        try (Connection connection = DatabaseConnection.getConnection()) {
            liquibase.database.DatabaseConnection databaseConnection = new JdbcConnection(connection);
            Liquibase liquibase = new Liquibase("db/changelog.xml", new ClassLoaderResourceAccessor(), databaseConnection);
            liquibase.setShowSummaryOutput(UpdateSummaryOutputEnum.LOG);
            liquibase.update();
        } catch (SQLException | LiquibaseException e) {
            throw new RuntimeException("Failed to run database migrations", e);
        }
    }
}
