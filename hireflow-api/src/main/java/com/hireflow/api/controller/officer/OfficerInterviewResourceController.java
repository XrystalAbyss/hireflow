package com.hireflow.api.controller.officer;

import com.hireflow.api.dto.officer.*;
import com.hireflow.db.TransactionManager;
import com.hireflow.exception.DuplicateEntityException;
import com.hireflow.exception.EntityNotFoundException;
import com.hireflow.exception.ValidationException;
import com.hireflow.model.Company;
import com.hireflow.model.Interviewer;
import com.hireflow.model.Room;
import com.hireflow.policy.UniversityCampusRoomPolicy;
import com.hireflow.repository.CompanyRepository;
import com.hireflow.repository.InterviewerRepository;
import com.hireflow.repository.RoomRepository;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST controller providing placement officer interview resource management (interviewers and rooms):
 * <ul>
 *     <li>GET  /api/v1/officer/interviewers      - List corporate interviewers</li>
 *     <li>POST /api/v1/officer/interviewers      - Register new interviewer</li>
 *     <li>PUT  /api/v1/officer/interviewers/{id} - Update interviewer details</li>
 *     <li>GET  /api/v1/officer/rooms             - List interview rooms</li>
 *     <li>POST /api/v1/officer/rooms             - Register new interview room</li>
 *     <li>PUT  /api/v1/officer/rooms/{id}        - Update interview room details</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/officer")
public class OfficerInterviewResourceController {

    private static final Logger log = LoggerFactory.getLogger(OfficerInterviewResourceController.class);

    private static final HttpHeaders ANTI_CACHE_HEADERS = new HttpHeaders();
    static {
        ANTI_CACHE_HEADERS.setCacheControl("no-cache, no-store, must-revalidate");
        ANTI_CACHE_HEADERS.setPragma("no-cache");
        ANTI_CACHE_HEADERS.setExpires(0);
    }

    private final InterviewerRepository interviewerRepository;
    private final RoomRepository roomRepository;
    private final CompanyRepository companyRepository;
    private final TransactionManager transactionManager;

    public OfficerInterviewResourceController(
            InterviewerRepository interviewerRepository,
            RoomRepository roomRepository,
            CompanyRepository companyRepository,
            @Qualifier("hireflowJdbcTransactionManager") TransactionManager transactionManager) {
        this.interviewerRepository = Objects.requireNonNull(interviewerRepository, "InterviewerRepository cannot be null.");
        this.roomRepository = Objects.requireNonNull(roomRepository, "RoomRepository cannot be null.");
        this.companyRepository = Objects.requireNonNull(companyRepository, "CompanyRepository cannot be null.");
        this.transactionManager = Objects.requireNonNull(transactionManager, "TransactionManager cannot be null.");
    }

    // ==========================================
    // INTERVIEWERS
    // ==========================================

    @GetMapping("/interviewers")
    public ResponseEntity<List<OfficerInterviewerDetailDto>> getInterviewers(
            @RequestParam(name = "companyId", required = false) String companyId) {

        List<Interviewer> interviewers = (companyId != null && !companyId.isBlank())
                ? interviewerRepository.findByCompanyId(companyId.trim().toUpperCase())
                : interviewerRepository.findAll();

        Map<String, String> companyNameMap = companyRepository.findAll().stream()
                .collect(Collectors.toMap(Company::getId, Company::getName, (a, b) -> a));

        List<OfficerInterviewerDetailDto> dtoList = interviewers.stream()
                .map(i -> OfficerInterviewerDetailDto.fromDomain(i, companyNameMap.get(i.getCompanyId().toUpperCase())))
                .sorted(Comparator.comparing(OfficerInterviewerDetailDto::name, String.CASE_INSENSITIVE_ORDER))
                .toList();

        return ResponseEntity.ok()
                .headers(ANTI_CACHE_HEADERS)
                .body(dtoList);
    }

    @PostMapping("/interviewers")
    public ResponseEntity<OfficerInterviewerDetailDto> createInterviewer(
            @Valid @RequestBody OfficerInterviewerCreateDto createDto) {

        String normId = createDto.id().trim().toUpperCase();
        String normCompanyId = createDto.companyId().trim().toUpperCase();

        Interviewer created = transactionManager.runInTransaction(conn -> {
            if (interviewerRepository.existsById(normId)) {
                throw new DuplicateEntityException("Interviewer with ID '" + normId + "' already exists.");
            }
            if (!companyRepository.existsById(normCompanyId)) {
                throw new EntityNotFoundException("Company with ID '" + normCompanyId + "' does not exist.");
            }

            Interviewer interviewer = new Interviewer(normId, createDto.name().trim(), normCompanyId);
            return interviewerRepository.add(interviewer);
        });

        log.info("[OfficerPortal] Interviewer '{}' ('{}') registered under company '{}'.",
                created.getId(), created.getName(), created.getCompanyId());

        String companyName = companyRepository.findById(normCompanyId).map(Company::getName).orElse(normCompanyId);
        OfficerInterviewerDetailDto dto = OfficerInterviewerDetailDto.fromDomain(created, companyName);
        URI location = URI.create("/api/v1/officer/interviewers/" + created.getId());

        return ResponseEntity.created(location)
                .headers(ANTI_CACHE_HEADERS)
                .body(dto);
    }

