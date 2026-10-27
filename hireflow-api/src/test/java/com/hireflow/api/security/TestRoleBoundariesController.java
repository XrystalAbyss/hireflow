package com.hireflow.api.security;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Test-only controller configured to verify role-based authorization boundaries
 * without exposing production-accessible fake dashboards.
 */
@RestController
@RequestMapping("/api/v1/test")
public class TestRoleBoundariesController {

    @GetMapping("/student-only")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Map<String, String>> studentOnly() {
        return ResponseEntity.ok(Map.of("scope", "STUDENT_RESTRICTED", "access", "GRANTED"));
    }

    @GetMapping("/officer-only")
    @PreAuthorize("hasAnyRole('PLACEMENT_OFFICER', 'ADMINISTRATOR')")
    public ResponseEntity<Map<String, String>> officerOnly() {
        return ResponseEntity.ok(Map.of("scope", "OFFICER_RESTRICTED", "access", "GRANTED"));
    }

    @GetMapping("/admin-only")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<Map<String, String>> adminOnly() {
        return ResponseEntity.ok(Map.of("scope", "ADMIN_RESTRICTED", "access", "GRANTED"));
    }
}
