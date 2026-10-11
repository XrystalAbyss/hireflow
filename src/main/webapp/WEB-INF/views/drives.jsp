<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Recruitment Drives - HireFlow Placement Portal" />
<c:set var="activeNav" value="drives" />
<c:set var="breadcrumbCurrent" value="Recruitment Drives" />
<%@ include file="/WEB-INF/views/common/header.jspf" %>

<div style="margin-bottom: 2rem;">
    <h1 style="font-size: 2rem; font-weight: 800; letter-spacing: -0.5px; margin-bottom: 0.5rem; color: var(--text-primary);">
        Campus Recruitment Directory
    </h1>
    <p style="color: var(--text-secondary); font-size: 1rem;">
        Explore current placement drives, job roles, academic eligibility criteria, and application deadlines across visiting corporate partners.
    </p>
</div>

<!-- Summary Metrics Bar -->
<div class="stats-grid">
    <div class="stat-card">
        <span class="stat-label">Total Published</span>
        <span class="stat-value"><c:out value="${totalDrives != null ? totalDrives : 0}" /></span>
        <span class="stat-desc">Drives in institutional directory</span>
    </div>
    <div class="stat-card">
        <span class="stat-label">Active Openings</span>
        <span class="stat-value" style="color: var(--success);"><c:out value="${activeDrives != null ? activeDrives : 0}" /></span>
        <span class="stat-desc">Open for candidate applications</span>
    </div>
    <div class="stat-card">
        <span class="stat-label">Corporate Partners</span>
        <span class="stat-value" style="color: var(--primary);"><c:out value="${totalCompanies != null ? totalCompanies : 0}" /></span>
        <span class="stat-desc">Verified recruiting organizations</span>
    </div>
</div>

<!-- Interactive Search, Filter & Sort Form -->
<div class="card" style="margin-bottom: 2rem;">
    <div class="card-body" style="padding: 1.25rem 1.5rem;">
        <form method="get" action="${pageContext.request.contextPath}/drives" class="filter-form">
            <div class="filter-grid">
                <!-- Keyword Search -->
                <div class="form-group">
                    <label for="searchQuery" class="form-label">Search Keyword</label>
                    <input type="text" id="searchQuery" name="q" class="form-control"
                           placeholder="Search by job role, company, or ID..."
                           value="<c:out value='${paramQuery}' />" maxlength="80">
                </div>

                <!-- Departmental Branch Filter -->
                <div class="form-group">
                    <label for="filterBranch" class="form-label">Eligible Branch</label>
                    <select id="filterBranch" name="branch" class="form-select">
                        <option value="ALL" ${paramBranch == 'ALL' ? 'selected' : ''}>All Branches</option>
                        <c:forEach var="br" items="${availableBranches}">
                            <option value="<c:out value='${br}'/>" ${paramBranch == br ? 'selected' : ''}>
                                <c:out value="${br}"/>
                            </option>
                        </c:forEach>
                    </select>
                </div>

                <!-- Status Filter -->
                <div class="form-group">
                    <label for="filterStatus" class="form-label">Drive Status</label>
                    <select id="filterStatus" name="status" class="form-select">
                        <option value="all" ${paramStatus == 'all' ? 'selected' : ''}>All Drives (Open &amp; Closed)</option>
                        <option value="active" ${paramStatus == 'active' ? 'selected' : ''}>Active / Open Only</option>
                        <option value="closed" ${paramStatus == 'closed' ? 'selected' : ''}>Closed Deadlines Only</option>
                    </select>
                </div>

                <!-- Sort Criteria -->
                <div class="form-group">
                    <label for="filterSort" class="form-label">Sort By</label>
                    <select id="filterSort" name="sort" class="form-select">
                        <option value="deadline-asc" ${paramSort == 'deadline-asc' ? 'selected' : ''}>Deadline (Earliest First)</option>
                        <option value="deadline-desc" ${paramSort == 'deadline-desc' ? 'selected' : ''}>Deadline (Latest First)</option>
                        <option value="company-asc" ${paramSort == 'company-asc' ? 'selected' : ''}>Company Name (A &rarr; Z)</option>
                        <option value="company-desc" ${paramSort == 'company-desc' ? 'selected' : ''}>Company Name (Z &rarr; A)</option>
                        <option value="cgpa-asc" ${paramSort == 'cgpa-asc' ? 'selected' : ''}>Min CGPA (Lowest First)</option>
                        <option value="cgpa-desc" ${paramSort == 'cgpa-desc' ? 'selected' : ''}>Min CGPA (Highest First)</option>
                    </select>
                </div>
            </div>

            <div class="filter-actions">
                <button type="submit" class="btn btn-primary btn-sm">
                    🔍 Apply Filters
                </button>
                <c:if test="${not empty paramQuery or (not empty paramBranch and paramBranch != 'ALL') or (not empty paramStatus and paramStatus != 'all') or (not empty paramSort and paramSort != 'deadline-asc')}">
                    <a href="${pageContext.request.contextPath}/drives" class="btn btn-outline btn-sm" style="color: var(--text-secondary); border-color: var(--border-light);">
                        ✕ Reset Filters
                    </a>
                </c:if>
                <span class="results-count-text">
                    Showing <strong><c:out value="${filteredCount}"/></strong> of <strong><c:out value="${totalDrives}"/></strong> drives
                </span>
            </div>
        </form>
    </div>
