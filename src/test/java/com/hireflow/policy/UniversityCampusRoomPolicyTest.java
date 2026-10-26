package com.hireflow.policy;

import com.hireflow.exception.ValidationException;
import com.hireflow.model.Room;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit Test Suite for UniversityCampusRoomPolicy.
 * Verifies exact 36-room engineering inventory, floor/block prefixes, exclusions of faculty rooms
 * and ground-floor laboratories, capacity validation, and legacy room preservation.
 */
public class UniversityCampusRoomPolicyTest {

    @Test
    @DisplayName("Phase C: Exactly 36 engineering interview-room codes are defined and approved")
    void testExact36RoomInventory() {
        assertEquals(36, UniversityCampusRoomPolicy.VALID_ROOM_CODES.size(),
                "Inventory must contain exactly 36 approved engineering interview rooms.");

        // Check K Block
        String[] kFloors = {"KF", "KS", "KT"};
        String[] validNumbers = {"02", "03", "04", "08", "09", "10"};
        for (String floor : kFloors) {
            for (String num : validNumbers) {
                String code = floor + num;
                assertTrue(UniversityCampusRoomPolicy.isApprovedEngineeringRoom(code),
                        "Room code " + code + " must be approved in K Block");
            }
        }

        // Check L Block
        String[] lFloors = {"LF", "LS", "LT"};
        for (String floor : lFloors) {
            for (String num : validNumbers) {
                String code = floor + num;
                assertTrue(UniversityCampusRoomPolicy.isApprovedEngineeringRoom(code),
                        "Room code " + code + " must be approved in L Block");
            }
        }
    }

    @Test
    @DisplayName("Phase C: Excluded faculty rooms (01, 05, 06, 07) across all floors are strictly non-bookable")
    void testExcludedFacultyRooms() {
        String[] allFloors = {"KF", "KS", "KT", "LF", "LS", "LT"};
        String[] excludedNumbers = {"01", "05", "06", "07"};

        assertEquals(24, UniversityCampusRoomPolicy.EXCLUDED_FACULTY_CODES.size(),
                "Exactly 24 faculty/amenity room codes must be excluded (6 floors x 4 rooms)");

        for (String floor : allFloors) {
            for (String num : excludedNumbers) {
                String code = floor + num;
                assertTrue(UniversityCampusRoomPolicy.isExcludedFacultyRoom(code),
                        "Room " + code + " must be recognized as an excluded faculty/amenity room");
                assertFalse(UniversityCampusRoomPolicy.isApprovedEngineeringRoom(code),
                        "Room " + code + " must NOT be in approved interview rooms");

                Room room = new Room(code, "Faculty Office " + code, 4);
                assertFalse(UniversityCampusRoomPolicy.isInterviewEligible(room),
                        "Faculty room " + code + " must not be interview-eligible");

                ValidationException ex = assertThrows(ValidationException.class, () ->
                        UniversityCampusRoomPolicy.validateRoomForInterviewAllocation(room));
                assertTrue(ex.getMessage().contains("designated for faculty"),
                        "Expected faculty rejection message: " + ex.getMessage());
            }
        }
    }

    @Test
    @DisplayName("Phase C: Ground floor laboratories are non-bookable facilities")
    void testGroundFloorLaboratories() {
        // K Block Labs 1-5, L Block Labs 6-9
        for (int i = 1; i <= 5; i++) {
            Room lab = new Room("K-LAB-" + i, "K Block Ground Floor - Laboratory " + i, 30);
            assertFalse(UniversityCampusRoomPolicy.isInterviewEligible(lab));
            assertThrows(ValidationException.class, () ->
                    UniversityCampusRoomPolicy.validateRoomForInterviewAllocation(lab));
        }

        for (int i = 6; i <= 9; i++) {
            Room lab = new Room("L-LAB-" + i, "L Block Ground Floor - Laboratory " + i, 30);
            assertFalse(UniversityCampusRoomPolicy.isInterviewEligible(lab));
            assertThrows(ValidationException.class, () ->
                    UniversityCampusRoomPolicy.validateRoomForInterviewAllocation(lab));
        }
    }

    @Test
    @DisplayName("Phase C: Legacy rooms are preserved and bookable for backward compatibility")
    void testLegacyRoomsPreserved() {
        Set<String> legacyIds = Set.of("RM-101", "RM-DEMO-01", "RM-DEMO-02", "RM-DEMO-03", "RM-DEMO-VIVA22");
        for (String id : legacyIds) {
            Room legacyRoom = new Room(id, "Legacy Room " + id, 4);
            assertTrue(UniversityCampusRoomPolicy.isInterviewEligible(legacyRoom),
                    "Legacy room " + id + " must remain interview-eligible for compatibility");
            assertDoesNotThrow(() -> UniversityCampusRoomPolicy.validateRoomForInterviewAllocation(legacyRoom));
        }
    }

    @Test
    @DisplayName("Phase C: Normalization and naming conventions")
    void testNormalizationAndLabels() {
        assertEquals("KF02", UniversityCampusRoomPolicy.normalizeCode("RM-KF02"));
        assertEquals("KF02", UniversityCampusRoomPolicy.normalizeCode("kf02"));
        assertEquals("LT10", UniversityCampusRoomPolicy.normalizeCode("  RM-lt10  "));

        assertEquals("K Block", UniversityCampusRoomPolicy.getBlock("KF02"));
        assertEquals("L Block", UniversityCampusRoomPolicy.getBlock("LS08"));
        assertEquals("First Floor", UniversityCampusRoomPolicy.getFloor("KF02"));
        assertEquals("Second Floor", UniversityCampusRoomPolicy.getFloor("KS04"));
        assertEquals("Third Floor", UniversityCampusRoomPolicy.getFloor("KT10"));

        assertEquals("K Block First Floor - Room KF02",
                UniversityCampusRoomPolicy.formatStandardRoomName("KF02"));
        assertEquals("L Block Third Floor - Room LT10",
                UniversityCampusRoomPolicy.formatStandardRoomName("LT10"));
    }

    @Test
    @DisplayName("Phase C: Capacity must be strictly positive")
    void testCapacityValidation() {
        Room validRoom = new Room("KF02", "K Block First Floor - Room KF02", 4);
        assertDoesNotThrow(() -> UniversityCampusRoomPolicy.validateRoomForInterviewAllocation(validRoom));

        assertThrows(ValidationException.class, () -> new Room("KF02", "K Block First Floor - Room KF02", 0));
        assertThrows(ValidationException.class, () -> new Room("KF02", "K Block First Floor - Room KF02", -5));
    }
}
