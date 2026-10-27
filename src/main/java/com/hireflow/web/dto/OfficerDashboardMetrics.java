package com.hireflow.web.dto;

import com.hireflow.model.RecruitmentDrive;

import java.util.Collections;
import java.util.List;

/**
 * Summary metrics container for the TPC Officer dashboard.
 */
public class OfficerDashboardMetrics {

    private final int totalCompanies;
    private final int totalDrives;
    private final int activeDrives;
    private final int totalApplications;
    private final int shortlistedApplications;
    private final int scheduledInterviews;
    private final List<RecruitmentDrive> upcomingDrives;

    public OfficerDashboardMetrics(int totalCompanies, int totalDrives, int activeDrives,
                                   int totalApplications, int shortlistedApplications,
                                   int scheduledInterviews, List<RecruitmentDrive> upcomingDrives) {
        this.totalCompanies = totalCompanies;
        this.totalDrives = totalDrives;
        this.activeDrives = activeDrives;
        this.totalApplications = totalApplications;
        this.shortlistedApplications = shortlistedApplications;
        this.scheduledInterviews = scheduledInterviews;
        this.upcomingDrives = upcomingDrives != null ? List.copyOf(upcomingDrives) : Collections.emptyList();
    }

    public int getTotalCompanies() {
        return totalCompanies;
    }

    public int getTotalDrives() {
        return totalDrives;
    }

    public int getActiveDrives() {
        return activeDrives;
    }

    public int getTotalApplications() {
        return totalApplications;
    }

    public int getShortlistedApplications() {
        return shortlistedApplications;
    }

    public int getScheduledInterviews() {
        return scheduledInterviews;
    }

    public List<RecruitmentDrive> getUpcomingDrives() {
        return upcomingDrives;
    }
}
