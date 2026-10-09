package com.hireflow.repository;

import com.hireflow.model.InterviewSession;

import java.util.Collections;
import java.util.List;

/**
 * In-memory thread-safe implementation of InterviewSessionRepository.
 */
public class InMemoryInterviewSessionRepository extends AbstractInMemoryRepository<InterviewSession, String> implements InterviewSessionRepository {

    public InMemoryInterviewSessionRepository() {
        super(InterviewSession::getId, "InterviewSession");
    }

    @Override
    public List<InterviewSession> findByApplicationId(String applicationId) {
        if (applicationId == null) return Collections.emptyList();
        String normalized = applicationId.trim().toUpperCase();
        List<InterviewSession> list = store.values().stream()
                .filter(s -> s.getApplicationId().equalsIgnoreCase(normalized))
                .toList();
        return Collections.unmodifiableList(list);
    }

    @Override
    public List<InterviewSession> findByInterviewerId(String interviewerId) {
        if (interviewerId == null) return Collections.emptyList();
        String normalized = interviewerId.trim().toUpperCase();
        List<InterviewSession> list = store.values().stream()
                .filter(s -> s.getInterviewerId().equalsIgnoreCase(normalized))
                .toList();
        return Collections.unmodifiableList(list);
    }

    @Override
    public List<InterviewSession> findByRoomId(String roomId) {
        if (roomId == null) return Collections.emptyList();
        String normalized = roomId.trim().toUpperCase();
        List<InterviewSession> list = store.values().stream()
                .filter(s -> s.getRoomId().equalsIgnoreCase(normalized))
                .toList();
        return Collections.unmodifiableList(list);
    }
}
