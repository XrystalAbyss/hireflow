package com.hireflow.ui;

import com.hireflow.model.*;
import com.hireflow.repository.*;
import com.hireflow.scheduler.GreedyScheduler;
import com.hireflow.scheduler.SchedulingEngine;
import com.hireflow.service.*;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Main application window for HireFlow Stage 1 Desktop Prototype.
 * Provides sidebar navigation, responsive card-based screen switching,
 * real-time synchronization across panels, and status bar metrics.
 */
public class MainFrame extends JFrame {

    public static final String SCREEN_DASHBOARD = "DASHBOARD";
    public static final String SCREEN_STUDENTS = "STUDENTS";
    public static final String SCREEN_DRIVES = "DRIVES";
    public static final String SCREEN_APPLICATIONS = "APPLICATIONS";
    public static final String SCREEN_SCHEDULE = "SCHEDULE";

    // Repositories
    private final StudentRepository studentRepository;
    private final CompanyRepository companyRepository;
    private final DriveRepository driveRepository;
    private final ApplicationRepository applicationRepository;
    private final InterviewerRepository interviewerRepository;
    private final RoomRepository roomRepository;
    private final InterviewSessionRepository sessionRepository;

    // Services
    private final StudentService studentService;
    private final RecruitmentDriveService driveService;
    private final EligibilityService eligibilityService;
    private final ApplicationService applicationService;
    private final SchedulingEngine schedulingEngine;

    // UI Panels
    private CardLayout cardLayout;
    private JPanel contentContainer;
    private DashboardPanel dashboardPanel;
    private StudentPanel studentPanel;
    private CompanyDrivePanel companyDrivePanel;
    private ApplicationPanel applicationPanel;
    private SchedulePanel schedulePanel;

    // Sidebar navigation buttons
    private final Map<String, JButton> navButtons = new HashMap<>();
    private String currentScreen = SCREEN_DASHBOARD;

    // Status bar labels
    private JLabel statusMessageLabel;
    private JLabel statusStatsLabel;

    public MainFrame(StudentRepository studentRepository,
                     CompanyRepository companyRepository,
                     DriveRepository driveRepository,
                     ApplicationRepository applicationRepository,
                     InterviewerRepository interviewerRepository,
                     RoomRepository roomRepository,
                     InterviewSessionRepository sessionRepository,
                     StudentService studentService,
                     RecruitmentDriveService driveService,
                     EligibilityService eligibilityService,
                     ApplicationService applicationService,
                     SchedulingEngine schedulingEngine) {
        super("HireFlow - Campus Recruitment & Interview Scheduling System (Stage 1)");

        this.studentRepository = studentRepository;
        this.companyRepository = companyRepository;
        this.driveRepository = driveRepository;
        this.applicationRepository = applicationRepository;
        this.interviewerRepository = interviewerRepository;
        this.roomRepository = roomRepository;
        this.sessionRepository = sessionRepository;

        this.studentService = studentService;
        this.driveService = driveService;
        this.eligibilityService = eligibilityService;
        this.applicationService = applicationService;
        this.schedulingEngine = schedulingEngine;

        initWindow();
        initComponents();
        switchScreen(SCREEN_DASHBOARD);
        updateStatusBar();
    }

