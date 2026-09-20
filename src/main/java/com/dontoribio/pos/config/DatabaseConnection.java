package com.dontoribio.pos.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Punto unico de acceso al pool de conexiones HikariCP hacia PostgreSQL.
 * Los DAO obtienen conexiones a traves de {@link #getConnection()} y nunca
 * instancian el DataSource directamente.
 */
public final class DatabaseConnection {

    private static final HikariDataSource DATA_SOURCE;

    static {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(PropertiesLoader.get("db.url"));
        config.setUsername(PropertiesLoader.get("db.user"));
        config.setPassword(PropertiesLoader.get("db.password"));
        config.setDriverClassName(PropertiesLoader.get("db.driver", "org.postgresql.Driver"));

        config.setMaximumPoolSize(PropertiesLoader.getInt("db.pool.maximumPoolSize", 10));
        config.setMinimumIdle(PropertiesLoader.getInt("db.pool.minimumIdle", 2));
        config.setConnectionTimeout(PropertiesLoader.getLong("db.pool.connectionTimeout", 30000));
        config.setIdleTimeout(PropertiesLoader.getLong("db.pool.idleTimeout", 600000));
        config.setMaxLifetime(PropertiesLoader.getLong("db.pool.maxLifetime", 1800000));
        config.setPoolName(PropertiesLoader.get("db.pool.name", "DonToribioPool"));

        DATA_SOURCE = new HikariDataSource(config);
    }

    private DatabaseConnection() {
    }

    public static Connection getConnection() throws SQLException {
        return DATA_SOURCE.getConnection();
    }

    /** Cierra el pool. Debe invocarse al finalizar la aplicacion (MainApp#stop). */
    public static void cerrarPool() {
        if (DATA_SOURCE != null && !DATA_SOURCE.isClosed()) {
            DATA_SOURCE.close();
        }
    }
}
