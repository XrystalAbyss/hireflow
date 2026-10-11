package com.hireflow.repository;

import com.hireflow.model.InterviewSession;

import java.util.Collections;
import java.util.List;

/**
 * In-memory thread-safe implementation of InterviewSessionRepository.
 */
public class InMemoryInterviewSessionRepository extends AbstractInMemoryRepository<InterviewSession, String> implements InterviewSessionRepository {

    private final ApplicationRepository applicationRepository;
    private final java.util.Map<String, String> sessionStudentMap = new java.util.concurrent.ConcurrentHashMap<>();

    public InMemoryInterviewSessionRepository() {
        this(null);
    }

    public InMemoryInterviewSessionRepository(ApplicationRepository applicationRepository) {
        super(InterviewSession::getId, "InterviewSession");
        this.applicationRepository = applicationRepository;
    }

    public InterviewSession addWithStudentId(InterviewSession session, String candidateStudentId) {
        if (candidateStudentId != null) {
            sessionStudentMap.put(session.getId().trim().toUpperCase(), candidateStudentId.trim().toUpperCase());
        }
        return add(session);
    }

    @Override
    public boolean deleteById(String id) {
        if (id != null) {
            sessionStudentMap.remove(id.trim().toUpperCase());
        }
        return super.deleteById(id);
    }

    @Override
    public void clear() {
        sessionStudentMap.clear();
        super.clear();
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

    @Override
    public List<InterviewSession> findByStudentId(String studentId) {
        if (studentId == null || studentId.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String normalized = studentId.trim().toUpperCase();
        java.util.Set<String> appIds = Collections.emptySet();
        if (applicationRepository != null) {
            appIds = applicationRepository.findByStudentId(normalized).stream()
                    .map(a -> a.getId().trim().toUpperCase())
                    .collect(java.util.stream.Collectors.toSet());
        }
        final java.util.Set<String> finalAppIds = appIds;
        List<InterviewSession> list = store.values().stream()
                .filter(s -> {
                    String normSessionId = s.getId().trim().toUpperCase();
                    String storedStudent = sessionStudentMap.get(normSessionId);
                    if (storedStudent != null && storedStudent.equalsIgnoreCase(normalized)) {
                        return true;
                    }
                    return finalAppIds.contains(s.getApplicationId().trim().toUpperCase());
                })
                .sorted(java.util.Comparator.comparing(InterviewSession::getStartTime).thenComparing(InterviewSession::getId))
                .toList();
        return Collections.unmodifiableList(list);
    }
}
