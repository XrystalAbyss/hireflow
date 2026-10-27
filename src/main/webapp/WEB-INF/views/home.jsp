<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="HireFlow - Campus Recruitment &amp; Placement Portal" />
<c:set var="activeNav" value="home" />
<%@ include file="/WEB-INF/views/common/header.jspf" %>

<section class="hero-section">
    <span class="hero-badge">Stage 3A &bull; Jakarta Servlets &amp; MVC</span>
    <h1 class="hero-title">Campus Recruitment &amp; Placement Automation</h1>
    <p class="hero-subtitle">
        Empowering university Training &amp; Placement Cells with real-time corporate recruitment coordination,
        strict academic eligibility enforcement, and deterministic conflict-free interview scheduling.
    </p>
    <div class="hero-actions">
        <a href="${pageContext.request.contextPath}/drives" class="btn btn-primary">
            Explore Active Drives &rarr;
        </a>
        <a href="${pageContext.request.contextPath}/health" class="btn btn-outline" target="_blank">
            System Health API
        </a>
    </div>
</section>

<div class="stats-grid">
    <div class="stat-card">
        <span class="stat-label">Architecture</span>
        <span class="stat-value" style="font-size: 1.5rem; color: var(--primary);">Jakarta EE 10</span>
        <span class="stat-desc">Pure Servlets 6.0, JSP 3.1 &amp; Standard MVC</span>
    </div>
    <div class="stat-card">
        <span class="stat-label">Persistence</span>
        <span class="stat-value" style="font-size: 1.5rem; color: var(--cyan-accent);">PostgreSQL 16</span>
        <span class="stat-desc">HikariCP connection pool with Flyway migrations</span>
    </div>
    <div class="stat-card">
        <span class="stat-label">Algorithmic Scheduling</span>
        <span class="stat-value" style="font-size: 1.5rem; color: var(--success);">7 Constraints</span>
        <span class="stat-desc">Zero double-booking for interviewers &amp; rooms</span>
    </div>
    <div class="stat-card">
        <span class="stat-label">Verification</span>
        <span class="stat-value" style="font-size: 1.5rem; color: var(--warning);">285+ Tests</span>
        <span class="stat-desc">100% automated regression test coverage</span>
    </div>
</div>

<div class="card">
    <div class="card-header">
        <div>
            <h2 class="card-title">Placement Portal Roadmap</h2>
            <p class="card-subtitle">Continuous Assessment Program (CAP-6) Multi-Stage Rollout</p>
        </div>
    </div>
    <div class="card-body">
        <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 1.5rem;">
            <div style="border: 1px solid var(--border-light); border-radius: var(--radius-sm); padding: 1.25rem; background-color: #fafafa;">
                <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 0.75rem;">
                    <span style="font-weight: 700; color: var(--primary);">Stage 3A (Current)</span>
                    <span class="status-pill status-open"><span class="dot"></span> Active</span>
                </div>
                <h3 style="font-size: 1.05rem; margin-bottom: 0.5rem; font-weight: 600;">MVC Web Foundation &amp; Public Drives</h3>
                <p style="font-size: 0.85rem; color: var(--text-secondary); line-height: 1.5;">
                    Centralized ServletContext lifecycle, HikariCP connection reuse, responsive HTML5/CSS3 views,
                    and public recruitment drive exploration without authentication boundaries.
                </p>
            </div>
            <div style="border: 1px solid var(--border-light); border-radius: var(--radius-sm); padding: 1.25rem; background-color: #fafafa;">
                <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 0.75rem;">
                    <span style="font-weight: 700; color: #64748b;">Stage 3B (Upcoming)</span>
                    <span class="nav-pill-future"><span class="dot"></span> In Planning</span>
                </div>
                <h3 style="font-size: 1.05rem; margin-bottom: 0.5rem; font-weight: 600;">Student Self-Service Portal</h3>
                <p style="font-size: 0.85rem; color: var(--text-secondary); line-height: 1.5;">
                    Candidate registration, academic profile management, real-time CGPA and branch eligibility evaluator,
                    and 1-click application submission with transactional status tracking.
                </p>
            </div>
            <div style="border: 1px solid var(--border-light); border-radius: var(--radius-sm); padding: 1.25rem; background-color: #fafafa;">
                <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 0.75rem;">
                    <span style="font-weight: 700; color: #64748b;">Stage 3C (Upcoming)</span>
                    <span class="nav-pill-future"><span class="dot"></span> In Planning</span>
                </div>
                <h3 style="font-size: 1.05rem; margin-bottom: 0.5rem; font-weight: 600;">TPC Officer Administration</h3>
                <p style="font-size: 0.85rem; color: var(--text-secondary); line-height: 1.5;">
                    Session-based administrative authentication, CSRF tokens, corporate drive creation,
                    conflict-free interview timetable generation, and candidate pipeline selection.
                </p>
            </div>
        </div>
    </div>
</div>

<%@ include file="/WEB-INF/views/common/footer.jspf" %>
