package com.hireflow.ui;

import com.hireflow.model.*;
import com.hireflow.repository.*;
import com.hireflow.scheduler.*;
import com.hireflow.service.ApplicationService;
import com.hireflow.service.InterviewService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Screen 5: Automated Interview Scheduling Panel (Configure windows, generate schedules on background thread, view timetables & conflicts).
 */
public class SchedulePanel extends JPanel {

    private final SchedulingEngine schedulingEngine;
    private final ApplicationService applicationService;
    private final StudentRepository studentRepository;
    private final DriveRepository driveRepository;
    private final CompanyRepository companyRepository;
    private final InterviewerRepository interviewerRepository;
    private final RoomRepository roomRepository;
    private final InterviewSessionRepository sessionRepository;
    private final Runnable onDataChanged;

    // Controls
    private JSpinner startHourSpinner;
    private JSpinner endHourSpinner;
    private JSpinner durationSpinner;
    private JSpinner bufferSpinner;
    private JButton btnGenerate;
    private JButton btnPublish;
    private JProgressBar progressBar;

    // Status cards
    private JLabel totalRequestedLabel;
    private JLabel totalScheduledLabel;
    private JLabel totalUnscheduledLabel;

    // Tables
    private DefaultTableModel proposedTableModel;
    private JTable proposedTable;

    private DefaultTableModel unscheduledTableModel;
    private JTable unscheduledTable;

    private DefaultTableModel confirmedTableModel;
    private JTable confirmedTable;

    private SchedulingResult lastResult;
    private final InterviewService interviewService;

    public SchedulePanel(SchedulingEngine schedulingEngine,
                         ApplicationService applicationService,
                         StudentRepository studentRepository,
                         DriveRepository driveRepository,
                         CompanyRepository companyRepository,
                         InterviewerRepository interviewerRepository,
                         RoomRepository roomRepository,
                         InterviewSessionRepository sessionRepository,
                         Runnable onDataChanged) {
        this(schedulingEngine, applicationService, studentRepository, driveRepository, companyRepository,
                interviewerRepository, roomRepository, sessionRepository, null, onDataChanged);
    }

    public SchedulePanel(SchedulingEngine schedulingEngine,
                         ApplicationService applicationService,
                         StudentRepository studentRepository,
                         DriveRepository driveRepository,
                         CompanyRepository companyRepository,
                         InterviewerRepository interviewerRepository,
                         RoomRepository roomRepository,
                         InterviewSessionRepository sessionRepository,
                         InterviewService interviewService,
                         Runnable onDataChanged) {
        this.schedulingEngine = schedulingEngine;
        this.applicationService = applicationService;
        this.studentRepository = studentRepository;
        this.driveRepository = driveRepository;
        this.companyRepository = companyRepository;
        this.interviewerRepository = interviewerRepository;
        this.roomRepository = roomRepository;
        this.sessionRepository = sessionRepository;
        this.interviewService = interviewService;
        this.onDataChanged = onDataChanged;

        setLayout(new BorderLayout(16, 16));
        setBackground(UIUtils.BG_MAIN);
        setBorder(new EmptyBorder(24, 24, 24, 24));

        initUI();
        refreshConfirmedTable();
    }

