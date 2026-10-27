package com.hireflow.policy;

import com.hireflow.exception.ValidationException;
import com.hireflow.model.Room;

import java.util.*;

/**
 * Institutional Campus Resource Policy for University Engineering Interview Facilities.
 *
 * Campus Architecture Governance:
 *  - University engineering facilities are housed across Block K and Block L.
 *  - Upper floors: First (prefix KF/LF), Second (prefix KS/LS), and Third (prefix KT/LT).
 *  - Valid bookable interview rooms on each upper floor: 02, 03, 04, 08, 09, 10 (exactly 36 rooms).
 *  - Excluded faculty / amenities rooms: 01, 05, 06, 07 (strictly non-bookable).
 *  - Ground-floor laboratories: K Block Labs 1–5 and L Block Labs 6–9 (strictly non-bookable).
 *  - Legacy rooms (RM-101, RM-DEMO-*, RM-DEMO-VIVA22): preserved for historical reads.
 */
public final class UniversityCampusRoomPolicy {

    public static final Set<String> VALID_ROOM_CODES = Set.of(
            // K Block - First Floor
            "KF02", "KF03", "KF04", "KF08", "KF09", "KF10",
            // K Block - Second Floor
            "KS02", "KS03", "KS04", "KS08", "KS09", "KS10",
            // K Block - Third Floor
            "KT02", "KT03", "KT04", "KT08", "KT09", "KT10",
            // L Block - First Floor
            "LF02", "LF03", "LF04", "LF08", "LF09", "LF10",
            // L Block - Second Floor
            "LS02", "LS03", "LS04", "LS08", "LS09", "LS10",
            // L Block - Third Floor
            "LT02", "LT03", "LT04", "LT08", "LT09", "LT10"
    );

    public static final Set<String> EXCLUDED_FACULTY_CODES = Set.of(
            // Faculty / amenities rooms on K Block upper floors
            "KF01", "KF05", "KF06", "KF07",
            "KS01", "KS05", "KS06", "KS07",
            "KT01", "KT05", "KT06", "KT07",
            // Faculty / amenities rooms on L Block upper floors
            "LF01", "LF05", "LF06", "LF07",
            "LS01", "LS05", "LS06", "LS07",
            "LT01", "LT05", "LT06", "LT07"
    );

    public static final Set<String> GROUND_FLOOR_LAB_PATTERNS = Set.of(
            "K-LAB-1", "K-LAB-2", "K-LAB-3", "K-LAB-4", "K-LAB-5",
            "L-LAB-6", "L-LAB-7", "L-LAB-8", "L-LAB-9"
    );

    /**
     * Exact immutable set of preserved historical legacy room identifiers.
     * Prevents over-permissive wildcard registration while retaining full backward
     * compatibility for existing database records and historical test fixtures.
     */
    public static final Set<String> PRESERVED_LEGACY_ROOM_IDS = Set.of(
            "RM-101", "RM-DEMO-01", "RM-DEMO-02", "RM-DEMO-03", "RM-DEMO-VIVA22",
            "RM-102", "RM-201", "RM-BUSY", "RM-A", "RM-B", "RM-CONT-01",
            "RM-REF-99", "RM-SCHED-01", "RM-PUB-01", "E2E-RM-01"
    );

    /**
     * Narrow set of test-fixture room identifiers required strictly by historical integration test suites.
     * These are never allowed in live production and only authorized when test-context is explicitly active.
     */
    public static final Set<String> HISTORICAL_TEST_FIXTURE_ROOM_IDS = Set.of(
            "RM-NEW-01", "RM-E2E-99", "RM-E2E-SCH"
    );

    private static volatile boolean testContextRegistrationAllowed = false;

    public static void setTestContextRegistrationAllowed(boolean allowed) {
        testContextRegistrationAllowed = allowed;
    }

    public static boolean isTestContextRegistrationAllowed() {
        return testContextRegistrationAllowed ||
                Boolean.getBoolean("hireflow.test.allow-test-rooms") ||
                "true".equalsIgnoreCase(System.getenv("HIREFLOW_TEST_ROOMS_ALLOWED"));
    }

    private UniversityCampusRoomPolicy() {
    }

    /**
     * Normalizes a room identifier or code by stripping leading 'RM-' if present and trimming to uppercase.
     */
    public static String normalizeCode(String idOrCode) {
        if (idOrCode == null) return "";
        String code = idOrCode.trim().toUpperCase();
        if (code.startsWith("RM-")) {
            return code.substring(3);
        }
        return code;
    }

    /**
     * Checks if the code matches one of the 36 approved engineering interview rooms.
     */
    public static boolean isApprovedEngineeringRoom(String idOrCode) {
        return VALID_ROOM_CODES.contains(normalizeCode(idOrCode));
    }

    /**
     * Checks if the code matches one of the excluded faculty / amenities rooms.
     */
    public static boolean isExcludedFacultyRoom(String idOrCode) {
        return EXCLUDED_FACULTY_CODES.contains(normalizeCode(idOrCode));
    }

