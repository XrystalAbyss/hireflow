package com.hireflow.ui;

import com.hireflow.model.Company;
import com.hireflow.model.RecruitmentDrive;
import com.hireflow.repository.CompanyRepository;
import com.hireflow.service.RecruitmentDriveService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Screen 3: Company and Recruitment Drive Management Panel.
 */
public class CompanyDrivePanel extends JPanel {

    private final RecruitmentDriveService driveService;
    private final CompanyRepository companyRepository;
    private final Runnable onDataChanged;

    // Drives Table & Inputs
    private DefaultTableModel drivesModel;
    private JTable drivesTable;
    private JTextField driveIdField;
    private JComboBox<String> companyDropdown;
    private JTextField jobTitleField;
    private JTextField minCgpaField;
    private JTextField branchesField;
    private JTextField deadlineField;

    // Companies Table & Inputs
    private DefaultTableModel companyModel;
    private JTable companyTable;
    private JTextField compIdField;
    private JTextField compNameField;
    private JTextField compIndustryField;
    private final com.hireflow.repository.InterviewerRepository interviewerRepository;

    public CompanyDrivePanel(RecruitmentDriveService driveService,
                             CompanyRepository companyRepository,
                             Runnable onDataChanged) {
        this(driveService, companyRepository, null, onDataChanged);
    }

    public CompanyDrivePanel(RecruitmentDriveService driveService,
                             CompanyRepository companyRepository,
                             com.hireflow.repository.InterviewerRepository interviewerRepository,
                             Runnable onDataChanged) {
        this.driveService = driveService;
        this.companyRepository = companyRepository;
        this.interviewerRepository = interviewerRepository;
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

        JLabel title = new JLabel("Corporate Partners & Recruitment Drives");
        title.setFont(UIUtils.FONT_HEADER);
        title.setForeground(UIUtils.TEXT_PRIMARY);

        JLabel subtitle = new JLabel("Manage visiting employers, configure campus drives, eligibility thresholds, and application windows");
        subtitle.setFont(UIUtils.FONT_REGULAR);
        subtitle.setForeground(UIUtils.TEXT_SECONDARY);

        JPanel titlesBox = new JPanel(new GridLayout(2, 1, 2, 2));
        titlesBox.setOpaque(false);
        titlesBox.add(title);
        titlesBox.add(subtitle);
        headerPanel.add(titlesBox, BorderLayout.WEST);

        add(headerPanel, BorderLayout.NORTH);

        // Tabbed Pane for Drives vs Companies
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(UIUtils.FONT_BOLD);
        tabbedPane.setBackground(Color.WHITE);

        tabbedPane.addTab("Recruitment Drives", createDrivesTab());
        tabbedPane.addTab("Partner Companies", createCompaniesTab());

        add(tabbedPane, BorderLayout.CENTER);
    }

    private JPanel createDrivesTab() {
        JPanel panel = new JPanel(new BorderLayout(16, 16));
        panel.setBackground(UIUtils.BG_MAIN);
        panel.setBorder(new EmptyBorder(16, 0, 0, 0));

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.68);
        splitPane.setBorder(null);
        splitPane.setOpaque(false);

        // Drives Table
        drivesModel = new DefaultTableModel(new String[]{
                "Drive ID", "Company", "Job Title", "Min CGPA", "Eligible Branches", "Deadline", "Status"
        }, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };

        drivesTable = new JTable(drivesModel);
        UIUtils.styleTable(drivesTable);
        drivesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane scrollPane = new JScrollPane(drivesTable);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        JPanel tableActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        tableActions.setOpaque(false);
        JButton btnDeleteDrive = UIUtils.createDangerButton("Delete Selected Drive");
        btnDeleteDrive.addActionListener(e -> deleteSelectedDrive());
        tableActions.add(btnDeleteDrive);

        JPanel tableContent = new JPanel(new BorderLayout(0, 10));
        tableContent.setOpaque(false);
        tableContent.add(scrollPane, BorderLayout.CENTER);
        tableContent.add(tableActions, BorderLayout.SOUTH);

        splitPane.setLeftComponent(UIUtils.createCard("Active & Scheduled Placement Drives", tableContent));

