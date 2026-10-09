package com.hireflow.repository;

import com.hireflow.exception.DuplicateEntityException;
import com.hireflow.model.Student;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * In-memory thread-safe implementation of StudentRepository.
 */
public class InMemoryStudentRepository extends AbstractInMemoryRepository<Student, String> implements StudentRepository {

    public InMemoryStudentRepository() {
        super(Student::getId, "Student");
    }

    @Override
    public Student add(Student student) {
        if (student != null) {
            Optional<Student> existingWithEmail = findByEmail(student.getEmail());
            if (existingWithEmail.isPresent()) {
                throw new DuplicateEntityException("A student with email '" + student.getEmail() + "' already exists.");
            }
        }
        return super.add(student);
    }

    @Override
    public Student update(Student student) {
        if (student != null) {
            Optional<Student> existingWithEmail = findByEmail(student.getEmail());
            if (existingWithEmail.isPresent() && !existingWithEmail.get().getId().equalsIgnoreCase(student.getId())) {
                throw new DuplicateEntityException("Another student with email '" + student.getEmail() + "' already exists.");
            }
        }
        return super.update(student);
    }

    @Override
    public Optional<Student> findByEmail(String email) {
        if (email == null) return Optional.empty();
        String normalized = email.trim().toLowerCase();
        return store.values().stream()
                .filter(s -> s.getEmail().equalsIgnoreCase(normalized))
                .findFirst();
    }

    @Override
    public List<Student> findByBranch(String branch) {
        if (branch == null) return Collections.emptyList();
        String normalized = branch.trim().toUpperCase();
        List<Student> list = store.values().stream()
                .filter(s -> s.getBranch().equalsIgnoreCase(normalized))
                .toList();
        return Collections.unmodifiableList(list);
    }
}
