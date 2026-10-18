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

    /**
     * Finds drive by unique ID with row-level synchronization against concurrent closure or criteria mutation.
     * Default implementation falls back to findById(id) for non-locking implementations.
     */
    default java.util.Optional<RecruitmentDrive> findByIdForUpdate(String id) {
        return findById(id);
    }

    /**
     * Explicit alias for {@link #findByIdForUpdate(String)} indicating shared row-level locking (PostgreSQL FOR SHARE).
     * Eliminates race conditions with concurrent placement officer drive closure (UPDATE) while allowing
     * concurrent student submissions for the same drive to proceed without serialization bottleneck.
     */
    default java.util.Optional<RecruitmentDrive> findByIdWithSharedLock(String id) {
        return findByIdForUpdate(id);
    }
}