        // Add Drive Form (Right side)
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 4, 5, 4);
        gbc.weightx = 1.0;
        gbc.gridx = 0;
        int row = 0;

        formPanel.add(new JLabel("Drive ID (e.g. DRV-106):"), gbcRow(gbc, row++));
        driveIdField = UIUtils.createTextField(14);
        formPanel.add(driveIdField, gbcRow(gbc, row++));

        formPanel.add(new JLabel("Visiting Company:"), gbcRow(gbc, row++));
        companyDropdown = new JComboBox<>();
        companyDropdown.setFont(UIUtils.FONT_REGULAR);
        formPanel.add(companyDropdown, gbcRow(gbc, row++));

        formPanel.add(new JLabel("Job Title / Role:"), gbcRow(gbc, row++));
        jobTitleField = UIUtils.createTextField(14);
        formPanel.add(jobTitleField, gbcRow(gbc, row++));

        formPanel.add(new JLabel("Minimum Cutoff CGPA:"), gbcRow(gbc, row++));
        minCgpaField = UIUtils.createTextField(14);
        formPanel.add(minCgpaField, gbcRow(gbc, row++));

        formPanel.add(new JLabel("Eligible Branches (comma-separated):"), gbcRow(gbc, row++));
        branchesField = UIUtils.createTextField(14);
        branchesField.setText("CSE, IT");
        formPanel.add(branchesField, gbcRow(gbc, row++));

        formPanel.add(new JLabel("Application Deadline (YYYY-MM-DD):"), gbcRow(gbc, row++));
        deadlineField = UIUtils.createTextField(14);
        deadlineField.setText(LocalDate.now().plusDays(14).toString());
        formPanel.add(deadlineField, gbcRow(gbc, row++));

        JButton btnCreateDrive = UIUtils.createSuccessButton("Publish Recruitment Drive");
        btnCreateDrive.addActionListener(e -> handleCreateDrive());
        gbc.insets = new Insets(14, 4, 5, 4);
        formPanel.add(btnCreateDrive, gbcRow(gbc, row));

        JPanel formContainer = new JPanel(new BorderLayout());
        formContainer.setOpaque(false);
        formContainer.add(formPanel, BorderLayout.NORTH);

        splitPane.setRightComponent(UIUtils.createCard("Configure Recruitment Drive", formContainer));

        panel.add(splitPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createCompaniesTab() {
        JPanel panel = new JPanel(new BorderLayout(16, 16));
        panel.setBackground(UIUtils.BG_MAIN);
        panel.setBorder(new EmptyBorder(16, 0, 0, 0));

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.68);
        splitPane.setBorder(null);
        splitPane.setOpaque(false);

        // Companies Table
        companyModel = new DefaultTableModel(new String[]{"Company ID", "Company Name", "Industry Domain"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };

        companyTable = new JTable(companyModel);
        UIUtils.styleTable(companyTable);
        companyTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane scrollPane = new JScrollPane(companyTable);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        JPanel tableActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        tableActions.setOpaque(false);
        JButton btnDeleteCompany = UIUtils.createDangerButton("Delete Selected Company");
        btnDeleteCompany.addActionListener(e -> deleteSelectedCompany());
        tableActions.add(btnDeleteCompany);

        JPanel tableContent = new JPanel(new BorderLayout(0, 10));
        tableContent.setOpaque(false);
        tableContent.add(scrollPane, BorderLayout.CENTER);
        tableContent.add(tableActions, BorderLayout.SOUTH);

        splitPane.setLeftComponent(UIUtils.createCard("Registered Corporate Partners", tableContent));

        // Add Company Form (Right side)
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 4, 5, 4);
        gbc.weightx = 1.0;
        gbc.gridx = 0;
        int row = 0;

        formPanel.add(new JLabel("Company ID (e.g. CMP-005):"), gbcRow(gbc, row++));
        compIdField = UIUtils.createTextField(14);
        formPanel.add(compIdField, gbcRow(gbc, row++));

        formPanel.add(new JLabel("Company Name:"), gbcRow(gbc, row++));
        compNameField = UIUtils.createTextField(14);
        formPanel.add(compNameField, gbcRow(gbc, row++));

        formPanel.add(new JLabel("Industry Domain:"), gbcRow(gbc, row++));
        compIndustryField = UIUtils.createTextField(14);
        formPanel.add(compIndustryField, gbcRow(gbc, row++));

        JButton btnAddCompany = UIUtils.createSuccessButton("Register Company");
        btnAddCompany.addActionListener(e -> handleAddCompany());
        gbc.insets = new Insets(14, 4, 5, 4);
        formPanel.add(btnAddCompany, gbcRow(gbc, row));

        JPanel formContainer = new JPanel(new BorderLayout());
        formContainer.setOpaque(false);
        formContainer.add(formPanel, BorderLayout.NORTH);

        splitPane.setRightComponent(UIUtils.createCard("Register New Corporate Partner", formContainer));

        panel.add(splitPane, BorderLayout.CENTER);
        return panel;
    }

    private GridBagConstraints gbcRow(GridBagConstraints gbc, int row) {
        gbc.gridy = row;
        return gbc;
    }

    private void handleCreateDrive() {
        try {
            String id = driveIdField.getText();
            String selectedComp = (String) companyDropdown.getSelectedItem();
            if (selectedComp == null || !selectedComp.contains(" - ")) {
                UIUtils.showError(this, "Please select a valid company.", "Selection Error");
                return;
            }
            String companyId = selectedComp.split(" - ")[0].trim();
            String jobTitle = jobTitleField.getText();
            double minCgpa = Double.parseDouble(minCgpaField.getText().trim());

            String branchesRaw = branchesField.getText().trim();
            Set<String> branches = Arrays.stream(branchesRaw.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());

            LocalDate deadline = LocalDate.parse(deadlineField.getText().trim());

            driveService.createDrive(id, companyId, jobTitle, minCgpa, branches, deadline);

            UIUtils.showSuccess(this, "Recruitment drive created successfully: " + id.trim().toUpperCase(), "Success");
            driveIdField.setText("");
            jobTitleField.setText("");
            minCgpaField.setText("");
            refreshDrivesTable();
            if (onDataChanged != null) onDataChanged.run();
        } catch (NumberFormatException ex) {
            UIUtils.showError(this, "Minimum CGPA must be a valid number (e.g. 7.50).", "Input Format Error");
        } catch (DateTimeParseException ex) {
            UIUtils.showError(this, "Deadline must follow format YYYY-MM-DD (e.g. 2026-11-15).", "Date Format Error");
        } catch (Exception ex) {
            UIUtils.showError(this, ex.getMessage(), "Error Creating Drive");
        }
    }

    private void handleAddCompany() {
        try {
            String id = compIdField.getText().trim().toUpperCase();
            String name = compNameField.getText().trim();
            String industry = compIndustryField.getText().trim();

            companyRepository.add(new Company(id, name, industry));

            UIUtils.showSuccess(this, "Company registered successfully: " + name, "Success");
            compIdField.setText("");
            compNameField.setText("");
            compIndustryField.setText("");
            refreshCompaniesTable();
            refreshCompanyDropdown();
            if (onDataChanged != null) onDataChanged.run();
        } catch (Exception ex) {
            UIUtils.showError(this, ex.getMessage(), "Error Registering Company");
        }
    }

    private void deleteSelectedDrive() {
        int row = drivesTable.getSelectedRow();
        if (row == -1) {
            UIUtils.showWarning(this, "Please select a recruitment drive to delete.", "No Selection");
            return;
        }

        String driveId = (String) drivesModel.getValueAt(row, 0);
        int confirm = JOptionPane.showConfirmDialog(this,
                "Delete recruitment drive '" + driveId + "'?", "Confirm", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            try {
                driveService.deleteDrive(driveId);
                UIUtils.showSuccess(this, "Recruitment drive deleted successfully.", "Deleted");
                refreshDrivesTable();
                if (onDataChanged != null) onDataChanged.run();
            } catch (Exception ex) {
                UIUtils.showError(this, ex.getMessage(), "Cannot Delete Drive");
            }
        }
    }

    private void deleteSelectedCompany() {
        int row = companyTable.getSelectedRow();
        if (row == -1) {
            UIUtils.showWarning(this, "Please select a company to delete.", "No Selection");
            return;
        }

        String compId = (String) companyModel.getValueAt(row, 0);
        int confirm = JOptionPane.showConfirmDialog(this,
                "Delete company '" + compId + "'?", "Confirm", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            try {
                List<RecruitmentDrive> drives = driveService.getDrivesForCompany(compId);
                if (!drives.isEmpty()) {
                    throw new com.hireflow.exception.ValidationException(String.format(
                            "Cannot delete company '%s' because %d recruitment drive(s) are registered under this corporate partner.",
                            compId, drives.size()
                    ));
                }
                if (interviewerRepository != null) {
                    List<com.hireflow.model.Interviewer> interviewers = interviewerRepository.findByCompanyId(compId);
                    if (!interviewers.isEmpty()) {
                        throw new com.hireflow.exception.ValidationException(String.format(
                                "Cannot delete company '%s' because %d interviewer(s) are registered under this corporate partner.",
                                compId, interviewers.size()
                        ));
                    }
                }
                companyRepository.deleteById(compId);
                UIUtils.showSuccess(this, "Company deleted successfully.", "Deleted");
                refreshCompaniesTable();
                refreshCompanyDropdown();
                if (onDataChanged != null) onDataChanged.run();
            } catch (Exception ex) {
                UIUtils.showError(this, ex.getMessage(), "Cannot Delete Company");
            }
        }
    }

    public void refreshAll() {
        refreshCompaniesTable();
        refreshCompanyDropdown();
        refreshDrivesTable();
    }

    public void refreshCompaniesTable() {
        companyModel.setRowCount(0);
        List<Company> companies = companyRepository.findAll();
        for (Company c : companies) {
            companyModel.addRow(new Object[]{c.getId(), c.getName(), c.getIndustry()});
        }
    }

    public void refreshCompanyDropdown() {
        companyDropdown.removeAllItems();
        List<Company> companies = companyRepository.findAll();
        for (Company c : companies) {
            companyDropdown.addItem(c.getId() + " - " + c.getName());
        }
    }

    public void refreshDrivesTable() {
        drivesModel.setRowCount(0);
        List<RecruitmentDrive> drives = driveService.getAllDrives();
        LocalDate today = LocalDate.now();
        for (RecruitmentDrive d : drives) {
            String companyName = companyRepository.findById(d.getCompanyId())
                    .map(Company::getName).orElse(d.getCompanyId());
            boolean active = !d.isDeadlinePassed(today);
            drivesModel.addRow(new Object[]{
                    d.getId(),
                    companyName,
                    d.getJobTitle(),
                    String.format("%.2f", d.getMinimumCgpa()),
                    d.getEligibleBranches(),
                    d.getApplicationDeadline(),
                    active ? "Active" : "Deadline Expired"
            });
        }
    }
}