    private void initUI() {
        // Top Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);

        JLabel title = new JLabel("Interview Scheduling Engine");
        title.setFont(UIUtils.FONT_HEADER);
        title.setForeground(UIUtils.TEXT_PRIMARY);

        JLabel subtitle = new JLabel("Run greedy interval slot allocation, prevent double-booking, and inspect conflicts");
        subtitle.setFont(UIUtils.FONT_REGULAR);
        subtitle.setForeground(UIUtils.TEXT_SECONDARY);

        JPanel titlesBox = new JPanel(new GridLayout(2, 1, 2, 2));
        titlesBox.setOpaque(false);
        titlesBox.add(title);
        titlesBox.add(subtitle);
        headerPanel.add(titlesBox, BorderLayout.WEST);

        add(headerPanel, BorderLayout.NORTH);

        // Center Content
        JPanel centerPanel = new JPanel(new BorderLayout(16, 16));
        centerPanel.setOpaque(false);

        // Parameters Card at Top
        JPanel paramsCard = createParametersCard();
        centerPanel.add(paramsCard, BorderLayout.NORTH);

        // Metrics + Results Tabbed Pane
        JPanel resultsPanel = new JPanel(new BorderLayout(12, 12));
        resultsPanel.setOpaque(false);

        // Metrics Banner
        JPanel metricsGrid = new JPanel(new GridLayout(1, 3, 14, 0));
        metricsGrid.setOpaque(false);
        JPanel cardReq = UIUtils.createStatCard("Candidates Evaluated", "-", UIUtils.PRIMARY);
        totalRequestedLabel = (JLabel) cardReq.getComponent(1);
        JPanel cardSched = UIUtils.createStatCard("Proposed Appointments", "-", UIUtils.SUCCESS);
        totalScheduledLabel = (JLabel) cardSched.getComponent(1);
        JPanel cardUnsched = UIUtils.createStatCard("Unscheduled Conflicts", "-", UIUtils.DANGER);
        totalUnscheduledLabel = (JLabel) cardUnsched.getComponent(1);

        metricsGrid.add(cardReq);
        metricsGrid.add(cardSched);
        metricsGrid.add(cardUnsched);
        resultsPanel.add(metricsGrid, BorderLayout.NORTH);

        // Tabbed Tables
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(UIUtils.FONT_BOLD);
        tabbedPane.setBackground(Color.WHITE);

        // Tab 1: Proposed Schedule
        proposedTableModel = new DefaultTableModel(new String[]{
                "Session ID", "App ID", "Student Name", "Company", "Interviewer", "Room", "Slot Time", "Duration"
        }, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        proposedTable = new JTable(proposedTableModel);
        UIUtils.styleTable(proposedTable);
        JScrollPane proposedScroll = new JScrollPane(proposedTable);
        proposedScroll.setBorder(BorderFactory.createEmptyBorder());
        tabbedPane.addTab("Proposed Timetable", proposedScroll);

        // Tab 2: Unscheduled Applications & Conflicts
        unscheduledTableModel = new DefaultTableModel(new String[]{
                "App ID", "Student ID", "Drive ID", "Constraint / Rejection Reason"
        }, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        unscheduledTable = new JTable(unscheduledTableModel);
        UIUtils.styleTable(unscheduledTable);
        JScrollPane unscheduledScroll = new JScrollPane(unscheduledTable);
        unscheduledScroll.setBorder(BorderFactory.createEmptyBorder());
        tabbedPane.addTab("Unscheduled Applications & Conflicts", unscheduledScroll);

        // Tab 3: Confirmed Existing Appointments
        confirmedTableModel = new DefaultTableModel(new String[]{
                "Session ID", "App ID", "Interviewer", "Room", "Start Time", "End Time"
        }, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        confirmedTable = new JTable(confirmedTableModel);
        UIUtils.styleTable(confirmedTable);
        JScrollPane confirmedScroll = new JScrollPane(confirmedTable);
        confirmedScroll.setBorder(BorderFactory.createEmptyBorder());
        tabbedPane.addTab("Confirmed Permanent Appointments", confirmedScroll);

        resultsPanel.add(UIUtils.createCard("", tabbedPane), BorderLayout.CENTER);
        centerPanel.add(resultsPanel, BorderLayout.CENTER);

        add(centerPanel, BorderLayout.CENTER);
    }

    private JPanel createParametersCard() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 8));
        panel.setOpaque(false);

        startHourSpinner = new JSpinner(new SpinnerNumberModel(9, 6, 22, 1));
        endHourSpinner = new JSpinner(new SpinnerNumberModel(17, 7, 23, 1));
        durationSpinner = new JSpinner(new SpinnerNumberModel(45, 15, 180, 15));
        bufferSpinner = new JSpinner(new SpinnerNumberModel(15, 0, 60, 5));

        panel.add(new JLabel("Window Start Hour:"));
        panel.add(startHourSpinner);

        panel.add(new JLabel("Window End Hour:"));
        panel.add(endHourSpinner);

        panel.add(new JLabel("Duration (mins):"));
        panel.add(durationSpinner);

        panel.add(new JLabel("Buffer (mins):"));
        panel.add(bufferSpinner);

        btnGenerate = UIUtils.createPrimaryButton("Generate Proposed Schedule");
        btnGenerate.addActionListener(e -> runSchedulingWorker());
        panel.add(btnGenerate);

        btnPublish = UIUtils.createSuccessButton("Publish & Confirm Schedule");
        btnPublish.setEnabled(false);
        btnPublish.addActionListener(e -> publishProposedSchedule());
        panel.add(btnPublish);

        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);
        progressBar.setPreferredSize(new Dimension(100, 24));
        panel.add(progressBar);

