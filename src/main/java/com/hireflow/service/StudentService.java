package com.hireflow.service;

import com.hireflow.exception.DuplicateEntityException;
import com.hireflow.exception.EntityNotFoundException;
import com.hireflow.exception.ValidationException;
import com.hireflow.model.Student;
import com.hireflow.repository.StudentRepository;

import com.hireflow.service.event.HireFlowEvent;
import com.hireflow.service.event.HireFlowEventListener;
import com.hireflow.service.event.HireFlowEventType;

import com.hireflow.repository.ApplicationRepository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Service orchestrating Student business logic, registration, updates, search, and queries.
 */
public class StudentService {
    private final StudentRepository studentRepository;
    private final ApplicationRepository applicationRepository;
    private final List<HireFlowEventListener> eventListeners = new CopyOnWriteArrayList<>();

    public StudentService(StudentRepository studentRepository) {
        this(studentRepository, null);
    }

    public StudentService(StudentRepository studentRepository, ApplicationRepository applicationRepository) {
        if (studentRepository == null) {
            throw new IllegalArgumentException("StudentRepository cannot be null.");
        }
        this.studentRepository = studentRepository;
        this.applicationRepository = applicationRepository;
    }

    public void addEventListener(HireFlowEventListener listener) {
        if (listener != null) {
            eventListeners.add(listener);
        }
    }

    public void removeEventListener(HireFlowEventListener listener) {
        eventListeners.remove(listener);
    }

    private void publishEvent(HireFlowEventType type, String sourceId, String message) {
        HireFlowEvent event = new HireFlowEvent(type, sourceId, message);
        for (HireFlowEventListener listener : eventListeners) {
            try {
                listener.onEvent(event);
            } catch (Exception ignored) {
            }
        }
    }

    public Student registerStudent(String id, String name, String email, String branch,
                                   double cgpa, int graduationYear) {
        return registerStudent(id, name, email, branch, cgpa, graduationYear, true);
    }

    public Student registerStudent(String id, String name, String email, String branch,
                                   double cgpa, int graduationYear, boolean active) {
        if (id == null || id.trim().isEmpty()) {
            throw new ValidationException("Student ID is required.");
        }
        String normalizedId = id.trim().toUpperCase();
        if (studentRepository.existsById(normalizedId)) {
            throw new DuplicateEntityException("A student with ID '" + normalizedId + "' already exists.");
        }

        Student student = new Student(normalizedId, name, email, branch, cgpa, graduationYear, active);
        Student saved = studentRepository.add(student);
        publishEvent(HireFlowEventType.STUDENT_REGISTERED, saved.getId(), "Student registered: " + saved.getName());
        return saved;
    }

    public Student registerStudent(Student student) {
        if (student == null) {
            throw new ValidationException("Student to register cannot be null.");
        }
        Student saved = studentRepository.add(student);
        publishEvent(HireFlowEventType.STUDENT_REGISTERED, saved.getId(), "Student registered: " + saved.getName());
        return saved;
    }

    public Student updateStudent(Student student) {
        if (student == null) {
            throw new ValidationException("Student cannot be null.");
        }
        if (!studentRepository.existsById(student.getId())) {
            throw new EntityNotFoundException("Student with ID '" + student.getId() + "' was not found.");
        }
        return studentRepository.update(student);
    }

    public Optional<Student> getStudentById(String id) {
        if (id == null || id.trim().isEmpty()) return Optional.empty();
        return studentRepository.findById(id.trim());
    }

    public Student getStudentOrThrow(String id) {
        return getStudentById(id)
                .orElseThrow(() -> new EntityNotFoundException("Student with ID '" + id + "' not found."));
    }

    public Optional<Student> getStudentByEmail(String email) {
        if (email == null || email.trim().isEmpty()) return Optional.empty();
        return studentRepository.findByEmail(email.trim());
    }

    public List<Student> getAllStudents() {
        return studentRepository.findAll();
    }

    public List<Student> getStudentsByBranch(String branch) {
        return studentRepository.findByBranch(branch);
    }

    /**
     * Searches students by name, email, branch, or ID (case-insensitive substring match).
     */
    public List<Student> searchStudents(String query) {
        if (query == null || query.trim().isEmpty()) {
            return getAllStudents();
        }
        String q = query.trim().toLowerCase();
        List<Student> results = studentRepository.findAll().stream()
                .filter(s -> s.getName().toLowerCase().contains(q)
                        || s.getEmail().toLowerCase().contains(q)
                        || s.getBranch().toLowerCase().contains(q)
                        || s.getId().toLowerCase().contains(q))
                .toList();
        return Collections.unmodifiableList(results);
    }

    public boolean deleteStudent(String id) {
        if (id == null || id.trim().isEmpty()) return false;
        String normalizedId = id.trim().toUpperCase();

        if (applicationRepository != null) {
            List<com.hireflow.model.Application> apps = applicationRepository.findByStudentId(normalizedId);
            if (!apps.isEmpty()) {
                throw new ValidationException(String.format(
                        "Cannot delete student '%s' because %d application(s) reference this candidate.",
                        normalizedId, apps.size()
                ));
            }
        } else {
            throw new IllegalStateException("Cannot delete student: ApplicationRepository is required for referential integrity enforcement.");
        }
        return studentRepository.deleteById(normalizedId);
    }

    public int getTotalStudentsCount() {
        return studentRepository.count();
    }
}
