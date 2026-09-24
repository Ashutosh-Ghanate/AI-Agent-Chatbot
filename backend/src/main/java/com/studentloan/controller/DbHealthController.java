package com.studentloan.controller;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Verifies that the Spring Boot backend can reach PostgreSQL and read
 * the student_table table.
 *
 * GET /api/db/ping
 */
@RestController
@RequestMapping("/api/db")
public class DbHealthController {

    private final DataSource dataSource;

    public DbHealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {

            st.executeQuery("SELECT 1");

            long rows = -1;
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM student_table")) {
                if (rs.next()) {
                    rows = rs.getLong(1);
                }
            }

            return Map.of(
                    "status", "UP",
                    "database", String.valueOf(conn.getCatalog()),
                    "student_table_rows", rows);
        } catch (Exception e) {
            return Map.of(
                    "status", "DOWN",
                    "error", e.getMessage() == null ? "unknown" : e.getMessage());
        }
    }
}