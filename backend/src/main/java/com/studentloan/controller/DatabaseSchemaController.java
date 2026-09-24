package com.studentloan.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studentloan.dto.DatabaseSchema;
import com.studentloan.service.DatabaseSchemaService;

/**
 * Exposes the schema the AI discovers at runtime - tables, columns, SQL types, keys and
 * relationships. Handy for demonstrating that new database objects need no code change.
 *
 * GET /api/db/schema
 *
 * Only object names and types are returned: never credentials, connection details or rows.
 */
@RestController
public class DatabaseSchemaController {

    private final DatabaseSchemaService databaseSchemaService;

    public DatabaseSchemaController(DatabaseSchemaService databaseSchemaService) {
        this.databaseSchemaService = databaseSchemaService;
    }

    @GetMapping("/api/db/schema")
    public DatabaseSchema schema() {
        return databaseSchemaService.discoverSchema();
    }
}
