package com.hireflow.service;

import com.hireflow.exception.DuplicateEntityException;
import com.hireflow.exception.EntityNotFoundException;
import com.hireflow.exception.ValidationException;
import com.hireflow.model.RecruitmentDrive;
import com.hireflow.repository.CompanyRepository;
import com.hireflow.repository.DriveRepository;

import com.hireflow.service.event.HireFlowEvent;
import com.hireflow.service.event.HireFlowEventListener;
import com.hireflow.service.event.HireFlowEventType;

import com.hireflow.repository.ApplicationRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Service managing recruitment drive creation, updates, and listings.
 */
public class RecruitmentDriveService {
    private final DriveRepository driveRepository;
    private final CompanyRepository companyRepository;
    private final ApplicationRepository applicationRepository;
    private final List<HireFlowEventListener> eventListeners = new CopyOnWriteArrayList<>();

    public RecruitmentDriveService(DriveRepository driveRepository, CompanyRepository companyRepository) {
        this(driveRepository, companyRepository, null);
    }

    public RecruitmentDriveService(DriveRepository driveRepository, CompanyRepository companyRepository, ApplicationRepository applicationRepository) {
        if (driveRepository == null || companyRepository == null) {
            throw new IllegalArgumentException("Repositories cannot be null.");
        }
        this.driveRepository = driveRepository;
        this.companyRepository = companyRepository;
        this.applicationRepository = applicationRepository;
    }

    public void addEventListener(HireFlowEventListener listener) {
        if (listener != null) {
            eventListeners.add(listener);
        }
    }

    public void removeEventListener(HireFlowEventListener listener) {
        eventListeners.remove(listener);
    }

    private void publishEvent(HireFlowEventType type, String sourceId, String message) {
        HireFlowEvent event = new HireFlowEvent(type, sourceId, message);
        for (HireFlowEventListener listener : eventListeners) {
            try {
                listener.onEvent(event);
            } catch (Exception ignored) {
            }
        }
    }

    public RecruitmentDrive createDrive(String id, String companyId, String jobTitle,
                                        double minimumCgpa, Set<String> eligibleBranches,
                                        LocalDate applicationDeadline) {
        if (id == null || id.trim().isEmpty()) {
            throw new ValidationException("Recruitment Drive ID is required.");
        }
        if (companyId == null || companyId.trim().isEmpty()) {
            throw new ValidationException("Company ID is required.");
        }

        String normCompanyId = companyId.trim().toUpperCase();
        if (!companyRepository.existsById(normCompanyId)) {
            throw new EntityNotFoundException("Cannot create drive for non-existent company ID: '" + normCompanyId + "'.");
        }

        String normDriveId = id.trim().toUpperCase();
        if (driveRepository.existsById(normDriveId)) {
            throw new DuplicateEntityException("Recruitment Drive with ID '" + normDriveId + "' already exists.");
        }

        RecruitmentDrive drive = new RecruitmentDrive(normDriveId, normCompanyId, jobTitle, minimumCgpa, eligibleBranches, applicationDeadline);
        RecruitmentDrive saved = driveRepository.add(drive);
        publishEvent(HireFlowEventType.JOB_DRIVE_CREATED, saved.getId(), "Placement drive created: " + saved.getJobTitle());
        return saved;
    }

    public RecruitmentDrive createDrive(RecruitmentDrive drive) {
        if (drive == null) {
            throw new ValidationException("Recruitment drive cannot be null.");
        }
        if (!companyRepository.existsById(drive.getCompanyId())) {
            throw new EntityNotFoundException("Company ID '" + drive.getCompanyId() + "' not found.");
        }
        RecruitmentDrive saved = driveRepository.add(drive);
        publishEvent(HireFlowEventType.JOB_DRIVE_CREATED, saved.getId(), "Placement drive created: " + saved.getJobTitle());
        return saved;
    }

    public RecruitmentDrive updateDrive(RecruitmentDrive drive) {
        if (drive == null) {
            throw new ValidationException("Recruitment drive cannot be null.");
        }
        if (!driveRepository.existsById(drive.getId())) {
            throw new EntityNotFoundException("Recruitment Drive with ID '" + drive.getId() + "' was not found.");
        }
        if (!companyRepository.existsById(drive.getCompanyId())) {
            throw new EntityNotFoundException("Company ID '" + drive.getCompanyId() + "' not found.");
        }
        return driveRepository.update(drive);
    }

    public Optional<RecruitmentDrive> getDriveById(String id) {
        if (id == null || id.trim().isEmpty()) return Optional.empty();
        return driveRepository.findById(id.trim());
    }

    public RecruitmentDrive getDriveOrThrow(String id) {
        return getDriveById(id)
                .orElseThrow(() -> new EntityNotFoundException("Recruitment Drive with ID '" + id + "' not found."));
    }

    public List<RecruitmentDrive> getAllDrives() {
        return driveRepository.findAll();
    }

    public List<RecruitmentDrive> getActiveDrives() {
        return driveRepository.findActiveDrives(LocalDate.now());
    }

    public List<RecruitmentDrive> getActiveDrives(LocalDate asOfDate) {
        return driveRepository.findActiveDrives(asOfDate);
    }

    public List<RecruitmentDrive> getDrivesForCompany(String companyId) {
        if (companyId == null) return List.of();
        return driveRepository.findByCompanyId(companyId.trim().toUpperCase());
    }

    public boolean deleteDrive(String id) {
        if (id == null || id.trim().isEmpty()) return false;
        String normId = id.trim().toUpperCase();

        if (applicationRepository != null) {
            List<com.hireflow.model.Application> apps = applicationRepository.findByDriveId(normId);
            if (!apps.isEmpty()) {
                throw new ValidationException(String.format(
                        "Cannot delete recruitment drive '%s' because %d application(s) reference this drive.",
                        normId, apps.size()
                ));
            }
        } else {
            throw new IllegalStateException("Cannot delete recruitment drive: ApplicationRepository is required for referential integrity enforcement.");
        }
        return driveRepository.deleteById(normId);
    }

    public int getTotalDrivesCount() {
        return driveRepository.count();
    }
}
