package com.hireflow.repository;

import com.hireflow.model.InterviewSession;

import java.util.List;

/**
 * Persistence contracts for InterviewSession entity operations.
 */
public interface InterviewSessionRepository extends CrudRepository<InterviewSession, String> {

    List<InterviewSession> findByApplicationId(String applicationId);

    List<InterviewSession> findByInterviewerId(String interviewerId);

    List<InterviewSession> findByRoomId(String roomId);
}
