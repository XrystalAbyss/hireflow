<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Candidate Workspace - HireFlow" />
<c:set var="breadcrumbCurrent" value="Candidate Dashboard" />
<%@ include file="/WEB-INF/views/common/header.jspf" %>

<div style="max-width: 1100px; margin: 2rem auto; padding: 0 1rem;">
    <!-- Welcome Header Card -->
    <div class="card" style="margin-bottom: 2rem;">
        <div class="card-body" style="padding: 2rem; display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 1.5rem;">
            <div>
                <div style="display: flex; align-items: center; gap: 0.75rem; margin-bottom: 0.5rem;">
                    <span class="badge badge-role">STUDENT CANDIDATE</span>
                    <span class="badge badge-cutoff">ROLL: <c:out value="${user.studentId}" /></span>
                    <c:if test="${student != null && student.active}">
                        <span class="badge badge-active">ELIGIBLE FOR RECRUITMENT</span>
                    </c:if>
                </div>
                <h1 style="font-size: 1.85rem; font-weight: 800; color: var(--text-primary); margin: 0 0 0.25rem;">
                    Welcome, <c:out value="${user.displayName}" />
                </h1>
                <p style="color: var(--text-muted); margin: 0; font-size: 0.875rem;">
                    Username: <code><c:out value="${user.name}" /></code> |
                    <c:choose>
                        <c:when test="${student != null}">
                            Department: <strong><c:out value="${student.branch}" /></strong> |
                            CGPA: <strong><c:out value="${student.cgpa}" /></strong>
                        </c:when>
                        <c:otherwise>
                            Academic record linked
                        </c:otherwise>
                    </c:choose>
                </p>
            </div>
            <div>
                <form action="${pageContext.request.contextPath}/logout" method="POST" id="studentLogoutForm" style="margin: 0;">
                    <input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}'/>" />
                    <button type="submit" class="btn btn-outline" id="studentLogoutBtn" style="color: var(--danger); border-color: rgba(239, 68, 68, 0.4);">
                        Sign Out
                    </button>
                </form>
            </div>
        </div>
    </div>

    <!-- Portal Feature Cards Grid -->
    <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: 1.5rem; margin-bottom: 2.5rem;">
        <!-- Card 1: Eligible Drives -->
        <div class="card" style="display: flex; flex-direction: column; justify-content: space-between;">
            <div class="card-body" style="padding: 1.75rem;">
                <div style="display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 1rem;">
                    <div style="font-size: 2rem;">💼</div>
                    <span class="badge badge-role"><c:out value="${activeDrivesCount}"/> Active Drives</span>
                </div>
                <h3 style="font-size: 1.2rem; font-weight: 700; color: var(--text-primary); margin: 0 0 0.5rem;">
                    Placement Drives &amp; Eligibility
                </h3>
                <p style="color: var(--text-secondary); font-size: 0.875rem; line-height: 1.6; margin-bottom: 1.5rem;">
                    Examine current corporate drives with real-time academic eligibility evaluation. Apply with one click to drives matching your departmental cutoffs.
                </p>
            </div>
            <div style="padding: 0 1.75rem 1.75rem;">
                <a href="${pageContext.request.contextPath}/student/drives" class="btn btn-primary" style="width: 100%; text-align: center;">
                    Browse Eligible Drives &rarr;
                </a>
            </div>
        </div>

        <!-- Card 2: Applications Tracker -->
        <div class="card" style="display: flex; flex-direction: column; justify-content: space-between;">
            <div class="card-body" style="padding: 1.75rem;">
                <div style="display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 1rem;">
                    <div style="font-size: 2rem;">📝</div>
                    <span class="badge badge-cutoff"><c:out value="${submittedApplicationsCount}"/> Submitted</span>
                </div>
                <h3 style="font-size: 1.2rem; font-weight: 700; color: var(--text-primary); margin: 0 0 0.5rem;">
                    Application Tracker
                </h3>
                <p style="color: var(--text-secondary); font-size: 0.875rem; line-height: 1.6; margin-bottom: 1.5rem;">
                    Track the status of your submitted applications through departmental review, shortlisting, and technical interview scheduling stages.
                </p>
            </div>
            <div style="padding: 0 1.75rem 1.75rem;">
                <a href="${pageContext.request.contextPath}/student/applications" class="btn btn-outline" style="width: 100%; text-align: center;">
                    View My Applications &rarr;
                </a>
            </div>
        </div>

        <!-- Card 3: Academic Profile -->
        <div class="card" style="display: flex; flex-direction: column; justify-content: space-between;">
            <div class="card-body" style="padding: 1.75rem;">
                <div style="display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 1rem;">
                    <div style="font-size: 2rem;">🎓</div>
                    <span class="badge badge-active">Verified Record</span>
                </div>
                <h3 style="font-size: 1.2rem; font-weight: 700; color: var(--text-primary); margin: 0 0 0.5rem;">
                    Academic Profile
                </h3>
                <p style="color: var(--text-secondary); font-size: 0.875rem; line-height: 1.6; margin-bottom: 1.5rem;">
                    Review your official enrollment record, CGPA, departmental branch affiliation, and institutional contact information.
                </p>
            </div>
            <div style="padding: 0 1.75rem 1.75rem;">
                <a href="${pageContext.request.contextPath}/student/profile" class="btn btn-outline" style="width: 100%; text-align: center;">
                    View Profile Details &rarr;
                </a>
            </div>
        </div>

        <!-- Card 4: Confirmed Interviews -->
        <div class="card" style="display: flex; flex-direction: column; justify-content: space-between;">
            <div class="card-body" style="padding: 1.75rem;">
                <div style="display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 1rem;">
                    <div style="font-size: 2rem;">🗓️</div>
                    <span class="badge ${confirmedInterviewsCount > 0 ? 'badge-eligible' : 'badge-role'}">
                        <c:out value="${confirmedInterviewsCount}"/> Scheduled
                    </span>
                </div>
                <h3 style="font-size: 1.2rem; font-weight: 700; color: var(--text-primary); margin: 0 0 0.5rem;">
                    Interview Schedule
                </h3>
                <p style="color: var(--text-secondary); font-size: 0.875rem; line-height: 1.6; margin-bottom: 1.5rem;">
                    View your confirmed technical and HR interview appointments, scheduled time windows, and assigned interview rooms.
                </p>
            </div>
            <div style="padding: 0 1.75rem 1.75rem;">
                <a href="${pageContext.request.contextPath}/student/interviews" class="btn btn-primary" style="width: 100%; text-align: center;" id="linkMyInterviews">
                    View Interview Schedule &rarr;
                </a>
            </div>
        </div>
    </div>

    <!-- Stage 3D Milestone Status -->
    <div class="card" style="border-left: 4px solid var(--success);">
        <div class="card-body" style="padding: 1.5rem 1.75rem;">
            <h4 style="font-size: 1rem; font-weight: 700; color: var(--text-primary); margin: 0 0 0.5rem;">
                ✅ Stage 3D Student Self-Service Portal Active
            </h4>
            <p style="color: var(--text-secondary); font-size: 0.875rem; line-height: 1.6; margin: 0;">
                All student self-service features are operational with server-side identity derivation, real-time eligibility checks, and CSRF-protected applications.
            </p>
        </div>
    </div>
</div>

<%@ include file="/WEB-INF/views/common/footer.jspf" %>
