package com.hireflow.repository;

import com.hireflow.model.Company;

import java.util.Optional;

/**
 * Persistence contracts for Company entity operations.
 */
public interface CompanyRepository extends CrudRepository<Company, String> {

    Optional<Company> findByName(String name);
}
