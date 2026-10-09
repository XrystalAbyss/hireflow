package com.hireflow.ui;

import com.hireflow.model.Application;
import com.hireflow.model.Company;
import com.hireflow.model.RecruitmentDrive;
import com.hireflow.repository.CompanyRepository;
import com.hireflow.repository.InterviewSessionRepository;
import com.hireflow.service.ApplicationService;
import com.hireflow.service.RecruitmentDriveService;
import com.hireflow.service.StudentService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Overview dashboard displaying operational metrics, active drives, and quick actions.
 */
public class DashboardPanel extends JPanel {

    private final StudentService studentService;
    private final RecruitmentDriveService driveService;
    private final CompanyRepository companyRepository;
    private final ApplicationService applicationService;
    private final InterviewSessionRepository sessionRepository;
    private final Consumer<String> navigationConsumer;

    private JLabel studentCountLabel;
    private JLabel companyCountLabel;
    private JLabel driveCountLabel;
    private JLabel appCountLabel;
    private JLabel sessionCountLabel;

    private DefaultTableModel activeDrivesModel;
    private DefaultTableModel recentAppsModel;

    public DashboardPanel(StudentService studentService,
                          RecruitmentDriveService driveService,
                          CompanyRepository companyRepository,
                          ApplicationService applicationService,
                          InterviewSessionRepository sessionRepository,
                          Consumer<String> navigationConsumer) {
        this.studentService = studentService;
        this.driveService = driveService;
        this.companyRepository = companyRepository;
        this.applicationService = applicationService;
        this.sessionRepository = sessionRepository;
        this.navigationConsumer = navigationConsumer;

        setLayout(new BorderLayout(16, 16));
        setBackground(UIUtils.BG_MAIN);
        setBorder(new EmptyBorder(24, 24, 24, 24));

        initUI();
        refreshData();
    }

