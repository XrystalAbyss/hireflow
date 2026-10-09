package com.hireflow.ui;

import com.hireflow.exception.ValidationException;
import com.hireflow.model.Student;
import com.hireflow.service.StudentService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * Screen 2: Student Management Panel (Add, view, edit, search, and delete students).
 */
public class StudentPanel extends JPanel {

    private final StudentService studentService;
    private final Runnable onDataChanged;

    private DefaultTableModel tableModel;
    private JTable studentTable;
    private JTextField searchField;

    // Form inputs
    private JTextField idField;
    private JTextField nameField;
    private JTextField emailField;
    private JComboBox<String> branchCombo;
    private JTextField cgpaField;
    private JTextField gradYearField;
    private JCheckBox activeCheck;

    public StudentPanel(StudentService studentService, Runnable onDataChanged) {
        this.studentService = studentService;
        this.onDataChanged = onDataChanged;

        setLayout(new BorderLayout(16, 16));
        setBackground(UIUtils.BG_MAIN);
        setBorder(new EmptyBorder(24, 24, 24, 24));

        initUI();
        refreshTable();
    }

    private void initUI() {
        // Top Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);

        JLabel title = new JLabel("Student Management Registry");
        title.setFont(UIUtils.FONT_HEADER);
        title.setForeground(UIUtils.TEXT_PRIMARY);

        JLabel subtitle = new JLabel("Enroll student records, maintain academic profiles, and search across departments");
        subtitle.setFont(UIUtils.FONT_REGULAR);
        subtitle.setForeground(UIUtils.TEXT_SECONDARY);

        JPanel titlesBox = new JPanel(new GridLayout(2, 1, 2, 2));
        titlesBox.setOpaque(false);
        titlesBox.add(title);
        titlesBox.add(subtitle);
        headerPanel.add(titlesBox, BorderLayout.WEST);

        // Search Bar in Header
        JPanel searchBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        searchBox.setOpaque(false);
        searchField = UIUtils.createTextField(18);
        searchField.putClientProperty("JTextField.placeholderText", "Search name, branch, ID...");

        JButton btnSearch = UIUtils.createPrimaryButton("Search");
        btnSearch.addActionListener(e -> performSearch());

        JButton btnResetSearch = UIUtils.createSecondaryButton("Clear");
        btnResetSearch.addActionListener(e -> {
            searchField.setText("");
            refreshTable();
        });

        searchBox.add(new JLabel("Find:"));
        searchBox.add(searchField);
        searchBox.add(btnSearch);
        searchBox.add(btnResetSearch);

        headerPanel.add(searchBox, BorderLayout.EAST);
        add(headerPanel, BorderLayout.NORTH);

        // Center split: Table on Left/Center, Registration Form on Right
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.68);
        splitPane.setBorder(null);
        splitPane.setOpaque(false);

        // Table Panel
        JPanel tableContainer = new JPanel(new BorderLayout(0, 10));
        tableContainer.setOpaque(false);

        tableModel = new DefaultTableModel(new String[]{
                "Student ID", "Full Name", "Institutional Email", "Branch", "CGPA", "Grad Year", "Status"
        }, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };

        studentTable = new JTable(tableModel);
        UIUtils.styleTable(studentTable);
        studentTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane scrollPane = new JScrollPane(studentTable);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        // Action Toolbar below table
        JPanel tableActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        tableActions.setOpaque(false);

        JButton btnEdit = UIUtils.createSecondaryButton("Edit Selected");
        btnEdit.addActionListener(e -> openEditDialog());

        JButton btnDelete = UIUtils.createDangerButton("Delete Selected");
        btnDelete.addActionListener(e -> deleteSelectedStudent());

        tableActions.add(btnEdit);
        tableActions.add(btnDelete);

        JPanel tableCardContent = new JPanel(new BorderLayout(0, 10));
        tableCardContent.setOpaque(false);
        tableCardContent.add(scrollPane, BorderLayout.CENTER);
        tableCardContent.add(tableActions, BorderLayout.SOUTH);

        JPanel tableCard = UIUtils.createCard("Registered Candidate Roster", tableCardContent);
        tableContainer.add(tableCard, BorderLayout.CENTER);
        splitPane.setLeftComponent(tableContainer);

        // Registration Form Panel (Right side)
        JPanel formCard = createRegistrationFormPanel();
        splitPane.setRightComponent(formCard);

