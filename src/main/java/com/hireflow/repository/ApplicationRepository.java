package com.hireflow.repository;

import com.hireflow.model.Application;
import com.hireflow.model.ApplicationStatus;

import java.util.List;
import java.util.Optional;

/**
 * Persistence contracts for candidate Application entity operations.
 */
public interface ApplicationRepository extends CrudRepository<Application, String> {

    List<Application> findByStudentId(String studentId);

    List<Application> findByDriveId(String driveId);

    List<Application> findByStatus(ApplicationStatus status);

    Optional<Application> findByStudentAndDrive(String studentId, String driveId);

    boolean existsByStudentAndDrive(String studentId, String driveId);

    default Optional<Application> findByStudentIdAndDriveId(String studentId, String driveId) {
        return findByStudentAndDrive(studentId, driveId);
    }

    default Optional<Application> findByIdForUpdate(String id) {
        return findById(id);
    }

    default boolean updateStatusConditional(String id, ApplicationStatus expectedStatus, ApplicationStatus newStatus, String remarks) {
        Optional<Application> opt = findById(id);
        if (opt.isEmpty() || opt.get().getStatus() != expectedStatus) {
            return false;
        }
        Application app = opt.get();
        app.setStatus(newStatus);
        if (remarks != null) {
            app.setRemarks(remarks);
        }
        update(app);
        return true;
    }
}
