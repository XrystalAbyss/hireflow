<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="My Academic Profile - HireFlow" />
<c:set var="breadcrumbCurrent" value="Academic Profile" />
<%@ include file="/WEB-INF/views/common/header.jspf" %>

<div style="max-width: 900px; margin: 2rem auto; padding: 0 1rem;">
    <!-- Profile Header Card -->
    <div class="card" style="margin-bottom: 2rem;">
        <div class="card-body" style="padding: 2rem; display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 1rem;">
            <div>
                <div style="display: flex; align-items: center; gap: 0.75rem; margin-bottom: 0.5rem;">
                    <span class="badge badge-role"><c:out value="${student.branch}" /></span>
                    <span class="badge badge-cutoff">ID: <c:out value="${student.id}" /></span>
                    <c:choose>
                        <c:when test="${student.active}">
                            <span class="badge badge-active">ACTIVE STATUS</span>
                        </c:when>
                        <c:otherwise>
                            <span class="badge badge-closed">INACTIVE STATUS</span>
                        </c:otherwise>
                    </c:choose>
                </div>
                <h1 style="font-size: 1.85rem; font-weight: 800; color: var(--text-primary); margin: 0 0 0.25rem;" id="studentProfileName">
                    <c:out value="${student.name}" />
                </h1>
                <p style="color: var(--text-muted); margin: 0; font-size: 0.875rem;">
                    Official University Student Record | Account: <code><c:out value="${principal.name}" /></code>
                </p>
            </div>
            <div style="display: flex; gap: 0.75rem;">
                <a href="${pageContext.request.contextPath}/student/drives" class="btn btn-primary" id="viewDrivesFromProfileBtn">
                    Browse Drives &rarr;
                </a>
            </div>
        </div>
    </div>

    <!-- Academic Details Grid -->
    <div class="card" style="margin-bottom: 2rem;">
        <div class="card-header" style="padding: 1.25rem 1.75rem; border-bottom: 1px solid var(--border);">
            <h2 style="font-size: 1.15rem; font-weight: 700; color: var(--text-primary); margin: 0;">
                Verified Academic Credentials
            </h2>
        </div>
        <div class="card-body" style="padding: 1.75rem;">
            <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 1.5rem;">
                <!-- Roll / ID -->
                <div style="background: var(--bg-secondary); padding: 1.25rem; border-radius: var(--radius-sm); border: 1px solid var(--border);">
                    <div style="font-size: 0.75rem; font-weight: 600; text-transform: uppercase; color: var(--text-muted); margin-bottom: 0.35rem;">
                        Student Identifier
                    </div>
                    <div style="font-size: 1.25rem; font-weight: 700; color: var(--text-primary);" id="studentIdValue">
                        <c:out value="${student.id}" />
                    </div>
                </div>

                <!-- Department / Branch -->
                <div style="background: var(--bg-secondary); padding: 1.25rem; border-radius: var(--radius-sm); border: 1px solid var(--border);">
                    <div style="font-size: 0.75rem; font-weight: 600; text-transform: uppercase; color: var(--text-muted); margin-bottom: 0.35rem;">
                        Academic Department
                    </div>
                    <div style="font-size: 1.25rem; font-weight: 700; color: var(--text-primary);" id="studentBranchValue">
                        <c:out value="${student.branch}" />
                    </div>
                </div>

                <!-- Cumulative GPA -->
                <div style="background: var(--bg-secondary); padding: 1.25rem; border-radius: var(--radius-sm); border: 1px solid var(--border);">
                    <div style="font-size: 0.75rem; font-weight: 600; text-transform: uppercase; color: var(--text-muted); margin-bottom: 0.35rem;">
                        Cumulative GPA (CGPA)
                    </div>
                    <div style="font-size: 1.25rem; font-weight: 800; color: var(--primary);" id="studentCgpaValue">
                        <c:out value="${student.cgpa}" /> / 10.00
                    </div>
                </div>

                <!-- Graduation Year -->
                <div style="background: var(--bg-secondary); padding: 1.25rem; border-radius: var(--radius-sm); border: 1px solid var(--border);">
                    <div style="font-size: 0.75rem; font-weight: 600; text-transform: uppercase; color: var(--text-muted); margin-bottom: 0.35rem;">
                        Graduation Class
                    </div>
                    <div style="font-size: 1.25rem; font-weight: 700; color: var(--text-primary);" id="studentGraduationYearValue">
                        Class of <c:out value="${student.graduationYear}" />
                    </div>
                </div>

                <!-- Official Email -->
                <div style="background: var(--bg-secondary); padding: 1.25rem; border-radius: var(--radius-sm); border: 1px solid var(--border); grid-column: 1 / -1;">
                    <div style="font-size: 0.75rem; font-weight: 600; text-transform: uppercase; color: var(--text-muted); margin-bottom: 0.35rem;">
                        Institutional Email Address
                    </div>
                    <div style="font-size: 1.1rem; font-weight: 600; color: var(--text-primary);" id="studentEmailValue">
                        <c:out value="${student.email}" />
                    </div>
                </div>
            </div>
        </div>
    </div>

    <!-- Read-Only Policy Advisory -->
    <div class="card" style="border-left: 4px solid var(--info);">
        <div class="card-body" style="padding: 1.5rem 1.75rem; display: flex; gap: 1rem; align-items: flex-start;">
            <div style="font-size: 1.5rem; line-height: 1;">ℹ️</div>
            <div>
                <h4 style="font-size: 1rem; font-weight: 700; color: var(--text-primary); margin: 0 0 0.35rem;">
                    Official Academic Record Notice
                </h4>
                <p style="color: var(--text-secondary); font-size: 0.875rem; line-height: 1.6; margin: 0;">
                    Student records are synchronized directly from the University Academic Registry.
                    To preserve placement audit integrity, candidate details are read-only.
                    If you detect an error in your CGPA or branch, please submit an official correction request to the Training &amp; Placement Cell.
                </p>
            </div>
        </div>
    </div>

    <!-- Back Navigation -->
    <div style="margin-top: 1.5rem; display: flex; gap: 1rem;">
        <a href="${pageContext.request.contextPath}/student/dashboard" class="btn btn-outline">
            &larr; Back to Dashboard
        </a>
        <a href="${pageContext.request.contextPath}/student/applications" class="btn btn-outline">
            My Submitted Applications &rarr;
        </a>
    </div>
</div>

<%@ include file="/WEB-INF/views/common/footer.jspf" %>
