package com.hireflow.scheduler;

import com.hireflow.exception.ValidationException;
import com.hireflow.model.Application;
import com.hireflow.model.ApplicationStatus;
import com.hireflow.model.InterviewSession;
import com.hireflow.model.Interviewer;
import com.hireflow.model.Room;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Greedy Interview Scheduling Engine Tests")
public class GreedySchedulerTest {

    private GreedyScheduler scheduler;
    private LocalDateTime baseTime;
    private TimeWindow defaultWindow;
    private Duration defaultDuration;

    @BeforeEach
    void setUp() {
        scheduler = new GreedyScheduler();
        baseTime = LocalDateTime.of(2026, 10, 15, 9, 0);
        defaultWindow = new TimeWindow(baseTime, baseTime.plusHours(4)); // 09:00 - 13:00 (4 hours)
        defaultDuration = Duration.ofMinutes(60); // 1 hour interviews
    }

    @Test
    @DisplayName("Two interviews requesting same interviewer and time are allocated without conflict")
    void testTwoInterviewsSameInterviewer() {
        // 2 candidates for Google (CMP-001) with only 1 interviewer and 1 room
        Application app1 = new Application("APP-001", "STU-001", "DRV-101", ApplicationStatus.SHORTLISTED);
        Application app2 = new Application("APP-002", "STU-002", "DRV-101", ApplicationStatus.SHORTLISTED);

        Interviewer interviewer = new Interviewer("INT-001", "Dr. Sarah Chen", "CMP-001");
        Room room = new Room("RM-101", "Interview Booth 1", 2);

        SchedulingRequest request = SchedulingRequest.builder()
                .applications(List.of(app1, app2))
                .interviewers(List.of(interviewer))
                .rooms(List.of(room))
                .schedulingWindow(defaultWindow)
                .interviewDuration(defaultDuration)
                .addDriveCompany("DRV-101", "CMP-001")
                .build();

        SchedulingResult result = scheduler.schedule(request);

        assertEquals(2, result.getTotalScheduled());
        assertEquals(0, result.getTotalUnscheduled());

        InterviewSession s1 = result.getScheduledSessions().get(0);
        InterviewSession s2 = result.getScheduledSessions().get(1);

        // They must NOT overlap
        assertFalse(s1.overlaps(s2.getStartTime(), s2.getEndTime()));
        assertEquals(interviewer.getId(), s1.getInterviewerId());
        assertEquals(interviewer.getId(), s2.getInterviewerId());
    }

    @Test
    @DisplayName("One student shortlisted by multiple companies cannot attend overlapping interviews")
    void testOneStudentMultipleCompanies() {
        // STU-001 shortlisted for Google (DRV-101) and Microsoft (DRV-102)
        Application appGoogle = new Application("APP-001", "STU-001", "DRV-101", ApplicationStatus.SHORTLISTED);
        Application appMicrosoft = new Application("APP-002", "STU-001", "DRV-102", ApplicationStatus.SHORTLISTED);

        Interviewer intGoogle = new Interviewer("INT-001", "Google Lead", "CMP-001");
        Interviewer intMicrosoft = new Interviewer("INT-002", "MS Lead", "CMP-002");

        Room room1 = new Room("RM-101", "Room 1", 2);
        Room room2 = new Room("RM-102", "Room 2", 2);

        // Even though 2 interviewers and 2 rooms are available at 09:00, STU-001 can only be in one interview!
        SchedulingRequest request = SchedulingRequest.builder()
                .applications(List.of(appGoogle, appMicrosoft))
                .interviewers(List.of(intGoogle, intMicrosoft))
                .rooms(List.of(room1, room2))
                .schedulingWindow(defaultWindow)
                .interviewDuration(defaultDuration)
                .addDriveCompany("DRV-101", "CMP-001")
                .addDriveCompany("DRV-102", "CMP-002")
                .build();

        SchedulingResult result = scheduler.schedule(request);

        assertEquals(2, result.getTotalScheduled());
        assertEquals(0, result.getTotalUnscheduled());

        InterviewSession s1 = result.getScheduledSessions().get(0);
        InterviewSession s2 = result.getScheduledSessions().get(1);

        // Student must not have overlapping sessions
        assertFalse(s1.overlaps(s2.getStartTime(), s2.getEndTime()));
        assertNotEquals(s1.getStartTime(), s2.getStartTime());
    }

    @Test
    @DisplayName("No available rooms results in explicit unscheduled entries")
    void testNoAvailableRooms() {
        Application app = new Application("APP-001", "STU-001", "DRV-101", ApplicationStatus.SHORTLISTED);
        Interviewer interviewer = new Interviewer("INT-001", "Alex", "CMP-001");

        SchedulingRequest request = SchedulingRequest.builder()
                .applications(List.of(app))
                .interviewers(List.of(interviewer))
                .rooms(List.of()) // Empty rooms
                .schedulingWindow(defaultWindow)
                .interviewDuration(defaultDuration)
                .build();

        SchedulingResult result = scheduler.schedule(request);

        assertEquals(0, result.getTotalScheduled());
        assertEquals(1, result.getTotalUnscheduled());
        assertTrue(result.getUnscheduledApplications().get(0).reason().contains("No interview rooms available"));
    }