    private void initUI() {
        // Top Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);

        JLabel title = new JLabel("Operations Dashboard");
        title.setFont(UIUtils.FONT_HEADER);
        title.setForeground(UIUtils.TEXT_PRIMARY);

        JLabel subtitle = new JLabel("Live campus recruitment metrics, ongoing drives, and interview status");
        subtitle.setFont(UIUtils.FONT_REGULAR);
        subtitle.setForeground(UIUtils.TEXT_SECONDARY);

        JPanel titlesBox = new JPanel(new GridLayout(2, 1, 2, 2));
        titlesBox.setOpaque(false);
        titlesBox.add(title);
        titlesBox.add(subtitle);
        headerPanel.add(titlesBox, BorderLayout.WEST);

        // Quick action buttons on header
        JPanel actionsBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actionsBox.setOpaque(false);

        JButton btnStudents = UIUtils.createSecondaryButton("+ Add Student");
        btnStudents.addActionListener(e -> navigationConsumer.accept("STUDENTS"));

        JButton btnDrives = UIUtils.createSecondaryButton("+ New Drive");
        btnDrives.addActionListener(e -> navigationConsumer.accept("DRIVES"));

        JButton btnSchedule = UIUtils.createPrimaryButton("Run Scheduler");
        btnSchedule.addActionListener(e -> navigationConsumer.accept("SCHEDULE"));

        actionsBox.add(btnStudents);
        actionsBox.add(btnDrives);
        actionsBox.add(btnSchedule);
        headerPanel.add(actionsBox, BorderLayout.EAST);

        add(headerPanel, BorderLayout.NORTH);

        // Center Content: Metric Cards + Tables
        JPanel centerPanel = new JPanel(new BorderLayout(16, 16));
        centerPanel.setOpaque(false);

        // Metric Cards Grid
        JPanel metricsGrid = new JPanel(new GridLayout(1, 5, 14, 0));
        metricsGrid.setOpaque(false);

        JPanel cardStudents = UIUtils.createStatCard("Registered Students", "0", UIUtils.PRIMARY);
        studentCountLabel = (JLabel) cardStudents.getComponent(1);

        JPanel cardCompanies = UIUtils.createStatCard("Partner Companies", "0", UIUtils.INFO);
        companyCountLabel = (JLabel) cardCompanies.getComponent(1);

        JPanel cardDrives = UIUtils.createStatCard("Active Drives", "0", UIUtils.SUCCESS);
        driveCountLabel = (JLabel) cardDrives.getComponent(1);

        JPanel cardApps = UIUtils.createStatCard("Applications", "0", UIUtils.WARNING);
        appCountLabel = (JLabel) cardApps.getComponent(1);

        JPanel cardSessions = UIUtils.createStatCard("Scheduled Sessions", "0", new Color(139, 92, 246));
        sessionCountLabel = (JLabel) cardSessions.getComponent(1);

        metricsGrid.add(cardStudents);
        metricsGrid.add(cardCompanies);
        metricsGrid.add(cardDrives);
        metricsGrid.add(cardApps);
        metricsGrid.add(cardSessions);

        centerPanel.add(metricsGrid, BorderLayout.NORTH);

        // Two tables side by side
        JPanel tablesGrid = new JPanel(new GridLayout(1, 2, 16, 0));
        tablesGrid.setOpaque(false);

        // Active Drives Table Card
        activeDrivesModel = new DefaultTableModel(new String[]{"Drive ID", "Company", "Job Title", "Cutoff", "Deadline"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        JTable drivesTable = new JTable(activeDrivesModel);
        UIUtils.styleTable(drivesTable);
        JScrollPane drivesScroll = new JScrollPane(drivesTable);
        drivesScroll.setBorder(BorderFactory.createEmptyBorder());
        JPanel drivesCard = UIUtils.createCard("Active Recruitment Drives", drivesScroll);
        tablesGrid.add(drivesCard);

        // Recent Applications Table Card
        recentAppsModel = new DefaultTableModel(new String[]{"App ID", "Student ID", "Drive ID", "Status"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        JTable appsTable = new JTable(recentAppsModel);
        UIUtils.styleTable(appsTable);
        JScrollPane appsScroll = new JScrollPane(appsTable);
        appsScroll.setBorder(BorderFactory.createEmptyBorder());
        JPanel appsCard = UIUtils.createCard("Recent Applications", appsScroll);
        tablesGrid.add(appsCard);

        centerPanel.add(tablesGrid, BorderLayout.CENTER);
        add(centerPanel, BorderLayout.CENTER);
    }

    public void refreshData() {
        studentCountLabel.setText(String.valueOf(studentService.getTotalStudentsCount()));
        companyCountLabel.setText(String.valueOf(companyRepository.count()));
        driveCountLabel.setText(String.valueOf(driveService.getActiveDrives().size()));
        appCountLabel.setText(String.valueOf(applicationService.getAllApplications().size()));
        sessionCountLabel.setText(String.valueOf(sessionRepository.count()));

        // Populate active drives table
        activeDrivesModel.setRowCount(0);
        List<RecruitmentDrive> activeDrives = driveService.getActiveDrives();
        for (RecruitmentDrive d : activeDrives) {
            String companyName = companyRepository.findById(d.getCompanyId())
                    .map(Company::getName).orElse(d.getCompanyId());
            activeDrivesModel.addRow(new Object[]{
                    d.getId(), companyName, d.getJobTitle(),
                    String.format("%.2f", d.getMinimumCgpa()),
                    d.getApplicationDeadline()
            });
        }

        // Populate recent applications table
        recentAppsModel.setRowCount(0);
        List<Application> apps = applicationService.getAllApplications();
        int limit = Math.min(apps.size(), 10);
        for (int i = 0; i < limit; i++) {
            Application a = apps.get(i);
            recentAppsModel.addRow(new Object[]{
                    a.getId(), a.getStudentId(), a.getDriveId(), a.getStatus()
            });
        }
    }
}
