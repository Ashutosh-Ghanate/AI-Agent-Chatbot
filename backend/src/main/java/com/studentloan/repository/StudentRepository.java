package com.studentloan.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.studentloan.entity.Student;

/**
 * Read-only data access for the existing {@code student_table}.
 */
public interface StudentRepository extends JpaRepository<Student, Long> {
}