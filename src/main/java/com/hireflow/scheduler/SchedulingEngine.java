package com.hireflow.scheduler;

/**
 * Strategy interface for automated interview scheduling engines.
 */
public interface SchedulingEngine {

    /**
     * Generates a proposed interview schedule based on the provided request parameters.
     * Does not mutate persistent state or existing confirmed appointments.
     *
     * @param request the scheduling parameters and resource pools
     * @return the proposed scheduling result with scheduled and unscheduled entries
     */
    SchedulingResult schedule(SchedulingRequest request);
}
