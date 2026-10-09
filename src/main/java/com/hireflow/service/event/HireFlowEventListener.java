package com.hireflow.service.event;

/**
 * Functional interface for subscribing to domain events in HireFlow.
 */
@FunctionalInterface
public interface HireFlowEventListener {
    void onEvent(HireFlowEvent event);
}
