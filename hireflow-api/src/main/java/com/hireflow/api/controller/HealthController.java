package com.hireflow.api.controller;

import com.hireflow.api.dto.HealthStatusDto;
import com.hireflow.api.service.CompanyCatalogService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Health check endpoint providing read-only diagnostic telemetry.
 */
@RestController
@RequestMapping(path = "/api/v1/health", produces = MediaType.APPLICATION_JSON_VALUE)
public class HealthController {

    private final CompanyCatalogService companyCatalogService;

    public HealthController(CompanyCatalogService companyCatalogService) {
        this.companyCatalogService = companyCatalogService;
    }

    @GetMapping
    public ResponseEntity<HealthStatusDto> checkHealth() {
        // Confirm active database connectivity by executing a lightweight read query
        companyCatalogService.countCompanies();
        HealthStatusDto health = HealthStatusDto.healthy("CONNECTED", "FLYWAY_VALIDATED");
        return ResponseEntity.ok(health);
    }
}
