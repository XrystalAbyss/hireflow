package com.hireflow.repository;

import com.hireflow.model.Interviewer;

import java.util.Collections;
import java.util.List;

/**
 * In-memory thread-safe implementation of InterviewerRepository.
 */
public class InMemoryInterviewerRepository extends AbstractInMemoryRepository<Interviewer, String> implements InterviewerRepository {

    public InMemoryInterviewerRepository() {
        super(Interviewer::getId, "Interviewer");
    }

    @Override
    public List<Interviewer> findByCompanyId(String companyId) {
        if (companyId == null) return Collections.emptyList();
        String normalized = companyId.trim().toUpperCase();
        List<Interviewer> list = store.values().stream()
                .filter(i -> i.getCompanyId().equalsIgnoreCase(normalized))
                .toList();
        return Collections.unmodifiableList(list);
    }
}
