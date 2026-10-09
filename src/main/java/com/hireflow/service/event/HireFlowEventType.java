package com.hireflow.service.event;

/**
 * Enumeration of domain events published throughout the recruitment lifecycle.
 */
public enum HireFlowEventType {
    STUDENT_REGISTERED("Candidate Registered"),
    COMPANY_ADDED("Company Partner Added"),
    JOB_DRIVE_CREATED("Placement Drive Created"),
    APPLICATION_SUBMITTED("Application Submitted"),
    APPLICATION_SHORTLISTED("Application Shortlisted"),
    INTERVIEW_SCHEDULED("Interview Scheduled"),
    INTERVIEW_COMPLETED("Interview Completed"),
    OFFER_EXTENDED("Job Offer Extended"),
    APPLICATION_REJECTED("Candidate Rejected"),
    CONFLICT_PREVENTED("Schedule Conflict Prevented");

    private final String description;

    HireFlowEventType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
