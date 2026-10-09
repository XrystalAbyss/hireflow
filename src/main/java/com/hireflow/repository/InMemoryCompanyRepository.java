package com.hireflow.repository;

import com.hireflow.model.Company;

import java.util.Optional;

/**
 * In-memory thread-safe implementation of CompanyRepository.
 */
public class InMemoryCompanyRepository extends AbstractInMemoryRepository<Company, String> implements CompanyRepository {

    public InMemoryCompanyRepository() {
        super(Company::getId, "Company");
    }

    @Override
    public Optional<Company> findByName(String name) {
        if (name == null) return Optional.empty();
        String normalized = name.trim();
        return store.values().stream()
                .filter(c -> c.getName().equalsIgnoreCase(normalized))
                .findFirst();
    }
}
