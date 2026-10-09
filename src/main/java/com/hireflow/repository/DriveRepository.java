package com.hireflow.repository;

import com.hireflow.model.RecruitmentDrive;

import java.time.LocalDate;
import java.util.List;

/**
 * Persistence contracts for RecruitmentDrive entity operations.
 */
public interface DriveRepository extends CrudRepository<RecruitmentDrive, String> {

    List<RecruitmentDrive> findByCompanyId(String companyId);

    List<RecruitmentDrive> findActiveDrives(LocalDate asOfDate);
}