    private void initWindow() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1280, 820);
        setMinimumSize(new Dimension(1050, 680));
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
    }

    private void initComponents() {
        // 1. Sidebar on West
        JPanel sidebar = createSidebar();
        add(sidebar, BorderLayout.WEST);

        // 2. Central Content Area with CardLayout
        cardLayout = new CardLayout();
        contentContainer = new JPanel(cardLayout);
        contentContainer.setBackground(UIUtils.BG_MAIN);

        // Initialize Screen Panels with reactive data-change listeners
        Runnable onDataChanged = this::refreshAllPanels;

        dashboardPanel = new DashboardPanel(
                studentService,
                driveService,
                companyRepository,
                applicationService,
                sessionRepository,
                this::switchScreen
        );

        studentPanel = new StudentPanel(studentService, onDataChanged);

        companyDrivePanel = new CompanyDrivePanel(
                driveService,
                companyRepository,
                interviewerRepository,
                onDataChanged
        );

        applicationPanel = new ApplicationPanel(
                applicationService,
                eligibilityService,
                studentRepository,
                driveRepository,
                companyRepository,
                onDataChanged
        );

        InterviewService interviewService = new InterviewService(
                sessionRepository,
                applicationRepository,
                applicationService
        );

        schedulePanel = new SchedulePanel(
                schedulingEngine,
                applicationService,
                studentRepository,
                driveRepository,
                companyRepository,
                interviewerRepository,
                roomRepository,
                sessionRepository,
                interviewService,
                onDataChanged
        );

        contentContainer.add(dashboardPanel, SCREEN_DASHBOARD);
        contentContainer.add(studentPanel, SCREEN_STUDENTS);
        contentContainer.add(companyDrivePanel, SCREEN_DRIVES);
        contentContainer.add(applicationPanel, SCREEN_APPLICATIONS);
        contentContainer.add(schedulePanel, SCREEN_SCHEDULE);

        add(contentContainer, BorderLayout.CENTER);

        // 3. Status Bar on South
        JPanel statusBar = createStatusBar();
        add(statusBar, BorderLayout.SOUTH);

        // 4. Attach Domain Event Listeners
        com.hireflow.service.event.HireFlowEventListener eventLogger = event -> {
            SwingUtilities.invokeLater(() -> {
                if (statusMessageLabel != null) {
                    statusMessageLabel.setText("● Event: " + event.type().getDescription() + " [" + (event.sourceId() != null ? event.sourceId() : "") + "]");
                }
            });
        };
        studentService.addEventListener(eventLogger);
        driveService.addEventListener(eventLogger);
        applicationService.addEventListener(eventLogger);
        interviewService.addEventListener(eventLogger);
    }

    private JPanel createSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setPreferredSize(new Dimension(250, 0));
        sidebar.setBackground(UIUtils.BG_SIDEBAR);
        sidebar.setBorder(new MatteBorder(0, 0, 0, 1, new Color(30, 41, 59)));

        // Top Brand Header
        JPanel brandPanel = new JPanel(new BorderLayout(10, 5));
        brandPanel.setOpaque(false);
        brandPanel.setBorder(new EmptyBorder(22, 20, 22, 20));

        JPanel logoBadge = new JPanel(new GridBagLayout());
        logoBadge.setPreferredSize(new Dimension(42, 42));
        logoBadge.setBackground(UIUtils.PRIMARY);
        logoBadge.setBorder(new LineBorder(new Color(96, 165, 250), 1, true));

        JLabel logoText = new JLabel("HF");
        logoText.setFont(new Font("Segoe UI", Font.BOLD, 18));
        logoText.setForeground(Color.WHITE);
        logoBadge.add(logoText);

        JPanel titleBox = new JPanel(new GridLayout(2, 1, 0, 2));
        titleBox.setOpaque(false);

        JLabel brandTitle = new JLabel("HireFlow");
        brandTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        brandTitle.setForeground(Color.WHITE);

        JLabel brandSubtitle = new JLabel("Campus Placement System");
        brandSubtitle.setFont(UIUtils.FONT_SMALL);
        brandSubtitle.setForeground(new Color(148, 163, 184));

        titleBox.add(brandTitle);
        titleBox.add(brandSubtitle);

        brandPanel.add(logoBadge, BorderLayout.WEST);
        brandPanel.add(titleBox, BorderLayout.CENTER);

        sidebar.add(brandPanel, BorderLayout.NORTH);

        // Middle Nav Buttons
        JPanel navList = new JPanel();
        navList.setLayout(new BoxLayout(navList, BoxLayout.Y_AXIS));
        navList.setOpaque(false);
        navList.setBorder(new EmptyBorder(10, 12, 10, 12));

        addNavButton(navList, SCREEN_DASHBOARD, "  Dashboard", "Overview & key metrics");
        addNavButton(navList, SCREEN_STUDENTS, "  Students", "Student profiles & search");
        addNavButton(navList, SCREEN_DRIVES, "  Companies & Drives", "Manage drives & partners");
        addNavButton(navList, SCREEN_APPLICATIONS, "  Applications", "Eligibility & candidate pipeline");
        addNavButton(navList, SCREEN_SCHEDULE, "  Interview Scheduler", "Greedy allocation engine");

        sidebar.add(navList, BorderLayout.CENTER);

        // Bottom Actions & Version Info
        JPanel bottomPanel = new JPanel(new BorderLayout(0, 10));
        bottomPanel.setOpaque(false);
        bottomPanel.setBorder(new EmptyBorder(16, 14, 16, 14));

        JButton btnReloadData = new JButton("Reset Demo Data");
        btnReloadData.setFont(UIUtils.FONT_BOLD);
        btnReloadData.setForeground(new Color(226, 232, 240));
        btnReloadData.setBackground(new Color(30, 41, 59));
        btnReloadData.setFocusPainted(false);
        btnReloadData.setBorder(new CompoundBorder(
                new LineBorder(new Color(51, 65, 85), 1, true),
                new EmptyBorder(8, 12, 8, 12)
        ));
        btnReloadData.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnReloadData.addActionListener(e -> handleResetDemoData());

        btnReloadData.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                btnReloadData.setBackground(new Color(51, 65, 85));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                btnReloadData.setBackground(new Color(30, 41, 59));
            }
        });

        JLabel versionLabel = new JLabel("Stage 1 Prototype • Core Java 21");
        versionLabel.setFont(UIUtils.FONT_SMALL);
        versionLabel.setForeground(new Color(100, 116, 139));
        versionLabel.setHorizontalAlignment(SwingConstants.CENTER);

        bottomPanel.add(btnReloadData, BorderLayout.NORTH);
        bottomPanel.add(versionLabel, BorderLayout.SOUTH);

        sidebar.add(bottomPanel, BorderLayout.SOUTH);

        return sidebar;
    }

    private void addNavButton(JPanel container, String screenKey, String title, String tooltip) {
        JButton btn = new JButton(title);
        btn.setFont(UIUtils.FONT_BOLD);
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        btn.setPreferredSize(new Dimension(226, 44));
        btn.setFocusPainted(false);
        btn.setToolTipText(tooltip);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setOpaque(true);

        btn.addActionListener(e -> switchScreen(screenKey));

        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (!screenKey.equals(currentScreen)) {
                    btn.setBackground(UIUtils.BG_SIDEBAR_HOVER);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (!screenKey.equals(currentScreen)) {
                    btn.setBackground(UIUtils.BG_SIDEBAR);
                }
            }
        });

        container.add(btn);
        container.add(Box.createVerticalStrut(6));
        navButtons.put(screenKey, btn);
    }

    public void switchScreen(String screenKey) {
        if (!navButtons.containsKey(screenKey)) {
            return;
        }

        currentScreen = screenKey;
        cardLayout.show(contentContainer, screenKey);

        // Update active button visual states
        for (Map.Entry<String, JButton> entry : navButtons.entrySet()) {
            JButton btn = entry.getValue();
            if (entry.getKey().equals(screenKey)) {
                btn.setBackground(UIUtils.BG_SIDEBAR_ACTIVE);
                btn.setForeground(Color.WHITE);
                btn.setBorder(new CompoundBorder(
                        new MatteBorder(0, 4, 0, 0, UIUtils.PRIMARY),
                        new EmptyBorder(8, 12, 8, 12)
                ));
            } else {
                btn.setBackground(UIUtils.BG_SIDEBAR);
                btn.setForeground(new Color(203, 213, 225));
                btn.setBorder(new EmptyBorder(8, 16, 8, 12));
            }
        }

        updateStatusBar();
    }

    private JPanel createStatusBar() {
        JPanel bar = new JPanel(new BorderLayout(16, 0));
        bar.setPreferredSize(new Dimension(0, 32));
        bar.setBackground(new Color(241, 245, 249));
        bar.setBorder(new CompoundBorder(
                new MatteBorder(1, 0, 0, 0, UIUtils.BORDER_COLOR),
                new EmptyBorder(0, 16, 0, 16)
        ));

        statusMessageLabel = new JLabel("Status: System Ready");
        statusMessageLabel.setFont(UIUtils.FONT_SMALL);
        statusMessageLabel.setForeground(UIUtils.TEXT_SECONDARY);

        statusStatsLabel = new JLabel("");
        statusStatsLabel.setFont(UIUtils.FONT_SMALL);
        statusStatsLabel.setForeground(UIUtils.TEXT_MUTED);

        JLabel techLabel = new JLabel("Event Dispatch Thread Active | Java 21");
        techLabel.setFont(UIUtils.FONT_SMALL);
        techLabel.setForeground(UIUtils.TEXT_MUTED);

        bar.add(statusMessageLabel, BorderLayout.WEST);
        bar.add(statusStatsLabel, BorderLayout.CENTER);
        bar.add(techLabel, BorderLayout.EAST);

        return bar;
    }

    public void refreshAllPanels() {
        dashboardPanel.refreshData();
        studentPanel.refreshTable();
        companyDrivePanel.refreshAll();
        applicationPanel.refreshAll();
        schedulePanel.refreshConfirmedTable();
        updateStatusBar();
    }

    private void updateStatusBar() {
        int students = studentService.getTotalStudentsCount();
        int drives = driveService.getAllDrives().size();
        int apps = applicationService.getAllApplications().size();
        int sessions = sessionRepository.count();

        statusMessageLabel.setText("Active Screen: " + currentScreen + " | State: Synchronized");
        statusStatsLabel.setText(String.format("Students: %d  |  Drives: %d  |  Applications: %d  |  Confirmed Sessions: %d",
                students, drives, apps, sessions));
    }

    private void handleResetDemoData() {
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Resetting demo data will restore standard seed students, companies, recruitment drives, and applications.\n" +
                        "Do you wish to proceed?",
                "Confirm Demo Data Reset",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );

        if (confirm == JOptionPane.YES_OPTION) {
            seedSampleData(
                    studentRepository, companyRepository, driveRepository,
                    applicationRepository, interviewerRepository, roomRepository, sessionRepository,
                    applicationService
            );
            refreshAllPanels();
            UIUtils.showSuccess(this, "Standard sample data successfully restored!", "Data Reset");
        }
    }

    /**
     * Seeds the standard demonstration dataset into repositories.
     */
    public static void seedSampleData(StudentRepository studentRepo,
                                      CompanyRepository companyRepo,
                                      DriveRepository driveRepo,
                                      ApplicationRepository appRepo,
                                      InterviewerRepository interviewerRepo,
                                      RoomRepository roomRepo,
                                      InterviewSessionRepository sessionRepo,
                                      ApplicationService appService) {
        // Clear existing records
        sessionRepo.clear();
        appRepo.clear();
        interviewerRepo.clear();
        roomRepo.clear();
        studentRepo.clear();
        companyRepo.clear();
        driveRepo.clear();

        // 1. Seed Companies, Students, Drives, Interviewers, Rooms via SampleDataLoader
        SampleDataLoader loader = new SampleDataLoader(
                studentRepo, companyRepo, driveRepo, interviewerRepo, roomRepo
        );
        loader.seedAll();

        // 2. Seed Applications & Shortlist for scheduling demo
        try {
            Application a1 = appService.submitApplication("STU-101", "DRV-101"); // Alice -> Google
            Application a2 = appService.submitApplication("STU-102", "DRV-101"); // Bob -> Google
            Application a3 = appService.submitApplication("STU-104", "DRV-102"); // Diana -> MS
            Application a4 = appService.submitApplication("STU-101", "DRV-102"); // Alice -> MS
            Application a5 = appService.submitApplication("STU-109", "DRV-102"); // Ian -> MS
            Application a6 = appService.submitApplication("STU-103", "DRV-102"); // Charlie -> MS (applied only)

            appService.shortlistApplication(a1.getId());
            appService.shortlistApplication(a2.getId());
            appService.shortlistApplication(a3.getId());
            appService.shortlistApplication(a4.getId());
            appService.shortlistApplication(a5.getId());
        } catch (Exception ignored) {
        }
    }
}
