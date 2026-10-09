package com.hireflow.repository;

import com.hireflow.model.Interviewer;

import java.util.List;

/**
 * Persistence contracts for Interviewer entity operations.
 */
public interface InterviewerRepository extends CrudRepository<Interviewer, String> {

    List<Interviewer> findByCompanyId(String companyId);
}
