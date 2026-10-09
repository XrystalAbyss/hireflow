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
}
