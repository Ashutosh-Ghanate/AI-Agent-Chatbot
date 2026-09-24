package com.studentloan.dto;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Runtime snapshot of the PostgreSQL schema the AI is allowed to query.
 *
 * The snapshot is produced by {@code DatabaseSchemaService} from JDBC database metadata,
 * so no table or column name is hardcoded anywhere in the application. It is returned
 * as-is by GET /api/db/schema and rendered into the LLM system prompt.
 */
public record DatabaseSchema(
        String schema,
        List<Table> tables,
        List<Relationship> relationships) {

    /** A single application table. */
    public record Table(String name, String type, List<Column> columns) {

        /** Lower-cased column names of this table. */
        public Set<String> columnNames() {
            Set<String> names = new LinkedHashSet<>();
            for (Column column : columns) {
                names.add(column.name().toLowerCase(Locale.ROOT));
            }
            return names;
        }
    }

    /** A single column: SQL type plus its key role ({@code PK}, {@code FK} or empty). */
    public record Column(String name, String type, boolean nullable, String key) {

        public static final String PRIMARY_KEY = "PK";
        public static final String FOREIGN_KEY = "FK";
    }

    /** A foreign key: {@code fromTable.fromColumn -> toTable.toColumn}. */
    public record Relationship(String fromTable, String fromColumn, String toTable, String toColumn) {
    }

    /** Lower-cased names of every table in the snapshot. */
    public Set<String> tableNames() {
        Set<String> names = new LinkedHashSet<>();
        for (Table table : tables) {
            names.add(table.name().toLowerCase(Locale.ROOT));
        }
        return names;
    }

    /** Lower-cased names of every column of every table in the snapshot. */
    public Set<String> columnNames() {
        Set<String> names = new LinkedHashSet<>();
        for (Table table : tables) {
            names.addAll(table.columnNames());
        }
        return names;
    }

    /** Name of the table that owns the given column (case-insensitive), or null. */
    public String tableOwning(String column) {
        if (column == null) {
            return null;
        }
        String wanted = column.toLowerCase(Locale.ROOT);
        for (Table table : tables) {
            if (table.columnNames().contains(wanted)) {
                return table.name();
            }
        }
        return null;
    }

    /** First table in the snapshot, or null when nothing was discovered. */
    public String firstTableName() {
        return tables.isEmpty() ? null : tables.get(0).name();
    }
}
