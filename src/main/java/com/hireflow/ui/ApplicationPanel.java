package com.hireflow.ui;

import com.hireflow.exception.ValidationException;
import com.hireflow.model.Application;
import com.hireflow.model.ApplicationStatus;
import com.hireflow.model.Company;
import com.hireflow.model.RecruitmentDrive;
import com.hireflow.model.Student;
import com.hireflow.repository.CompanyRepository;
import com.hireflow.repository.DriveRepository;
import com.hireflow.repository.StudentRepository;
import com.hireflow.service.ApplicationService;
import com.hireflow.service.EligibilityService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * Screen 4: Applications & Eligibility Panel (Evaluate eligibility, submit applications, track status).
 */
public class ApplicationPanel extends JPanel {

    private final ApplicationService applicationService;
    private final EligibilityService eligibilityService;
    private final StudentRepository studentRepository;
    private final DriveRepository driveRepository;
    private final CompanyRepository companyRepository;
    private final Runnable onDataChanged;

    private JComboBox<String> studentSelectCombo;
    private JComboBox<String> driveSelectCombo;
    private JLabel eligibilityFeedbackLabel;

    private DefaultTableModel applicationsModel;
    private JTable applicationsTable;

    public ApplicationPanel(ApplicationService applicationService,
                            EligibilityService eligibilityService,
                            StudentRepository studentRepository,
                            DriveRepository driveRepository,
                            CompanyRepository companyRepository,
                            Runnable onDataChanged) {
        this.applicationService = applicationService;
        this.eligibilityService = eligibilityService;
        this.studentRepository = studentRepository;
        this.driveRepository = driveRepository;
        this.companyRepository = companyRepository;
        this.onDataChanged = onDataChanged;

        setLayout(new BorderLayout(16, 16));
        setBackground(UIUtils.BG_MAIN);
        setBorder(new EmptyBorder(24, 24, 24, 24));

        initUI();
        refreshAll();
    }

    private void initUI() {
        // Top Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);

        JLabel title = new JLabel("Application Lifecycle & Eligibility Evaluation");
        title.setFont(UIUtils.FONT_HEADER);
        title.setForeground(UIUtils.TEXT_PRIMARY);

        JLabel subtitle = new JLabel("Evaluate academic criteria, submit candidate applications, and track progress through selection rounds");
        subtitle.setFont(UIUtils.FONT_REGULAR);
        subtitle.setForeground(UIUtils.TEXT_SECONDARY);

        JPanel titlesBox = new JPanel(new GridLayout(2, 1, 2, 2));
        titlesBox.setOpaque(false);
        titlesBox.add(title);
        titlesBox.add(subtitle);
        headerPanel.add(titlesBox, BorderLayout.WEST);

        add(headerPanel, BorderLayout.NORTH);

        // Center split: Table on Left/Center, Eligibility & Application Form on Right
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.65);
        splitPane.setBorder(null);
        splitPane.setOpaque(false);

        // Applications Table
        applicationsModel = new DefaultTableModel(new String[]{
                "Application ID", "Student", "Company & Role", "Status", "Applied At", "Remarks"
        }, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };

        applicationsTable = new JTable(applicationsModel);
        UIUtils.styleTable(applicationsTable);
        applicationsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane scrollPane = new JScrollPane(applicationsTable);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        // Action Toolbar below table
        JPanel tableActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        tableActions.setOpaque(false);

        JButton btnShortlist = UIUtils.createPrimaryButton("Shortlist Candidate");
        btnShortlist.addActionListener(e -> updateSelectedStatus(ApplicationStatus.SHORTLISTED));

        JButton btnSchedule = UIUtils.createButton("Schedule Candidate", new Color(139, 92, 246), new Color(124, 58, 237), Color.WHITE);
        btnSchedule.addActionListener(e -> updateSelectedStatus(ApplicationStatus.SCHEDULED));

        JButton btnSelect = UIUtils.createSuccessButton("Mark Selected / Hired");
        btnSelect.addActionListener(e -> updateSelectedStatus(ApplicationStatus.SELECTED));