    @Test
    @DisplayName("Insufficient scheduling capacity flags overflow candidates with reasons")
    void testInsufficientSchedulingCapacity() {
        // Window is only 2 hours (09:00 - 11:00), allowing only 2 slots of 1 hour
        TimeWindow tightWindow = new TimeWindow(baseTime, baseTime.plusHours(2));

        Application app1 = new Application("APP-001", "STU-001", "DRV-101", ApplicationStatus.SHORTLISTED);
        Application app2 = new Application("APP-002", "STU-002", "DRV-101", ApplicationStatus.SHORTLISTED);
        Application app3 = new Application("APP-003", "STU-003", "DRV-101", ApplicationStatus.SHORTLISTED);

        Interviewer interviewer = new Interviewer("INT-001", "Lead", "CMP-001");
        Room room = new Room("RM-101", "Solo Room", 2);

        SchedulingRequest request = SchedulingRequest.builder()
                .applications(List.of(app1, app2, app3))
                .interviewers(List.of(interviewer))
                .rooms(List.of(room))
                .schedulingWindow(tightWindow)
                .interviewDuration(defaultDuration)
                .build();

        SchedulingResult result = scheduler.schedule(request);

        assertEquals(3, result.getTotalRequested());
        assertEquals(2, result.getTotalScheduled());
        assertEquals(1, result.getTotalUnscheduled());

        UnscheduledApplication overflow = result.getUnscheduledApplications().get(0);
        assertNotNull(overflow.application());
        assertTrue(overflow.reason().contains("Insufficient scheduling capacity"));
    }

    @Test
    @DisplayName("Invalid or non-positive duration throws ValidationException")
    void testInvalidDuration() {
        SchedulingRequest.Builder builder = SchedulingRequest.builder()
                .schedulingWindow(defaultWindow);

        assertThrows(ValidationException.class, () -> builder.interviewDuration(Duration.ZERO).build());
        assertThrows(ValidationException.class, () -> builder.interviewDuration(Duration.ofMinutes(-15)).build());
        assertThrows(ValidationException.class, () -> builder.interviewDuration(null).build());
    }

    @Test
    @DisplayName("Interviewer unavailable during scheduling window results in unscheduled status")
    void testInterviewerUnavailable() {
        Application app = new Application("APP-001", "STU-001", "DRV-101", ApplicationStatus.SHORTLISTED);
        Interviewer interviewer = new Interviewer("INT-001", "Lead", "CMP-001");
        Room room = new Room("RM-101", "Room A", 2);

        // Interviewer availability is from 15:00 - 18:00, but scheduling window is 09:00 - 13:00
        TimeWindow interviewerWindow = new TimeWindow(baseTime.plusHours(6), baseTime.plusHours(9));

        SchedulingRequest request = SchedulingRequest.builder()
                .applications(List.of(app))
                .interviewers(List.of(interviewer))
                .addInterviewerAvailability(interviewer.getId(), List.of(interviewerWindow))
                .rooms(List.of(room))
                .schedulingWindow(defaultWindow)
                .interviewDuration(defaultDuration)
                .build();

        SchedulingResult result = scheduler.schedule(request);

        assertEquals(0, result.getTotalScheduled());
        assertEquals(1, result.getTotalUnscheduled());
        assertTrue(result.getUnscheduledApplications().get(0).reason().contains("Interviewer not available"));
    }

    @Test
    @DisplayName("Existing confirmed appointment is not modified and blocks conflicting slot")
    void testExistingConfirmedAppointmentPreserved() {
        // Pre-existing session at 09:00 - 10:00 in RM-101 with INT-001
        InterviewSession confirmed = new InterviewSession(
                "SES-CONFIRMED-001", "APP-EXISTING", "INT-001", "RM-101",
                baseTime, baseTime.plusHours(1)
        );

        Application newApp = new Application("APP-001", "STU-001", "DRV-101", ApplicationStatus.SHORTLISTED);
        Interviewer interviewer = new Interviewer("INT-001", "Lead", "CMP-001");
        Room room = new Room("RM-101", "Room A", 2);

        SchedulingRequest request = SchedulingRequest.builder()
                .applications(List.of(newApp))
                .interviewers(List.of(interviewer))
                .rooms(List.of(room))
                .existingSessions(List.of(confirmed))
                .schedulingWindow(defaultWindow)
                .interviewDuration(defaultDuration)
                .build();

        SchedulingResult result = scheduler.schedule(request);

        assertEquals(1, result.getTotalScheduled());
        InterviewSession proposed = result.getScheduledSessions().get(0);

        // Confirmed appointment was at 09:00 - 10:00. Proposed session MUST be scheduled at 10:00 or later!
        assertFalse(proposed.overlaps(confirmed.getStartTime(), confirmed.getEndTime()));
        assertTrue(!proposed.getStartTime().isBefore(confirmed.getEndTime()));
    }