</div>

<!-- Drives Catalog Table Card -->
<div class="card">
    <div class="card-header">
        <div>
            <h2 class="card-title">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="color: var(--primary);">
                    <rect x="2" y="7" width="20" height="14" rx="2" ry="2"></rect>
                    <path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"></path>
                </svg>
                Placement Drives Directory
            </h2>
            <p class="card-subtitle">Public drives sorted by availability and deadline</p>
        </div>
    </div>
    <div class="card-body" style="padding: 0;">
        <c:choose>
            <c:when test="${empty drives}">
                <div class="empty-state">
                    <div class="empty-state-icon">📋</div>
                    <h3 class="empty-state-title">No Matching Recruitment Drives</h3>
                    <p class="empty-state-desc">
                        No recruitment drives matched your current filter criteria. Try adjusting your search query, selecting different academic branches, or clearing your filters.
                    </p>
                    <div style="margin-top: 1.5rem;">
                        <a href="${pageContext.request.contextPath}/drives" class="btn btn-primary btn-sm">
                            View All Drives
                        </a>
                    </div>
                </div>
            </c:when>
            <c:otherwise>
                <div class="table-responsive">
                    <table class="data-table">
                        <thead>
                            <tr>
                                <th>Company</th>
                                <th>Job Role &amp; ID</th>
                                <th>Min CGPA</th>
                                <th>Eligible Branches</th>
                                <th>Deadline</th>
                                <th>Status</th>
                            </tr>
                        </thead>
                        <tbody>
                            <c:forEach var="drive" items="${drives}">
                                <tr>
                                    <td>
                                        <div class="company-cell">
                                            <span class="company-name"><c:out value="${drive.companyName}" /></span>
                                            <span class="company-id"><c:out value="${drive.companyId}" /></span>
                                        </div>
                                    </td>
                                    <td>
                                        <div class="role-cell"><c:out value="${drive.jobTitle}" /></div>
                                        <div class="drive-id-tag"><c:out value="${drive.driveId}" /></div>
                                    </td>
                                    <td>
                                        <span class="cgpa-badge">
                                            <c:out value="${drive.minCgpa}" /> CGPA
                                        </span>
                                    </td>
                                    <td>
                                        <div class="branches-cell">
                                            <c:forEach var="branch" items="${drive.eligibleBranches}">
                                                <span class="branch-badge"><c:out value="${branch}" /></span>
                                            </c:forEach>
                                        </div>
                                    </td>
                                    <td>
                                        <div style="font-weight: 500; color: var(--text-primary);">
                                            <c:out value="${drive.applicationDeadline}" />
                                        </div>
                                        <c:if test="${drive.active}">
                                            <div style="font-size: 0.75rem; color: var(--success); font-weight: 600;">
                                                <c:choose>
                                                    <c:when test="${drive.daysRemaining == 0}">
                                                        Last Day Today!
                                                    </c:when>
                                                    <c:otherwise>
                                                        <c:out value="${drive.daysRemaining}" /> days remaining
                                                    </c:otherwise>
                                                </c:choose>
                                            </div>
                                        </c:if>
                                    </td>
                                    <td>
                                        <c:choose>
                                            <c:when test="${drive.active}">
                                                <span class="status-pill status-open">
                                                    <span class="dot"></span> Open
                                                </span>
                                            </c:when>
                                            <c:when test="${drive.closed}">
                                                <span class="status-pill status-closed">
                                                    <span class="dot"></span> Manually Closed
                                                </span>
                                            </c:when>
                                            <c:otherwise>
                                                <span class="status-pill status-closed">
                                                    <span class="dot"></span> Expired
                                                </span>
                                            </c:otherwise>
                                        </c:choose>
                                    </td>
                                </tr>
                            </c:forEach>
                        </tbody>
                    </table>
                </div>
            </c:otherwise>
        </c:choose>
    </div>
</div>

<%@ include file="/WEB-INF/views/common/footer.jspf" %>
