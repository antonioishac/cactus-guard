package br.com.cactus.guard.tenant;

import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Camada de banco do isolamento: a CADA emprestimo de conexao do pool grava o tenant corrente no GUC
 * de sessao {@code app.tenant_id}, lido pelas policies de Row Level Security (V38). Como o valor e
 * sobrescrito em todo emprestimo (inclusive com vazio quando nao ha tenant), nenhuma conexao reciclada
 * carrega o tenant da requisicao anterior. Sem tenant o GUC fica vazio e as policies devolvem zero linhas.
 * <p>
 * Escolha frente a {@code SET LOCAL} por transacao: cobre tambem leituras fora de transacao, JdbcTemplate e
 * qualquer consumidor do DataSource, sem depender de sincronizacao de transacao. A troca de tenant com
 * transacao ativa e barrada em {@link TenantContext#callAs}. Em bancos que nao sao PostgreSQL (H2 dos
 * testes unitarios) nao faz nada.
 */
public class TenantAwareDataSource extends DelegatingDataSource {

    private volatile Boolean postgres;

    public TenantAwareDataSource(DataSource target) {
        super(target);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return bind(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return bind(super.getConnection(username, password));
    }

    private Connection bind(Connection connection) throws SQLException {
        try {
            if (postgres == null) {
                postgres = connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgres");
            }
            if (postgres) {
                UUID tenant = TenantContext.currentOrNone();
                try (PreparedStatement ps = connection.prepareStatement("SELECT set_config('app.tenant_id', ?, false)")) {
                    ps.setString(1, TenantContext.NO_TENANT.equals(tenant) ? "" : tenant.toString());
                    ps.execute();
                }
            }
            return connection;
        } catch (SQLException | RuntimeException e) {
            // nunca devolver ao chamador uma conexao sem o tenant aplicado
            try {
                connection.close();
            } catch (SQLException ignored) {
                // melhor esforco
            }
            throw e;
        }
    }
}
