package com.hireflow.repository;

import com.hireflow.model.Student;

import java.util.List;
import java.util.Optional;

/**
 * Persistence contracts for Student entity operations.
 */
public interface StudentRepository extends CrudRepository<Student, String> {

    Optional<Student> findByEmail(String email);

    List<Student> findByBranch(String branch);
}