        return UIUtils.createCard("Scheduling Window & Resource Parameters", panel);
    }

    /**
     * Executes the scheduling engine on a SwingWorker background thread to avoid freezing the EDT.
     */
    private void runSchedulingWorker() {
        int startHour = (Integer) startHourSpinner.getValue();
        int endHour = (Integer) endHourSpinner.getValue();
        int durationMins = (Integer) durationSpinner.getValue();
        int bufferMins = (Integer) bufferSpinner.getValue();

        if (startHour >= endHour) {
            UIUtils.showError(this, "Window start hour must be strictly before end hour.", "Invalid Window");
            return;
        }

        btnGenerate.setEnabled(false);
        progressBar.setVisible(true);

        LocalDate today = LocalDate.now();
        TimeWindow window = new TimeWindow(
                LocalDateTime.of(today, LocalTime.of(startHour, 0)),
                LocalDateTime.of(today, LocalTime.of(endHour, 0))
        );

        SwingWorker<SchedulingResult, Void> worker = new SwingWorker<>() {
            @Override
            protected SchedulingResult doInBackground() {
                // Fetch applications
                List<Application> applications = applicationService.getAllApplications();

                // Build drive-company map
                SchedulingRequest.Builder builder = SchedulingRequest.builder()
                        .applications(applications)
                        .interviewers(interviewerRepository.findAll())
                        .rooms(roomRepository.findAll())
                        .schedulingWindow(window)
                        .interviewDuration(Duration.ofMinutes(durationMins))
                        .bufferDuration(Duration.ofMinutes(bufferMins))
                        .existingSessions(sessionRepository.findAll());

                for (RecruitmentDrive drive : driveRepository.findAll()) {
                    builder.addDriveCompany(drive.getId(), drive.getCompanyId());
                }

                return schedulingEngine.schedule(builder.build());
            }

            @Override
            protected void done() {
                btnGenerate.setEnabled(true);
                progressBar.setVisible(false);
                try {
                    lastResult = get();
                    displayResult(lastResult);
                    btnPublish.setEnabled(!lastResult.getScheduledSessions().isEmpty());
                } catch (Exception ex) {
                    UIUtils.showError(SchedulePanel.this, "Scheduling error: " + ex.getMessage(), "Algorithm Failure");
                }
            }
        };

        worker.execute();
    }

    private void displayResult(SchedulingResult result) {
        totalRequestedLabel.setText(String.valueOf(result.getTotalRequested()));
        totalScheduledLabel.setText(String.valueOf(result.getTotalScheduled()));
        totalUnscheduledLabel.setText(String.valueOf(result.getTotalUnscheduled()));

        // Populate proposed table
        proposedTableModel.setRowCount(0);
        for (InterviewSession session : result.getScheduledSessions()) {
            String studentId = applicationService.getApplicationById(session.getApplicationId())
                    .map(Application::getStudentId).orElse("?");
            String studentName = studentRepository.findById(studentId)
                    .map(Student::getName).orElse(studentId);

            String driveId = applicationService.getApplicationById(session.getApplicationId())
                    .map(Application::getDriveId).orElse("?");
            String companyName = driveRepository.findById(driveId)
                    .map(RecruitmentDrive::getCompanyId).orElse("?");

            String interviewerName = interviewerRepository.findById(session.getInterviewerId())
                    .map(Interviewer::getName).orElse(session.getInterviewerId());
            String roomName = roomRepository.findById(session.getRoomId())
                    .map(Room::getName).orElse(session.getRoomId());

            String timeSlot = session.getStartTime().toLocalTime() + " - " + session.getEndTime().toLocalTime();

            proposedTableModel.addRow(new Object[]{
                    session.getId(),
                    session.getApplicationId(),
                    studentName,
                    companyName,
                    interviewerName,
                    roomName,
                    timeSlot,
                    session.getDurationMinutes() + " mins"
            });
        }

        // Populate unscheduled table
        unscheduledTableModel.setRowCount(0);
        for (UnscheduledApplication un : result.getUnscheduledApplications()) {
            unscheduledTableModel.addRow(new Object[]{
                    un.getApplicationId(),
                    un.getStudentId(),
                    un.getDriveId(),
                    un.reason()
            });
        }
    }

    private void publishProposedSchedule() {
        if (lastResult == null || lastResult.getScheduledSessions().isEmpty()) {
            UIUtils.showWarning(this, "No proposed sessions to publish.", "Empty Schedule");
            return;
        }

        btnPublish.setEnabled(false);
        progressBar.setVisible(true);

        int bufferMins = (Integer) bufferSpinner.getValue();
        List<InterviewSession> proposedSessions = List.copyOf(lastResult.getScheduledSessions());

        SwingWorker<InterviewService.PublicationResult, Void> worker = new SwingWorker<>() {
            @Override
            protected InterviewService.PublicationResult doInBackground() {
                if (interviewService != null) {
                    return interviewService.publishSchedule(proposedSessions, Duration.ofMinutes(bufferMins));
                } else {
                    for (InterviewSession session : proposedSessions) {
                        sessionRepository.save(session);
                        try {
                            applicationService.updateStatus(session.getApplicationId(), ApplicationStatus.SCHEDULED,
                                    "Interview scheduled for " + session.getStartTime().toLocalTime());
                        } catch (Exception ignored) {
                        }
                    }
                    return new InterviewService.PublicationResult(proposedSessions.size(), proposedSessions, List.of(), List.of());
                }
            }

            @Override
            protected void done() {
                progressBar.setVisible(false);
                try {
                    InterviewService.PublicationResult pubResult = get();
                    if (pubResult.publishedCount() > 0) {
                        String msg = "Successfully confirmed and published " + pubResult.publishedCount() + " interview appointment(s).";
                        if (!pubResult.skippedDuplicateApplicationIds().isEmpty()) {
                            msg += "\n" + pubResult.skippedDuplicateApplicationIds().size() + " duplicate appointment(s) were skipped.";
                        }
                        UIUtils.showSuccess(SchedulePanel.this, msg, "Schedule Published");
                    } else if (!pubResult.skippedDuplicateApplicationIds().isEmpty()) {
                        UIUtils.showWarning(SchedulePanel.this, "All proposed sessions have already been published. No duplicate appointments created.", "Already Published");
                    } else if (pubResult.hasConflicts()) {
                        UIUtils.showError(SchedulePanel.this, "Could not publish due to conflicts:\n" + String.join("\n", pubResult.conflictRejections()), "Publication Conflict");
                    }
                } catch (Exception ex) {
                    UIUtils.showError(SchedulePanel.this, "Failed to publish schedule: " + ex.getMessage(), "Publication Error");
                } finally {
                    refreshConfirmedTable();
                    if (onDataChanged != null) onDataChanged.run();
                }
            }
        };

        worker.execute();
    }

    public void refreshConfirmedTable() {
        confirmedTableModel.setRowCount(0);
        List<InterviewSession> sessions = sessionRepository.findAll();
        for (InterviewSession s : sessions) {
            String interviewerName = interviewerRepository.findById(s.getInterviewerId())
                    .map(Interviewer::getName).orElse(s.getInterviewerId());
            String roomName = roomRepository.findById(s.getRoomId())
                    .map(Room::getName).orElse(s.getRoomId());

            confirmedTableModel.addRow(new Object[]{
                    s.getId(),
                    s.getApplicationId(),
                    interviewerName,
                    roomName,
                    s.getStartTime().toLocalTime(),
                    s.getEndTime().toLocalTime()
            });
        }
    }
}