    /**
     * Checks if the room represents a ground-floor laboratory or contains laboratory markers.
     */
    public static boolean isLaboratoryFacility(String idOrCode, String name) {
        String norm = normalizeCode(idOrCode);
        if (GROUND_FLOOR_LAB_PATTERNS.contains(norm)) {
            return true;
        }
        if (name != null) {
            String upper = name.toUpperCase();
            if (upper.contains("LABORATORY") || upper.contains("GROUND FLOOR LAB")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the room matches the exact set of recognized historical legacy rooms.
     * Arbitrary unapproved room codes or prefixes are strictly rejected.
     */
    public static boolean isLegacyOrTestRoom(String id) {
        if (id == null) return false;
        String upper = id.trim().toUpperCase();
        return PRESERVED_LEGACY_ROOM_IDS.contains(upper) || HISTORICAL_TEST_FIXTURE_ROOM_IDS.contains(upper);
    }

    /**
     * Determines if a room is eligible for interview allocation.
     * Approved engineering rooms are eligible; legacy/test rooms are eligible for compatibility;
     * faculty amenities and ground-floor laboratories are strictly non-eligible.
     */
    public static boolean isInterviewEligible(Room room) {
        if (room == null) return false;
        String id = room.getId();
        String name = room.getName();

        if (isExcludedFacultyRoom(id)) {
            return false;
        }
        if (isLaboratoryFacility(id, name)) {
            return false;
        }
        return isApprovedEngineeringRoom(id) || isLegacyOrTestRoom(id);
    }

    /**
     * Validates that a newly created interview room strictly belongs to the approved
     * 36-room university engineering inventory (K/L Block upper floors).
     * Prevents placement officers from registering arbitrary or unapproved room numbers.
     */
    public static void validateRoomForCreation(Room room) {
        if (room == null) {
            throw new ValidationException("Interview room cannot be null.");
        }
        String id = room.getId();
        String name = room.getName();

        if (isExcludedFacultyRoom(id)) {
            throw new ValidationException(String.format(
                    "Cannot register room '%s'. Room is designated for faculty offices or student amenities and cannot be used for interview sessions.",
                    id));
        }
        if (isLaboratoryFacility(id, name)) {
            throw new ValidationException(String.format(
                    "Cannot register facility '%s' ('%s'). Ground-floor engineering laboratories cannot be registered as interview rooms.",
                    id, name));
        }

        boolean isApproved = isApprovedEngineeringRoom(id);
        boolean isTestFixtureAllowed = isTestContextRegistrationAllowed() && HISTORICAL_TEST_FIXTURE_ROOM_IDS.contains(id.trim().toUpperCase());

        if (!isApproved && !isTestFixtureAllowed) {
            throw new ValidationException(String.format(
                    "Room code '%s' is not an approved university engineering interview venue. Newly registered rooms must belong to the approved 36-room inventory across K and L Block upper floors (e.g. KF02..KT10, LF02..LT10). Legacy and test identifiers cannot be used for new room registration in live production.",
                    id));
        }
        if (room.getCapacity() <= 0) {
            throw new ValidationException(String.format(
                    "Room '%s' has invalid capacity (%d). Seating capacity must be greater than zero.",
                    id, room.getCapacity()));
        }
    }

    /**
     * Validates that a room is strictly approved for interview scheduling and timetable allocation.
     * Throws {@link ValidationException} with informative reason if ineligible.
     */
    public static void validateRoomForInterviewAllocation(Room room) {
        if (room == null) {
            throw new ValidationException("Interview room cannot be null.");
        }
        String id = room.getId();
        String name = room.getName();

        if (isExcludedFacultyRoom(id)) {
            throw new ValidationException(String.format(
                    "Room '%s' is designated for faculty offices or student amenities and cannot be allocated for interview sessions.",
                    id));
        }
        if (isLaboratoryFacility(id, name)) {
            throw new ValidationException(String.format(
                    "Facility '%s' ('%s') is an engineering laboratory and cannot be booked as an interview room.",
                    id, name));
        }
        if (!isApprovedEngineeringRoom(id) && !isLegacyOrTestRoom(id)) {
            throw new ValidationException(String.format(
                    "Room '%s' is not an approved university interview venue under campus resource policy. Approved inventory comprises K and L Block upper floors.",
                    id));
        }
        if (room.getCapacity() <= 0) {
            throw new ValidationException(String.format(
                    "Room '%s' has invalid capacity (%d). Seating capacity must be greater than zero.",
                    id, room.getCapacity()));
        }
    }

    /**
     * Resolves the campus block from room code.
     */
    public static String getBlock(String idOrCode) {
        String code = normalizeCode(idOrCode);
        if (code.startsWith("K")) return "K Block";
        if (code.startsWith("L")) return "L Block";
        return "Campus Center";
    }

    /**
     * Resolves floor description from room code.
     */
    public static String getFloor(String idOrCode) {
        String code = normalizeCode(idOrCode);
        if (code.length() < 2) return "Ground Floor";
        char floorChar = code.charAt(1);
        return switch (floorChar) {
            case 'F' -> "First Floor";
            case 'S' -> "Second Floor";
            case 'T' -> "Third Floor";
            default -> "Ground Floor";
        };
    }

    /**
     * Generates standard descriptive name for a valid engineering room code.
     */
    public static String formatStandardRoomName(String code) {
        String norm = normalizeCode(code);
        String block = getBlock(norm);
        String floor = getFloor(norm);
        return String.format("%s %s - Room %s", block, floor, norm);
    }
}
