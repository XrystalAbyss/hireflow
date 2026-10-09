package com.hireflow.repository;

import com.hireflow.model.RecruitmentDrive;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * In-memory thread-safe implementation of DriveRepository.
 */
public class InMemoryDriveRepository extends AbstractInMemoryRepository<RecruitmentDrive, String> implements DriveRepository {

    public InMemoryDriveRepository() {
        super(RecruitmentDrive::getId, "RecruitmentDrive");
    }

    @Override
    public List<RecruitmentDrive> findByCompanyId(String companyId) {
        if (companyId == null) return Collections.emptyList();
        String normalized = companyId.trim().toUpperCase();
        List<RecruitmentDrive> list = store.values().stream()
                .filter(d -> d.getCompanyId().equalsIgnoreCase(normalized))
                .toList();
        return Collections.unmodifiableList(list);
    }

    @Override
    public List<RecruitmentDrive> findActiveDrives(LocalDate asOfDate) {
        LocalDate date = asOfDate != null ? asOfDate : LocalDate.now();
        List<RecruitmentDrive> list = store.values().stream()
                .filter(d -> !d.isDeadlinePassed(date))
                .toList();
        return Collections.unmodifiableList(list);
    }
}