        add(splitPane, BorderLayout.CENTER);
    }

    private JPanel createRegistrationFormPanel() {
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 4, 5, 4);
        gbc.weightx = 1.0;
        gbc.gridx = 0;

        int row = 0;

        // ID
        formPanel.add(new JLabel("Student ID (e.g. STU-112):"), gbcRow(gbc, row++));
        idField = UIUtils.createTextField(14);
        formPanel.add(idField, gbcRow(gbc, row++));

        // Name
        formPanel.add(new JLabel("Full Name:"), gbcRow(gbc, row++));
        nameField = UIUtils.createTextField(14);
        formPanel.add(nameField, gbcRow(gbc, row++));

        // Email
        formPanel.add(new JLabel("Email Address:"), gbcRow(gbc, row++));
        emailField = UIUtils.createTextField(14);
        formPanel.add(emailField, gbcRow(gbc, row++));

        // Branch
        formPanel.add(new JLabel("Academic Branch:"), gbcRow(gbc, row++));
        branchCombo = UIUtils.createComboBox(new String[]{"CSE", "IT", "ECE", "MECH", "CIVIL", "EEE"});
        formPanel.add(branchCombo, gbcRow(gbc, row++));

        // CGPA
        formPanel.add(new JLabel("Cumulative CGPA (0.0 - 10.0):"), gbcRow(gbc, row++));
        cgpaField = UIUtils.createTextField(14);
        formPanel.add(cgpaField, gbcRow(gbc, row++));

        // Grad Year
        formPanel.add(new JLabel("Graduation Year:"), gbcRow(gbc, row++));
        gradYearField = UIUtils.createTextField(14);
        gradYearField.setText("2026");
        formPanel.add(gradYearField, gbcRow(gbc, row++));

        // Active
        activeCheck = new JCheckBox("Active & Eligible for Drives", true);
        activeCheck.setFont(UIUtils.FONT_REGULAR);
        activeCheck.setOpaque(false);
        formPanel.add(activeCheck, gbcRow(gbc, row++));

        // Submit Button
        JButton btnRegister = UIUtils.createSuccessButton("Register Student");
        btnRegister.addActionListener(e -> handleAddStudent());
        gbc.insets = new Insets(14, 4, 5, 4);
        formPanel.add(btnRegister, gbcRow(gbc, row++));

        JButton btnReset = UIUtils.createSecondaryButton("Reset Form");
        btnReset.addActionListener(e -> resetForm());
        gbc.insets = new Insets(4, 4, 5, 4);
        formPanel.add(btnReset, gbcRow(gbc, row));

        JPanel container = new JPanel(new BorderLayout());
        container.setOpaque(false);
        container.add(formPanel, BorderLayout.NORTH);

        return UIUtils.createCard("Register New Candidate", container);
    }

    private GridBagConstraints gbcRow(GridBagConstraints gbc, int row) {
        gbc.gridy = row;
        return gbc;
    }

    private void handleAddStudent() {
        try {
            String id = idField.getText();
            String name = nameField.getText();
            String email = emailField.getText();
            String branch = (String) branchCombo.getSelectedItem();
            String cgpaStr = cgpaField.getText().trim();
            String gradYearStr = gradYearField.getText().trim();
            boolean active = activeCheck.isSelected();

            double cgpa = Double.parseDouble(cgpaStr);
            int gradYear = Integer.parseInt(gradYearStr);

            studentService.registerStudent(id, name, email, branch, cgpa, gradYear, active);

            UIUtils.showSuccess(this, "Student registered successfully: " + id.trim().toUpperCase(), "Success");
            resetForm();
            refreshTable();
            if (onDataChanged != null) onDataChanged.run();
        } catch (NumberFormatException ex) {
            UIUtils.showError(this, "CGPA must be a valid decimal (e.g. 8.5) and Graduation Year must be an integer (e.g. 2026).", "Input Format Error");
        } catch (Exception ex) {
            UIUtils.showError(this, ex.getMessage(), "Registration Error");
        }
    }

    private void openEditDialog() {
        int selectedRow = studentTable.getSelectedRow();
        if (selectedRow == -1) {
            UIUtils.showWarning(this, "Please select a student from the table to edit.", "No Selection");
            return;
        }

        String studentId = (String) tableModel.getValueAt(selectedRow, 0);
        Student student = studentService.getStudentOrThrow(studentId);

        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Edit Student: " + student.getId(), true);
        dialog.setLayout(new BorderLayout());
        dialog.setSize(420, 460);
        dialog.setLocationRelativeTo(this);

        JPanel editPanel = new JPanel(new GridLayout(7, 2, 10, 12));
        editPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        JTextField editName = UIUtils.createTextField(14);
        editName.setText(student.getName());

        JTextField editEmail = UIUtils.createTextField(14);
        editEmail.setText(student.getEmail());

        JComboBox<String> editBranch = UIUtils.createComboBox(new String[]{"CSE", "IT", "ECE", "MECH", "CIVIL", "EEE"});
        editBranch.setSelectedItem(student.getBranch());

        JTextField editCgpa = UIUtils.createTextField(14);
        editCgpa.setText(String.valueOf(student.getCgpa()));

        JTextField editGradYear = UIUtils.createTextField(14);
        editGradYear.setText(String.valueOf(student.getGraduationYear()));

        JCheckBox editActive = new JCheckBox("Active", student.isActive());

        editPanel.add(new JLabel("Full Name:")); editPanel.add(editName);
        editPanel.add(new JLabel("Email Address:")); editPanel.add(editEmail);
        editPanel.add(new JLabel("Academic Branch:")); editPanel.add(editBranch);
        editPanel.add(new JLabel("CGPA:")); editPanel.add(editCgpa);
        editPanel.add(new JLabel("Graduation Year:")); editPanel.add(editGradYear);
        editPanel.add(new JLabel("Enrollment Status:")); editPanel.add(editActive);

        JPanel buttonBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        JButton btnSave = UIUtils.createSuccessButton("Save Changes");
        btnSave.addActionListener(e -> {
            try {
                student.setName(editName.getText());
                student.setEmail(editEmail.getText());
                student.setBranch((String) editBranch.getSelectedItem());
                student.setCgpa(Double.parseDouble(editCgpa.getText().trim()));
                student.setGraduationYear(Integer.parseInt(editGradYear.getText().trim()));
                student.setActive(editActive.isSelected());

                studentService.updateStudent(student);
                dialog.dispose();
                UIUtils.showSuccess(this, "Student profile updated successfully.", "Saved");
                refreshTable();
                if (onDataChanged != null) onDataChanged.run();
            } catch (Exception ex) {
                UIUtils.showError(dialog, ex.getMessage(), "Update Error");
            }
        });

        JButton btnCancel = UIUtils.createSecondaryButton("Cancel");
        btnCancel.addActionListener(e -> dialog.dispose());

        buttonBox.add(btnCancel);
        buttonBox.add(btnSave);

        dialog.add(editPanel, BorderLayout.CENTER);
        dialog.add(buttonBox, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private void deleteSelectedStudent() {
        int selectedRow = studentTable.getSelectedRow();
        if (selectedRow == -1) {
            UIUtils.showWarning(this, "Please select a student to delete.", "No Selection");
            return;
        }

        String studentId = (String) tableModel.getValueAt(selectedRow, 0);
        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to delete student '" + studentId + "'? This action cannot be undone.",
                "Confirm Deletion", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                studentService.deleteStudent(studentId);
                UIUtils.showSuccess(this, "Student deleted successfully.", "Deleted");
                refreshTable();
                if (onDataChanged != null) onDataChanged.run();
            } catch (Exception ex) {
                UIUtils.showError(this, ex.getMessage(), "Cannot Delete Student");
            }
        }
    }

    private void performSearch() {
        String query = searchField.getText().trim();
        List<Student> results = studentService.searchStudents(query);
        populateTable(results);
    }

    private void resetForm() {
        idField.setText("");
        nameField.setText("");
        emailField.setText("");
        cgpaField.setText("");
        gradYearField.setText("2026");
        activeCheck.setSelected(true);
    }

    public void refreshTable() {
        populateTable(studentService.getAllStudents());
    }

    private void populateTable(List<Student> students) {
        tableModel.setRowCount(0);
        for (Student s : students) {
            tableModel.addRow(new Object[]{
                    s.getId(),
                    s.getName(),
                    s.getEmail(),
                    s.getBranch(),
                    String.format("%.2f", s.getCgpa()),
                    s.getGraduationYear(),
                    s.isActive() ? "Active" : "Inactive"
            });
        }
    }
}
