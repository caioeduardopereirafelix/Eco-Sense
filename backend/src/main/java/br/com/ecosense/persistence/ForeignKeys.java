package br.com.ecosense.persistence;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(0)
public class ForeignKeys implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ForeignKeys.class);

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    public ForeignKeys(DataSource dataSource) {
        this.dataSource = dataSource;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public void run(ApplicationArguments args) throws SQLException {
        if (!isMySql()) return;
        if (!exists("fk_request_device")) {
            int removed = jdbc.update("DELETE FROM maintenance_requests WHERE device_id NOT IN (SELECT id FROM devices)");
            jdbc.execute("ALTER TABLE maintenance_requests ADD CONSTRAINT fk_request_device "
                    + "FOREIGN KEY (device_id) REFERENCES devices (id) ON DELETE CASCADE");
            log.info("Chave estrangeira fk_request_device criada ({} pedido(s) sem dispositivo removido(s)).", removed);
        }
        if (!exists("fk_request_user")) {
            int cleared = jdbc.update("UPDATE maintenance_requests SET requested_by_id = NULL "
                    + "WHERE requested_by_id IS NOT NULL AND requested_by_id NOT IN (SELECT id FROM app_users)");
            jdbc.execute("ALTER TABLE maintenance_requests ADD CONSTRAINT fk_request_user "
                    + "FOREIGN KEY (requested_by_id) REFERENCES app_users (id) ON DELETE SET NULL");
            log.info("Chave estrangeira fk_request_user criada ({} pedido(s) de usuário apagado ajustado(s)).", cleared);
        }
    }

    private boolean isMySql() throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            return c.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql");
        }
    }

    private boolean exists(String name) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() "
                        + "AND TABLE_NAME = 'maintenance_requests' AND CONSTRAINT_NAME = ?", Integer.class, name);
        return n != null && n > 0;
    }
}