        JButton btnReject = UIUtils.createDangerButton("Reject Application");
        btnReject.addActionListener(e -> updateSelectedStatus(ApplicationStatus.REJECTED));

        tableActions.add(btnShortlist);
        tableActions.add(btnSchedule);
        tableActions.add(btnSelect);
        tableActions.add(btnReject);

        JPanel tableContent = new JPanel(new BorderLayout(0, 10));
        tableContent.setOpaque(false);
        tableContent.add(scrollPane, BorderLayout.CENTER);
        tableContent.add(tableActions, BorderLayout.SOUTH);

        splitPane.setLeftComponent(UIUtils.createCard("Registered Candidate Applications", tableContent));

        // Eligibility Evaluation & Submission Card (Right side)
        JPanel actionCard = createActionCard();
        splitPane.setRightComponent(actionCard);

        add(splitPane, BorderLayout.CENTER);
    }

    private JPanel createActionCard() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 4, 6, 4);
        gbc.weightx = 1.0;
        gbc.gridx = 0;
        int row = 0;

        panel.add(new JLabel("Select Candidate Student:"), gbcRow(gbc, row++));
        studentSelectCombo = new JComboBox<>();
        studentSelectCombo.setFont(UIUtils.FONT_REGULAR);
        panel.add(studentSelectCombo, gbcRow(gbc, row++));

        panel.add(new JLabel("Select Target Recruitment Drive:"), gbcRow(gbc, row++));
        driveSelectCombo = new JComboBox<>();
        driveSelectCombo.setFont(UIUtils.FONT_REGULAR);
        panel.add(driveSelectCombo, gbcRow(gbc, row++));

        // Evaluate Button
        JButton btnCheck = UIUtils.createSecondaryButton("Check Eligibility Criteria");
        btnCheck.addActionListener(e -> performEligibilityCheck());
        gbc.insets = new Insets(10, 4, 4, 4);
        panel.add(btnCheck, gbcRow(gbc, row++));

        // Eligibility Feedback Banner
        eligibilityFeedbackLabel = new JLabel("<html><i>Select student and drive to evaluate criteria.</i></html>");
        eligibilityFeedbackLabel.setFont(UIUtils.FONT_REGULAR);
        eligibilityFeedbackLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIUtils.BORDER_COLOR, 1, true),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));
        gbc.insets = new Insets(4, 4, 14, 4);
        panel.add(eligibilityFeedbackLabel, gbcRow(gbc, row++));

        // Submit Application Button
        JButton btnApply = UIUtils.createSuccessButton("Submit Application for Drive");
        btnApply.addActionListener(e -> handleSubmitApplication());
        gbc.insets = new Insets(4, 4, 4, 4);
        panel.add(btnApply, gbcRow(gbc, row));

        JPanel container = new JPanel(new BorderLayout());
        container.setOpaque(false);
        container.add(panel, BorderLayout.NORTH);

        return UIUtils.createCard("Eligibility Verifier & Application Submission", container);
    }

    private GridBagConstraints gbcRow(GridBagConstraints gbc, int row) {
        gbc.gridy = row;
        return gbc;
    }

    private void performEligibilityCheck() {
        String sItem = (String) studentSelectCombo.getSelectedItem();
        String dItem = (String) driveSelectCombo.getSelectedItem();

        if (sItem == null || dItem == null || !sItem.contains(" - ") || !dItem.contains(" - ")) {
            UIUtils.showError(this, "Please select both a valid student and a recruitment drive.", "Selection Required");
            return;
        }

        String studentId = sItem.split(" - ")[0].trim();
        String driveId = dItem.split(" - ")[0].trim();

        Student student = studentRepository.findById(studentId).orElse(null);
        RecruitmentDrive drive = driveRepository.findById(driveId).orElse(null);

        if (student == null || drive == null) {
            UIUtils.showError(this, "Student or Drive record not found.", "Not Found");
            return;
        }

        EligibilityService.EligibilityResult result = eligibilityService.checkEligibility(student, drive);

        if (result.isEligible()) {
            eligibilityFeedbackLabel.setText("<html><b style='color:#10B981;'>[PASS] ELIGIBLE TO APPLY</b><br>" +
                    result.getFormattedReason() + "</html>");
        } else {
            eligibilityFeedbackLabel.setText("<html><b style='color:#EF4444;'>[FAIL] INELIGIBLE</b><br>" +
                    result.getFormattedReason() + "</html>");
        }
    }

    private void handleSubmitApplication() {
        String sItem = (String) studentSelectCombo.getSelectedItem();
        String dItem = (String) driveSelectCombo.getSelectedItem();

        if (sItem == null || dItem == null || !sItem.contains(" - ") || !dItem.contains(" - ")) {
            UIUtils.showError(this, "Please select both a student and a recruitment drive.", "Selection Required");
            return;
        }

        String studentId = sItem.split(" - ")[0].trim();
        String driveId = dItem.split(" - ")[0].trim();

        try {
            Application app = applicationService.submitApplication(studentId, driveId);
            UIUtils.showSuccess(this,
                    "Application submitted successfully!\nApplication ID: " + app.getId() + "\nStatus: " + app.getStatus(),
                    "Application Registered");
            refreshApplicationsTable();
            if (onDataChanged != null) onDataChanged.run();
        } catch (Exception ex) {
            UIUtils.showError(this, ex.getMessage(), "Application Rejected");
        }
    }

    private void updateSelectedStatus(ApplicationStatus newStatus) {
        int row = applicationsTable.getSelectedRow();
        if (row == -1) {
            UIUtils.showWarning(this, "Please select an application from the table.", "No Selection");
            return;
        }

        String appId = (String) applicationsModel.getValueAt(row, 0);
        try {
            applicationService.updateStatus(appId, newStatus, "Status updated via management console.");
            UIUtils.showSuccess(this, "Application '" + appId + "' updated to " + newStatus, "Status Updated");
            refreshApplicationsTable();
            if (onDataChanged != null) onDataChanged.run();
        } catch (ValidationException ex) {
            UIUtils.showError(this, ex.getMessage(), "Invalid Status Transition");
        } catch (Exception ex) {
            UIUtils.showError(this, ex.getMessage(), "Update Error");
        }
    }

    public void refreshAll() {
        refreshDropdowns();
        refreshApplicationsTable();
    }

    public void refreshDropdowns() {
        studentSelectCombo.removeAllItems();
        List<Student> students = studentRepository.findAll();
        for (Student s : students) {
            studentSelectCombo.addItem(s.getId() + " - " + s.getName() + " (" + s.getBranch() + ", " + String.format("%.2f", s.getCgpa()) + ")");
        }

        driveSelectCombo.removeAllItems();
        List<RecruitmentDrive> drives = driveRepository.findAll();
        for (RecruitmentDrive d : drives) {
            String companyName = companyRepository.findById(d.getCompanyId())
                    .map(Company::getName).orElse(d.getCompanyId());
            driveSelectCombo.addItem(d.getId() + " - " + companyName + " (" + d.getJobTitle() + ")");
        }
    }

    public void refreshApplicationsTable() {
        applicationsModel.setRowCount(0);
        List<Application> apps = applicationService.getAllApplications();
        for (Application a : apps) {
            String studentName = studentRepository.findById(a.getStudentId())
                    .map(Student::getName).orElse(a.getStudentId());
            String driveInfo = driveRepository.findById(a.getDriveId())
                    .map(d -> d.getJobTitle() + " (" + d.getCompanyId() + ")").orElse(a.getDriveId());

            applicationsModel.addRow(new Object[]{
                    a.getId(),
                    studentName + " (" + a.getStudentId() + ")",
                    driveInfo,
                    a.getStatus(),
                    a.getAppliedAt().toString().substring(0, 16).replace('T', ' '),
                    a.getRemarks()
            });
        }
    }
}
