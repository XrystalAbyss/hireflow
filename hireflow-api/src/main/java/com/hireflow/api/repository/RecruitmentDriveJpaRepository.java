package com.hireflow.api.repository;

import com.hireflow.api.entity.RecruitmentDriveJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Spring Data JPA repository for read-only access to {@link RecruitmentDriveJpaEntity}.
 */
@Repository
public interface RecruitmentDriveJpaRepository extends JpaRepository<RecruitmentDriveJpaEntity, String> {

    List<RecruitmentDriveJpaEntity> findByCompanyId(String companyId);

    Page<RecruitmentDriveJpaEntity> findByCompanyId(String companyId, Pageable pageable);

    Page<RecruitmentDriveJpaEntity> findByIsClosedFalseAndApplicationDeadlineGreaterThanEqual(
            LocalDate asOfDate, Pageable pageable);

    Page<RecruitmentDriveJpaEntity> findByCompanyIdAndIsClosedFalseAndApplicationDeadlineGreaterThanEqual(
            String companyId, LocalDate asOfDate, Pageable pageable);
}
