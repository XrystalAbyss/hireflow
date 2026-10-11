<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Drive Details: ${drive.id} - HireFlow" />
<c:set var="activeNav" value="officer-drives" />
<c:set var="breadcrumbCurrent" value="Drive Details" />
<%@ include file="/WEB-INF/views/common/header.jspf" %>

<div style="max-width: 900px; margin: 2rem auto; padding: 0 1rem;">
    <div style="margin-bottom: 1.5rem;">
        <a href="${pageContext.request.contextPath}/officer/drives" style="color: var(--primary); font-size: 0.875rem; text-decoration: none; font-weight: 600;">
            &larr; Back to Recruitment Drives Directory
        </a>
    </div>

    <!-- Drive Info Card -->
    <div class="card" style="margin-bottom: 2rem;">
        <div class="card-body" style="padding: 2.5rem;">
            <c:set var="isPassed" value="${drive.isDeadlinePassed(java.time.LocalDate.now())}" />
            <div style="display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 1rem; margin-bottom: 1.5rem;">
                <div>
                    <div style="display: flex; align-items: center; gap: 0.5rem; margin-bottom: 0.5rem;">
                        <span style="font-size: 0.85rem; font-weight: 700; color: var(--text-muted); text-transform: uppercase;">
                            DRIVE <c:out value="${drive.id}"/>
                        </span>
                        <c:choose>
                            <c:when test="${drive.closed}">
                                <span class="badge badge-closed">MANUALLY CLOSED</span>
                            </c:when>
                            <c:when test="${isPassed}">
                                <span class="badge badge-closed">WINDOW EXPIRED</span>
                            </c:when>
                            <c:otherwise>
                                <span class="badge badge-eligible">ACTIVE APPLICATION WINDOW</span>
                            </c:otherwise>
                        </c:choose>
                    </div>
                    <h1 style="font-size: 2rem; font-weight: 800; color: var(--text-primary); margin: 0 0 0.5rem;">
                        <c:out value="${drive.jobTitle}"/>
                    </h1>
                    <p style="font-size: 1.1rem; color: var(--primary); font-weight: 600; margin: 0;">
                        🏢 <c:out value="${company != null ? company.name : drive.companyId}"/> (<c:out value="${drive.companyId}"/>)
                    </p>
                </div>
                <div style="display: flex; gap: 0.5rem; align-items: center;">
                    <a href="${pageContext.request.contextPath}/officer/drives/${drive.id}/applications"
                       class="btn btn-primary" id="btnDriveApplicants">
                        View Applicants (<c:out value="${applicationCount}"/>)
                    </a>
                    <a href="${pageContext.request.contextPath}/officer/drives/${drive.id}/edit"
                       class="btn btn-outline" id="btnEditDrive">
                        Edit
                    </a>
                    <c:if test="${!drive.closed && !isPassed}">
                        <form action="${pageContext.request.contextPath}/officer/drives/${drive.id}/close"
                              method="POST" style="display: inline;"
                              onsubmit="return confirm('Are you sure you want to close this drive? No further student applications will be accepted.');">
                            <input type="hidden" name="_csrf" value="<c:out value='${csrfToken}'/>" />
                            <button type="submit" class="btn btn-outline" style="color: var(--danger); border-color: rgba(239, 68, 68, 0.4);" id="btnCloseDrive">
                                Close Drive
                            </button>
                        </form>
                    </c:if>
                </div>
            </div>

            <!-- Parameters Grid -->
            <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 1.5rem; padding-top: 1.5rem; border-top: 1px solid var(--border-color);">
                <div>
                    <div style="font-size: 0.8rem; font-weight: 600; color: var(--text-muted); text-transform: uppercase; margin-bottom: 0.25rem;">
                        Minimum CGPA Cutoff
                    </div>
                    <div style="font-size: 1.35rem; font-weight: 700; color: var(--text-primary);">
                        <c:out value="${drive.minimumCgpa}"/> / 10.00
                    </div>
                </div>

                <div>
                    <div style="font-size: 0.8rem; font-weight: 600; color: var(--text-muted); text-transform: uppercase; margin-bottom: 0.25rem;">
                        Application Deadline
                    </div>
                    <div style="font-size: 1.35rem; font-weight: 700; color: var(--text-primary);">
                        <c:out value="${drive.applicationDeadline}"/>
                    </div>
                </div>

                <div>
                    <div style="font-size: 0.8rem; font-weight: 600; color: var(--text-muted); text-transform: uppercase; margin-bottom: 0.25rem;">
                        Registered Applicants
                    </div>
                    <div style="font-size: 1.35rem; font-weight: 700; color: var(--primary);">
                        <c:out value="${applicationCount}"/> Candidates
                    </div>
                </div>

                <div>
                    <div style="font-size: 0.8rem; font-weight: 600; color: var(--text-muted); text-transform: uppercase; margin-bottom: 0.25rem;">
                        Employer Industry
                    </div>
                    <div style="font-size: 1.1rem; font-weight: 600; color: var(--text-secondary);">
                        <c:out value="${company != null ? company.industry : 'Corporate Partner'}"/>
                    </div>
                </div>
            </div>

            <!-- Eligible Branches -->
            <div style="margin-top: 2rem; padding-top: 1.5rem; border-top: 1px solid var(--border-color);">
                <div style="font-size: 0.85rem; font-weight: 700; color: var(--text-primary); margin-bottom: 0.75rem;">
                    Eligible Academic Branches:
                </div>
                <div style="display: flex; gap: 0.5rem; flex-wrap: wrap;">
                    <c:forEach var="branch" items="${drive.eligibleBranches}">
                        <span class="badge" style="background: rgba(37, 99, 235, 0.1); color: var(--primary); font-size: 0.85rem; padding: 0.4rem 0.75rem;">
                            <c:out value="${branch}"/>
                        </span>
                    </c:forEach>
                </div>
            </div>
        </div>
    </div>
</div>

<%@ include file="/WEB-INF/views/common/footer.jspf" %>
