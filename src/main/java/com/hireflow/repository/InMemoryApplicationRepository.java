package com.hireflow.repository;

import com.hireflow.exception.DuplicateEntityException;
import com.hireflow.model.Application;
import com.hireflow.model.ApplicationStatus;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * In-memory thread-safe implementation of ApplicationRepository.
 */
public class InMemoryApplicationRepository extends AbstractInMemoryRepository<Application, String> implements ApplicationRepository {

    public InMemoryApplicationRepository() {
        super(Application::getId, "Application");
    }

    @Override
    public Application add(Application application) {
        if (application != null) {
            if (existsByStudentAndDrive(application.getStudentId(), application.getDriveId())) {
                throw new DuplicateEntityException("Student '" + application.getStudentId() +
                        "' has already applied to Drive '" + application.getDriveId() + "'.");
            }
        }
        return super.add(application);
    }

    @Override
    public List<Application> findByStudentId(String studentId) {
        if (studentId == null) return Collections.emptyList();
        String normalized = studentId.trim().toUpperCase();
        List<Application> list = store.values().stream()
                .filter(a -> a.getStudentId().equalsIgnoreCase(normalized))
                .toList();
        return Collections.unmodifiableList(list);
    }

    @Override
    public List<Application> findByDriveId(String driveId) {
        if (driveId == null) return Collections.emptyList();
        String normalized = driveId.trim().toUpperCase();
        List<Application> list = store.values().stream()
                .filter(a -> a.getDriveId().equalsIgnoreCase(normalized))
                .toList();
        return Collections.unmodifiableList(list);
    }

    @Override
    public List<Application> findByStatus(ApplicationStatus status) {
        if (status == null) return Collections.emptyList();
        List<Application> list = store.values().stream()
                .filter(a -> a.getStatus() == status)
                .toList();
        return Collections.unmodifiableList(list);
    }

    @Override
    public Optional<Application> findByStudentAndDrive(String studentId, String driveId) {
        if (studentId == null || driveId == null) return Optional.empty();
        String sNorm = studentId.trim().toUpperCase();
        String dNorm = driveId.trim().toUpperCase();
        return store.values().stream()
                .filter(a -> a.getStudentId().equalsIgnoreCase(sNorm) && a.getDriveId().equalsIgnoreCase(dNorm))
                .findFirst();
    }

    @Override
    public boolean existsByStudentAndDrive(String studentId, String driveId) {
        return findByStudentAndDrive(studentId, driveId).isPresent();
    }

    @Override
    public synchronized boolean updateStatusConditional(String id, ApplicationStatus expectedStatus,
                                                        ApplicationStatus newStatus, String remarks) {
        if (id == null) return false;
        String normId = id.trim().toUpperCase();
        Application app = store.get(normId);
        if (app == null || app.getStatus() != expectedStatus) {
            return false;
        }
        app.setStatus(newStatus);
        if (remarks != null) {
            app.setRemarks(remarks.trim());
        }
        return true;
    }
}
