/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.sql.DataSource;

/** {@link TestDb} for PostgreSQL, MySQL and MariaDB, through JDBC. */
@ApplicationScoped
public class SqlTestDb implements TestDb {

    @Inject
    DataSource dataSource;

    @Override
    public String passwordHashOf(String email) {
        return scalar("select password_hash from app_user where lower(email) = ?", email.toLowerCase(Locale.ROOT));
    }

    @Override
    public long accountsHolding(String text) {
        return Long.parseLong(scalar("select count(*) from app_user where password_hash = ? or display_name = ? or email = ?", text, text, text));
    }

    @Override
    public boolean hasRefreshTokenWithHash(String tokenHash) {
        return Long.parseLong(scalar("select count(*) from refresh_token where token_hash = ?", tokenHash)) > 0;
    }

    @Override
    public Map<String, List<String>> everyRowAsText() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        try (Connection c = dataSource.getConnection()) {
            DatabaseMetaData meta = c.getMetaData();
            String quote = meta.getIdentifierQuoteString();
            List<String> tables = new ArrayList<>();
            try (ResultSet rs = meta.getTables(c.getCatalog(), c.getSchema(), "%", new String[] {"TABLE"})) {
                while (rs.next()) {
                    tables.add(rs.getString("TABLE_NAME"));
                }
            }
            for (String table : tables) {
                List<String> rows = new ArrayList<>();
                try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("select * from " + quote + table + quote)) {
                    ResultSetMetaData md = rs.getMetaData();
                    while (rs.next()) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 1; i <= md.getColumnCount(); i++) {
                            sb.append(md.getColumnLabel(i)).append('=').append(rs.getString(i)).append(' ');
                        }
                        rows.add(sb.toString());
                    }
                }
                out.put(table, rows);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    @Override
    public List<String> serverTokenFields(String serverId) {
        try (Connection c = dataSource.getConnection(); PreparedStatement st = c.prepareStatement("select token_hash, token_prefix from server where cast(id as char(36)) = ?")) {
            st.setString(1, serverId);
            try (ResultSet rs = st.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("no such server: " + serverId);
                }
                return List.of(rs.getString(1), rs.getString(2));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private String scalar(String sql, String... args) {
        try (Connection c = dataSource.getConnection(); PreparedStatement st = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                st.setString(i + 1, args[i]);
            }
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