    @Test
    @DisplayName("Successful schedule with multiple parallel resources allocates with zero conflicts")
    void testSuccessfulScheduleNoConflicts() {
        Application a1 = new Application("APP-001", "STU-001", "DRV-101", ApplicationStatus.SHORTLISTED);
        Application a2 = new Application("APP-002", "STU-002", "DRV-101", ApplicationStatus.SHORTLISTED);
        Application a3 = new Application("APP-003", "STU-003", "DRV-101", ApplicationStatus.SHORTLISTED);

        Interviewer i1 = new Interviewer("INT-001", "Lead 1", "CMP-001");
        Interviewer i2 = new Interviewer("INT-002", "Lead 2", "CMP-001");

        Room r1 = new Room("RM-101", "Booth 1", 2);
        Room r2 = new Room("RM-102", "Booth 2", 2);

        SchedulingRequest request = SchedulingRequest.builder()
                .applications(List.of(a1, a2, a3))
                .interviewers(List.of(i1, i2))
                .rooms(List.of(r1, r2))
                .schedulingWindow(defaultWindow)
                .interviewDuration(defaultDuration)
                .build();

        SchedulingResult result = scheduler.schedule(request);

        assertTrue(result.isFullyScheduled());
        assertEquals(3, result.getTotalScheduled());
        assertEquals(0, result.getTotalUnscheduled());

        List<InterviewSession> sessions = result.getScheduledSessions();
        // Verify no pair conflicts across all 3 sessions
        for (int i = 0; i < sessions.size(); i++) {
            for (int j = i + 1; j < sessions.size(); j++) {
                InterviewSession s1 = sessions.get(i);
                InterviewSession s2 = sessions.get(j);

                if (s1.overlaps(s2.getStartTime(), s2.getEndTime())) {
                    assertNotEquals(s1.getInterviewerId(), s2.getInterviewerId(), "Interviewer double booked!");
                    assertNotEquals(s1.getRoomId(), s2.getRoomId(), "Room double booked!");
                }
            }
        }
    }

    @Test
    @DisplayName("Non-shortlisted applications are explicitly rejected by the scheduler")
    void testNonShortlistedApplicationsRejected() {
        Application applied = new Application("APP-001", "STU-001", "DRV-101", ApplicationStatus.APPLIED);
        Application rejected = new Application("APP-002", "STU-002", "DRV-101", ApplicationStatus.REJECTED);

        Interviewer i1 = new Interviewer("INT-001", "Lead", "CMP-001");
        Room r1 = new Room("RM-101", "Booth", 2);

        SchedulingRequest request = SchedulingRequest.builder()
                .applications(List.of(applied, rejected))
                .interviewers(List.of(i1))
                .rooms(List.of(r1))
                .schedulingWindow(defaultWindow)
                .interviewDuration(defaultDuration)
                .build();

        SchedulingResult result = scheduler.schedule(request);

        assertEquals(0, result.getTotalScheduled());
        assertEquals(2, result.getTotalUnscheduled());
        assertTrue(result.getUnscheduledApplications().get(0).reason().contains("SHORTLISTED"));
        assertTrue(result.getUnscheduledApplications().get(1).reason().contains("SHORTLISTED"));
    }

    @Test
    @DisplayName("Repeated runs with same inputs produce identical deterministic output")
    void testDeterministicExecution() {
        Application a1 = new Application("APP-001", "STU-001", "DRV-101", ApplicationStatus.SHORTLISTED);
        Application a2 = new Application("APP-002", "STU-002", "DRV-101", ApplicationStatus.SHORTLISTED);

        Interviewer i1 = new Interviewer("INT-001", "Lead", "CMP-001");
        Room r1 = new Room("RM-101", "Booth", 2);

        SchedulingRequest request = SchedulingRequest.builder()
                .applications(List.of(a1, a2))
                .interviewers(List.of(i1))
                .rooms(List.of(r1))
                .schedulingWindow(defaultWindow)
                .interviewDuration(defaultDuration)
                .build();

        SchedulingResult result1 = scheduler.schedule(request);
        SchedulingResult result2 = scheduler.schedule(request);

        assertEquals(result1.getTotalScheduled(), result2.getTotalScheduled());
        assertEquals(result1.getScheduledSessions().get(0).getStartTime(),
                result2.getScheduledSessions().get(0).getStartTime());
        assertEquals(result1.getScheduledSessions().get(1).getStartTime(),
                result2.getScheduledSessions().get(1).getStartTime());
    }
}
