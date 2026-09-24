package com.studentloan.service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.studentloan.dto.DatabaseSchema;

/**
 * Discovers the live PostgreSQL schema through JDBC database metadata.
 *
 * Only the configured schema (the allowlist - {@code public} by default) is exposed and
 * only real tables inside it, so PostgreSQL internals (information_schema, pg_catalog,
 * pg_toast and anything named {@code pg_*}) can never reach the LLM or the SQL guardrail.
 *
 * Nothing is cached: the schema is read once per AI request (and once per /api/db/schema
 * call). That keeps the POC simple and makes a table or column added a second ago visible
 * immediately - including to the running application.
 */
@Service
public class DatabaseSchemaService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSchemaService.class);

    /** Schema names that must never be exposed, whatever the configured schema is. */
    private static final Set<String> SYSTEM_SCHEMAS =
            Set.of("information_schema", "pg_catalog", "pg_toast");

    /** Table-like objects that are exposed (views and system objects are not). */
    private static final Set<String> EXPOSED_TABLE_TYPES =
            Set.of("TABLE", "PARTITIONED TABLE", "FOREIGN TABLE");

    /** SQL-friendly names for the type names the PostgreSQL JDBC driver reports. */
    private static final Map<String, String> TYPE_NAMES = Map.ofEntries(
            Map.entry("int2", "smallint"),
            Map.entry("int4", "integer"),
            Map.entry("int8", "bigint"),
            Map.entry("bool", "boolean"),
            Map.entry("bpchar", "char"),
            Map.entry("float4", "real"),
            Map.entry("float8", "double precision"),
            Map.entry("timestamptz", "timestamp with time zone"),
            Map.entry("timetz", "time with time zone"));

    private final DataSource dataSource;
    private final String schemaName;

    public DatabaseSchemaService(DataSource dataSource,
            @Value("${app.db.schema:public}") String schemaName) {
        this.dataSource = dataSource;
        this.schemaName = schemaName;
    }

    /**
     * Reads the current tables, columns, primary keys and foreign keys of the allowlisted
     * schema. Returns object names and types only - never credentials, URLs or row data.
     */
    public DatabaseSchema discoverSchema() {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = connection.getCatalog();

            List<DatabaseSchema.Table> tables = new ArrayList<>();
            List<DatabaseSchema.Relationship> relationships = new ArrayList<>();

            for (String tableName : readTableNames(metaData, catalog)) {
                tables.add(new DatabaseSchema.Table(
                        tableName,
                        "TABLE",
                        readColumns(metaData, catalog, tableName, relationships)));
            }

            relationships.sort(Comparator.comparing(DatabaseSchema.Relationship::fromTable)
                    .thenComparing(DatabaseSchema.Relationship::fromColumn));

            log.debug("Schema discovery: {} table(s), {} relationship(s) in schema '{}'",
                    tables.size(), relationships.size(), schemaName);
            if (log.isDebugEnabled()) {
                for (DatabaseSchema.Table table : tables) {
                    log.debug("  exposed table '{}.{}' -> columns {}",
                            schemaName, table.name(), table.columnNames());
                }
            }

            return new DatabaseSchema(schemaName, List.copyOf(tables), List.copyOf(relationships));
        } catch (SQLException ex) {
            // The driver message never contains the configured credentials.
            throw new IllegalStateException("Could not read the database schema: " + ex.getMessage(), ex);
        }
    }

    /**
     * Renders the schema for the LLM: one block per table with its columns and SQL types
     * (primary and foreign keys marked), followed by the foreign-key relationships.
     * Nullability is part of the DTO but is left out here to keep the prompt short for a
     * small local model.
     */
    public String describeForPrompt(DatabaseSchema schema) {
        StringBuilder text = new StringBuilder();
        text.append("Database schema discovered at runtime (PostgreSQL schema \"")
                .append(schema.schema())
                .append("\") - you may ONLY query the tables listed below:\n");

        if (schema.tables().isEmpty()) {
            return text.append("\n(no application tables are available)\n").toString();
        }

        int nameWidth = 0;
        int typeWidth = 0;
        for (DatabaseSchema.Table table : schema.tables()) {
            for (DatabaseSchema.Column column : table.columns()) {
                nameWidth = Math.max(nameWidth, column.name().length());
                typeWidth = Math.max(typeWidth, column.type().length());
            }
        }

        for (DatabaseSchema.Table table : schema.tables()) {
            text.append("\nTable: ").append(table.name()).append('\n').append("Columns:\n");
            for (DatabaseSchema.Column column : table.columns()) {
                text.append("  ").append(pad(column.name(), nameWidth)).append(' ')
                        .append(pad(column.type(), typeWidth));
                if (DatabaseSchema.Column.PRIMARY_KEY.equals(column.key())) {
                    text.append(" PRIMARY KEY");
                } else if (DatabaseSchema.Column.FOREIGN_KEY.equals(column.key())) {
                    text.append(" FOREIGN KEY");
                }
                text.append('\n');
            }
        }

        if (!schema.relationships().isEmpty()) {
            text.append("\nRelationships (foreign keys):\n");
            for (DatabaseSchema.Relationship relationship : schema.relationships()) {
                text.append("  ").append(relationship.fromTable()).append('.').append(relationship.fromColumn())
                        .append(" -> ").append(relationship.toTable()).append('.').append(relationship.toColumn())
                        .append('\n');
            }
        }

        return text.toString().stripTrailing();
    }

    /** Tables of the allowlisted schema, sorted by name. */
    private List<String> readTableNames(DatabaseMetaData metaData, String catalog) throws SQLException {
        List<String> names = new ArrayList<>();
        try (ResultSet resultSet = metaData.getTables(catalog, schemaName, "%", null)) {
            while (resultSet.next()) {
                String name = resultSet.getString("TABLE_NAME");
                String type = resultSet.getString("TABLE_TYPE");
                if (name == null || type == null
                        || !EXPOSED_TABLE_TYPES.contains(type.toUpperCase(Locale.ROOT))) {
                    continue;
                }
                String lowerName = name.toLowerCase(Locale.ROOT);
                if (SYSTEM_SCHEMAS.contains(lowerName) || lowerName.startsWith("pg_")) {
                    continue;
                }
                names.add(name);
            }
        }
        Collections.sort(names);
        return names;
    }

    /** Columns of one table in table order, with primary/foreign key markers. */
    private List<DatabaseSchema.Column> readColumns(DatabaseMetaData metaData, String catalog, String table,
            List<DatabaseSchema.Relationship> relationships) throws SQLException {

        Set<String> primaryKeys = readPrimaryKeys(metaData, catalog, table);
        Set<String> foreignKeys = readForeignKeys(metaData, catalog, table, relationships);

        Map<Integer, DatabaseSchema.Column> columns = new TreeMap<>();
        try (ResultSet resultSet = metaData.getColumns(catalog, schemaName, table, "%")) {
            while (resultSet.next()) {
                String name = resultSet.getString("COLUMN_NAME");
                String lowerName = name.toLowerCase(Locale.ROOT);
                String type = formatType(
                        resultSet.getString("TYPE_NAME"),
                        resultSet.getInt("DATA_TYPE"),
                        resultSet.getInt("COLUMN_SIZE"),
                        resultSet.getInt("DECIMAL_DIGITS"));

                String key = primaryKeys.contains(lowerName)
                        ? DatabaseSchema.Column.PRIMARY_KEY
                        : foreignKeys.contains(lowerName) ? DatabaseSchema.Column.FOREIGN_KEY : "";
                boolean nullable = resultSet.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls;

                columns.put(resultSet.getInt("ORDINAL_POSITION"),
                        new DatabaseSchema.Column(name, type, nullable, key));
            }
        }
        return List.copyOf(columns.values());
    }

    /** Primary key columns of a table; empty when the driver cannot report them. */
    private Set<String> readPrimaryKeys(DatabaseMetaData metaData, String catalog, String table) {
        Set<String> keys = new LinkedHashSet<>();
        try (ResultSet resultSet = metaData.getPrimaryKeys(catalog, schemaName, table)) {
            while (resultSet.next()) {
                keys.add(resultSet.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
            }
        } catch (SQLException ex) {
            log.debug("Primary keys unavailable for {}.{}: {}", schemaName, table, ex.getMessage());
        }
        return keys;
    }

    /** Foreign key columns of a table; also records the relationships they point at. */
    private Set<String> readForeignKeys(DatabaseMetaData metaData, String catalog, String table,
            List<DatabaseSchema.Relationship> relationships) {
        Set<String> keys = new LinkedHashSet<>();
        try (ResultSet resultSet = metaData.getImportedKeys(catalog, schemaName, table)) {
            while (resultSet.next()) {
                String column = resultSet.getString("FKCOLUMN_NAME");
                String targetTable = resultSet.getString("PKTABLE_NAME");
                String targetColumn = resultSet.getString("PKCOLUMN_NAME");
                keys.add(column.toLowerCase(Locale.ROOT));
                relationships.add(new DatabaseSchema.Relationship(table, column, targetTable, targetColumn));
            }
        } catch (SQLException ex) {
            log.debug("Foreign keys unavailable for {}.{}: {}", schemaName, table, ex.getMessage());
        }
        return keys;
    }

    /** PostgreSQL column type as SQL-friendly text, e.g. {@code varchar(100)}, {@code numeric(12,2)}. */
    private String formatType(String driverTypeName, int jdbcType, int size, int scale) {
        if (driverTypeName == null) {
            return "unknown";
        }
        String type = TYPE_NAMES.getOrDefault(driverTypeName.toLowerCase(Locale.ROOT), driverTypeName);
        if (jdbcType == Types.NUMERIC || jdbcType == Types.DECIMAL) {
            return size > 0 ? type + "(" + size + (scale > 0 ? "," + scale : "") + ")" : type;
        }
        boolean sized = jdbcType == Types.VARCHAR || jdbcType == Types.CHAR
                || jdbcType == Types.NVARCHAR || jdbcType == Types.NCHAR;
        return sized && size > 0 ? type + "(" + size + ")" : type;
    }

    /** Left-aligns text so the schema block stays easy to read for the model. */
    private String pad(String value, int width) {
        return value.length() >= width ? value : value + " ".repeat(width - value.length());
    }
}
