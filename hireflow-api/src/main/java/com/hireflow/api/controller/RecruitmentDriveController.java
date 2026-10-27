package com.hireflow.api.controller;

import com.hireflow.api.dto.PagedResponse;
import com.hireflow.api.dto.RecruitmentDriveDto;
import com.hireflow.api.service.RecruitmentDriveCatalogService;
import com.hireflow.exception.EntityNotFoundException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * Public, read-only REST controller for recruitment drives.
 */
@RestController
@RequestMapping(path = "/api/v1/drives", produces = MediaType.APPLICATION_JSON_VALUE)
public class RecruitmentDriveController {

    private static final Set<String> ALLOWED_SORTS = Set.of(
            "id", "jobtitle", "minimumcgpa", "applicationdeadline", "createdat"
    );
    private static final Set<String> ALLOWED_DIRECTIONS = Set.of("ASC", "DESC");

    private final RecruitmentDriveCatalogService driveCatalogService;

    public RecruitmentDriveController(RecruitmentDriveCatalogService driveCatalogService) {
        this.driveCatalogService = driveCatalogService;
    }

    @GetMapping
    public ResponseEntity<PagedResponse<RecruitmentDriveDto>> listDrives(
            @RequestParam(name = "companyId", required = false) String companyId,
            @RequestParam(name = "activeOnly", defaultValue = "false") boolean activeOnly,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "sortBy", defaultValue = "applicationDeadline") String sortBy,
            @RequestParam(name = "direction", defaultValue = "ASC") String direction) {

        validatePaginationAndSort(page, size, sortBy, direction);

        PagedResponse<RecruitmentDriveDto> response = driveCatalogService.getDrives(
                companyId, activeOnly, page, size, sortBy, direction
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RecruitmentDriveDto> getDriveById(@PathVariable("id") String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Recruitment Drive ID cannot be blank.");
        }
        return driveCatalogService.getDriveById(id.trim())
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new EntityNotFoundException("Recruitment Drive with ID '" + id + "' was not found."));
    }

    private void validatePaginationAndSort(int page, int size, String sortBy, String direction) {
        if (page < 0) {
            throw new IllegalArgumentException("Page index cannot be negative. Provided: " + page);
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("Page size must be between 1 and 100. Provided: " + size);
        }
        if (sortBy == null || !ALLOWED_SORTS.contains(sortBy.trim().toLowerCase())) {
            throw new IllegalArgumentException("Invalid sort parameter '" + sortBy + "'. Allowed: id, jobTitle, minimumCgpa, applicationDeadline, createdAt.");
        }
        if (direction == null || !ALLOWED_DIRECTIONS.contains(direction.trim().toUpperCase())) {
            throw new IllegalArgumentException("Invalid direction '" + direction + "'. Allowed: ASC, DESC.");
        }
    }
}
