package com.hireflow.service;

import com.hireflow.model.Company;
import com.hireflow.model.Interviewer;
import com.hireflow.model.RecruitmentDrive;
import com.hireflow.model.Room;
import com.hireflow.model.Student;
import com.hireflow.repository.CompanyRepository;
import com.hireflow.repository.DriveRepository;
import com.hireflow.repository.InterviewerRepository;
import com.hireflow.repository.RoomRepository;
import com.hireflow.repository.StudentRepository;

import java.time.LocalDate;
import java.util.Set;

/**
 * Utility to seed standard sample data (students, companies, recruitment drives, interviewers, and rooms).
 */
public class SampleDataLoader {

    private final StudentRepository studentRepository;
    private final CompanyRepository companyRepository;
    private final DriveRepository driveRepository;
    private final InterviewerRepository interviewerRepository;
    private final RoomRepository roomRepository;

    public SampleDataLoader(StudentRepository studentRepository,
                            CompanyRepository companyRepository,
                            DriveRepository driveRepository) {
        this(studentRepository, companyRepository, driveRepository, null, null);
    }

    public SampleDataLoader(StudentRepository studentRepository,
                            CompanyRepository companyRepository,
                            DriveRepository driveRepository,
                            InterviewerRepository interviewerRepository,
                            RoomRepository roomRepository) {
        this.studentRepository = studentRepository;
        this.companyRepository = companyRepository;
        this.driveRepository = driveRepository;
        this.interviewerRepository = interviewerRepository;
        this.roomRepository = roomRepository;
    }

    /**
     * Seeds at least 10 students, 4 companies, 5 recruitment drives, and fictional interviewers/rooms if configured.
     */
    public void seedAll() {
        seedCompanies();
        seedStudents();
        seedDrives();
        seedInterviewers();
        seedRooms();
    }

    /**
     * Seeds sample data only if the repository dataset is currently empty.
     * Safe for development database initialization without duplicate records or destructive wipes.
     *
     * @return true if seeding was executed, false if skipped because records already exist
     */
    public boolean seedIfEmpty() {
        if (studentRepository.count() == 0 && companyRepository.count() == 0) {
            seedAll();
            return true;
        }
        return false;
    }

    public void seedCompanies() {
        companyRepository.save(new Company("CMP-001", "Google Cloud", "Cloud Computing & AI"));
        companyRepository.save(new Company("CMP-002", "Microsoft", "Enterprise Software & Cloud"));
        companyRepository.save(new Company("CMP-003", "Goldman Sachs", "FinTech & Investment Banking"));
        companyRepository.save(new Company("CMP-004", "Tata Motors", "Automotive & Electric Vehicles"));
    }

    public void seedStudents() {
        studentRepository.save(new Student("STU-101", "Alice Smith", "alice.smith@univ.edu", "CSE", 9.20, 2026, true));
        studentRepository.save(new Student("STU-102", "Bob Jones", "bob.jones@univ.edu", "CSE", 8.40, 2026, true));
        studentRepository.save(new Student("STU-103", "Charlie Brown", "charlie.brown@univ.edu", "IT", 7.80, 2026, true));
        studentRepository.save(new Student("STU-104", "Diana Prince", "diana.prince@univ.edu", "ECE", 8.90, 2026, true));
        studentRepository.save(new Student("STU-105", "Ethan Hunt", "ethan.hunt@univ.edu", "ECE", 7.20, 2026, true));
        studentRepository.save(new Student("STU-106", "Fiona Gallagher", "fiona.g@univ.edu", "MECH", 8.10, 2026, true));
        studentRepository.save(new Student("STU-107", "George Clark", "george.clark@univ.edu", "CIVIL", 7.50, 2026, true));
        studentRepository.save(new Student("STU-108", "Hannah Abbott", "hannah.a@univ.edu", "CSE", 6.80, 2026, true));
        studentRepository.save(new Student("STU-109", "Ian Malcolm", "ian.malcolm@univ.edu", "IT", 9.50, 2026, true));
        studentRepository.save(new Student("STU-110", "Julia Roberts", "julia.r@univ.edu", "CSE", 8.60, 2026, false)); // Inactive student
        studentRepository.save(new Student("STU-111", "Kevin Bacon", "kevin.bacon@univ.edu", "MECH", 6.50, 2026, true));
    }

    public void seedDrives() {
        LocalDate today = LocalDate.now();

        // Active Drive 1: Google SDE-1 (High CGPA, CSE/IT)
        driveRepository.save(new RecruitmentDrive(
                "DRV-101",
                "CMP-001",
                "Software Development Engineer - I",
                8.00,
                Set.of("CSE", "IT"),
                today.plusDays(14)
        ));

        // Active Drive 2: Microsoft Cloud Engineer (Medium CGPA, CSE/IT/ECE)
        driveRepository.save(new RecruitmentDrive(
                "DRV-102",
                "CMP-002",
                "Cloud Systems Engineer",
                7.50,
                Set.of("CSE", "IT", "ECE"),
                today.plusDays(21)
        ));

        // Active Drive 3: Goldman Sachs Quant Analyst (High CGPA, CSE/IT/ECE)
        driveRepository.save(new RecruitmentDrive(
                "DRV-103",
                "CMP-003",
                "Quantitative Risk Analyst",
                8.50,
                Set.of("CSE", "IT", "ECE"),
                today.plusDays(10)
        ));

        // Active Drive 4: Tata Motors Core Engineer (MECH/CIVIL/ECE)
        driveRepository.save(new RecruitmentDrive(
                "DRV-104",
                "CMP-004",
                "Automotive Design Engineer",
                7.00,
                Set.of("MECH", "CIVIL", "ECE"),
                today.plusDays(30)
        ));

        // Expired Drive 5: Past Deadline Drive (To test deadline rejection)
        driveRepository.save(new RecruitmentDrive(
                "DRV-105",
                "CMP-001",
                "Winter Research Intern",
                7.00,
                Set.of("CSE"),
                today.minusDays(5)
        ));
    }

    public void seedInterviewers() {
        if (interviewerRepository != null) {
            seedInterviewers(interviewerRepository);
        }
    }

    public void seedInterviewers(InterviewerRepository repo) {
        if (repo != null) {
            // Clearly fictional interviewer identities
            repo.save(new Interviewer("INT-001", "Dr. Sarah Chen", "CMP-001"));
            repo.save(new Interviewer("INT-002", "Marcus Vance", "CMP-002"));
            repo.save(new Interviewer("INT-003", "David Sterling", "CMP-003"));
        }
    }

    public void seedRooms() {
        if (roomRepository != null) {
            seedRooms(roomRepository);
        }
    }

    public void seedRooms(RoomRepository repo) {
        if (repo != null) {
            repo.save(new Room("RM-101", "Tech Lab A - Booth 1", 2));
            repo.save(new Room("RM-102", "Tech Lab A - Booth 2", 2));
        }
    }
}