    @PutMapping("/interviewers/{id}")
    public ResponseEntity<OfficerInterviewerDetailDto> updateInterviewer(
            @PathVariable("id") String id,
            @Valid @RequestBody OfficerInterviewerUpdateDto updateDto) {

        if (id == null || id.trim().isEmpty()) {
            throw new ValidationException("Interviewer ID cannot be null or empty.");
        }
        String normId = id.trim().toUpperCase();
        String normCompanyId = updateDto.companyId().trim().toUpperCase();

        Interviewer updated = transactionManager.runInTransaction(conn -> {
            Interviewer existing = interviewerRepository.findById(normId)
                    .orElseThrow(() -> new EntityNotFoundException("Interviewer with ID '" + normId + "' not found."));

            if (!companyRepository.existsById(normCompanyId)) {
                throw new EntityNotFoundException("Company with ID '" + normCompanyId + "' does not exist.");
            }

            existing.setName(updateDto.name().trim());
            existing.setCompanyId(normCompanyId);
            return interviewerRepository.update(existing);
        });

        log.info("[OfficerPortal] Interviewer '{}' updated.", updated.getId());

        String companyName = companyRepository.findById(normCompanyId).map(Company::getName).orElse(normCompanyId);
        OfficerInterviewerDetailDto dto = OfficerInterviewerDetailDto.fromDomain(updated, companyName);
        return ResponseEntity.ok()
                .headers(ANTI_CACHE_HEADERS)
                .body(dto);
    }

    // ==========================================
    // ROOMS
    // ==========================================

    @GetMapping("/rooms")
    public ResponseEntity<List<OfficerRoomDetailDto>> getRooms() {
        List<Room> rooms = roomRepository.findAll();
        List<OfficerRoomDetailDto> dtoList = rooms.stream()
                .map(OfficerRoomDetailDto::fromDomain)
                .sorted(Comparator.comparing(OfficerRoomDetailDto::name, String.CASE_INSENSITIVE_ORDER))
                .toList();

        return ResponseEntity.ok()
                .headers(ANTI_CACHE_HEADERS)
                .body(dtoList);
    }

    @PostMapping("/rooms")
    public ResponseEntity<OfficerRoomDetailDto> createRoom(
            @Valid @RequestBody OfficerRoomCreateDto createDto) {

        String normId = createDto.id().trim().toUpperCase();
        if (createDto.capacity() <= 0) {
            throw new ValidationException("Room capacity must be a positive integer.");
        }

        Room testRoom = new Room(normId, createDto.name().trim(), createDto.capacity());
        UniversityCampusRoomPolicy.validateRoomForInterviewAllocation(testRoom);

        Room created = transactionManager.runInTransaction(conn -> {
            if (roomRepository.existsById(normId)) {
                throw new DuplicateEntityException("Room with ID '" + normId + "' already exists.");
            }
            Room room = new Room(normId, createDto.name().trim(), createDto.capacity());
            return roomRepository.add(room);
        });

        log.info("[OfficerPortal] Interview room '{}' ('{}', capacity {}) registered.",
                created.getId(), created.getName(), created.getCapacity());

        OfficerRoomDetailDto dto = OfficerRoomDetailDto.fromDomain(created);
        URI location = URI.create("/api/v1/officer/rooms/" + created.getId());

        return ResponseEntity.created(location)
                .headers(ANTI_CACHE_HEADERS)
                .body(dto);
    }

    @PutMapping("/rooms/{id}")
    public ResponseEntity<OfficerRoomDetailDto> updateRoom(
            @PathVariable("id") String id,
            @Valid @RequestBody OfficerRoomUpdateDto updateDto) {

        if (id == null || id.trim().isEmpty()) {
            throw new ValidationException("Room ID cannot be null or empty.");
        }
        String normId = id.trim().toUpperCase();

        if (updateDto.capacity() <= 0) {
            throw new ValidationException("Room capacity must be a positive integer.");
        }

        Room testRoom = new Room(normId, updateDto.name().trim(), updateDto.capacity());
        UniversityCampusRoomPolicy.validateRoomForInterviewAllocation(testRoom);

        Room updated = transactionManager.runInTransaction(conn -> {
            Room existing = roomRepository.findById(normId)
                    .orElseThrow(() -> new EntityNotFoundException("Room with ID '" + normId + "' not found."));

            existing.setName(updateDto.name().trim());
            existing.setCapacity(updateDto.capacity());
            return roomRepository.update(existing);
        });

        log.info("[OfficerPortal] Interview room '{}' updated.", updated.getId());

        OfficerRoomDetailDto dto = OfficerRoomDetailDto.fromDomain(updated);
        return ResponseEntity.ok()
                .headers(ANTI_CACHE_HEADERS)
                .body(dto);
    }
}
