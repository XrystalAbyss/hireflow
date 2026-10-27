package com.hireflow.api.repository;

import com.hireflow.api.entity.CompanyJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for read-only access to {@link CompanyJpaEntity}.
 */
@Repository
public interface CompanyJpaRepository extends JpaRepository<CompanyJpaEntity, String> {

    Optional<CompanyJpaEntity> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);
}
