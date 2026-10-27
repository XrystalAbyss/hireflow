<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Placement Policies &amp; Architecture - HireFlow" />
<c:set var="activeNav" value="about" />
<c:set var="breadcrumbCurrent" value="About &amp; Policies" />
<%@ include file="/WEB-INF/views/common/header.jspf" %>

<div style="margin-bottom: 2rem;">
    <h1 style="font-size: 2rem; font-weight: 800; letter-spacing: -0.5px; margin-bottom: 0.5rem; color: var(--text-primary);">
        Campus Placement Guidelines &amp; Architecture
    </h1>
    <p style="color: var(--text-secondary); font-size: 1rem;">
        Official policies, academic eligibility criteria, conflict-free scheduling standards, and enterprise architecture governing the university placement process.
    </p>
</div>

<div style="display: grid; grid-template-columns: 2fr 1fr; gap: 2rem; margin-bottom: 2.5rem;">
    <div>
        <div class="card">
            <div class="card-header">
                <h2 class="card-title">1. Academic Eligibility &amp; Cutoff Rules</h2>
            </div>
            <div class="card-body" style="font-size: 0.92rem; color: var(--text-secondary); line-height: 1.6;">
                <p style="margin-bottom: 1rem;">
                    Campus placement drives coordinated through HireFlow enforce automated, deterministic academic eligibility validation at the service tier:
                </p>
                <ul style="margin-left: 1.5rem; margin-bottom: 1rem;">
                    <li style="margin-bottom: 0.5rem;">
                        <strong>Minimum CGPA Thresholds:</strong> Students must satisfy the visiting company's cumulative grade point average cutoff at the moment of application submission.
                    </li>
                    <li style="margin-bottom: 0.5rem;">
                        <strong>Departmental Branch Authorization:</strong> Drives define permitted academic branches (e.g. <code>CSE</code>, <code>IT</code>, <code>ECE</code>, <code>MECH</code>). Applications from unauthorized branches are automatically rejected.
                    </li>
                    <li style="margin-bottom: 0.5rem;">
                        <strong>Strict Application Deadlines:</strong> The application window closes automatically at 23:59:59 on the specified deadline date. Post-deadline submissions are blocked.
                    </li>
                    <li>
                        <strong>Duplicate Application Prevention:</strong> A student cannot submit multiple concurrent applications to the same recruitment drive.
                    </li>
                </ul>
            </div>
        </div>

        <div class="card">
            <div class="card-header">
                <h2 class="card-title">2. Algorithmic Interview Scheduling Standards</h2>
            </div>
            <div class="card-body" style="font-size: 0.92rem; color: var(--text-secondary); line-height: 1.6;">
                <p style="margin-bottom: 1rem;">
                    HireFlow employs a greedy scheduling engine designed to eliminate interview logistics bottlenecks while satisfying seven strict real-world constraints:
                </p>
                <ol style="margin-left: 1.5rem;">
                    <li style="margin-bottom: 0.4rem;"><strong>Candidate Temporal Exclusion:</strong> No student is scheduled for more than one interview simultaneously.</li>
                    <li style="margin-bottom: 0.4rem;"><strong>Interviewer Concurrency Safety:</strong> Interviewers can conduct only one interview session per timeslot.</li>
                    <li style="margin-bottom: 0.4rem;"><strong>Room Resource Mutex:</strong> Interview booths cannot host concurrent sessions (enforced via PostgreSQL GiST temporal exclusion constraints).</li>
                    <li style="margin-bottom: 0.4rem;"><strong>Buffer Interval Protection:</strong> Configurable recovery buffers (default 15 mins) are maintained between consecutive rounds.</li>
                    <li style="margin-bottom: 0.4rem;"><strong>Drive Window Adherence:</strong> Sessions are confined to the official recruitment drive schedule window.</li>
                    <li style="margin-bottom: 0.4rem;"><strong>Transactional Publication:</strong> Publishing timetables confirms status transitions atomically inside ACID transactions.</li>
                    <li><strong>Deterministic Resource Locking:</strong> Global lock ordering (Applications &rarr; Rooms &rarr; Interviewers &rarr; Students) guarantees deadlock-free schedule publication.</li>
                </ol>
            </div>
        </div>
    </div>

    <div>
        <div class="card">
            <div class="card-header">
                <h2 class="card-title">Portal Quick Links</h2>
            </div>
            <div class="card-body">
                <div style="display: flex; flex-direction: column; gap: 0.75rem;">
                    <a href="${pageContext.request.contextPath}/drives" class="btn btn-primary" style="justify-content: center;">
                        Browse Public Drives &rarr;
                    </a>
                    <a href="${pageContext.request.contextPath}/student/profile" class="btn btn-outline" style="justify-content: center; color: var(--text-primary); border-color: var(--border-light);">
                        Student Portal Access
                    </a>
                    <a href="${pageContext.request.contextPath}/officer/dashboard" class="btn btn-outline" style="justify-content: center; color: var(--text-primary); border-color: var(--border-light);">
                        TPC Officer Console
                    </a>
                </div>
            </div>
        </div>

        <div class="card">
            <div class="card-header">
                <h2 class="card-title">Technology Stack</h2>
            </div>
            <div class="card-body" style="font-size: 0.85rem; color: var(--text-secondary);">
                <p style="margin-bottom: 0.5rem;"><strong>Runtime:</strong> Java 21 LTS</p>
                <p style="margin-bottom: 0.5rem;"><strong>Web Tier:</strong> Jakarta Servlets 6.0</p>
                <p style="margin-bottom: 0.5rem;"><strong>View Tier:</strong> JSP 3.1 &amp; JSTL 3.0</p>
                <p style="margin-bottom: 0.5rem;"><strong>Container:</strong> Apache Tomcat 10.1</p>
                <p style="margin-bottom: 0.5rem;"><strong>Database:</strong> PostgreSQL 16 (HikariCP)</p>
                <p style="margin-bottom: 0.5rem;"><strong>Desktop:</strong> Standard Java Swing GUI</p>
                <p><strong>Testing:</strong> JUnit 5 &amp; Mockito</p>
            </div>
        </div>
    </div>
</div>

<%@ include file="/WEB-INF/views/common/footer.jspf" %>
