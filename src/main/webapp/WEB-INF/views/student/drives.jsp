<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Placement Drives & Eligibility - HireFlow" />
<c:set var="breadcrumbCurrent" value="Eligible Drives" />
<%@ include file="/WEB-INF/views/common/header.jspf" %>

<div style="max-width: 1100px; margin: 2rem auto; padding: 0 1rem;">
    <!-- Context Banner Card -->
    <div class="card" style="margin-bottom: 2rem;">
        <div class="card-body" style="padding: 1.75rem; display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 1rem;">
            <div>
                <div style="display: flex; align-items: center; gap: 0.5rem; margin-bottom: 0.5rem;">
                    <span class="badge badge-role"><c:out value="${student.branch}" /> Department</span>
                    <span class="badge badge-cutoff">CGPA: <c:out value="${student.cgpa}" /></span>
                    <span class="badge badge-active">ID: <c:out value="${student.id}" /></span>
                </div>
                <h1 style="font-size: 1.75rem; font-weight: 800; color: var(--text-primary); margin: 0 0 0.25rem;">
                    Campus Recruitment Drives Directory
                </h1>
                <p style="color: var(--text-muted); margin: 0; font-size: 0.875rem;">
                    Real-time academic eligibility evaluated against your official profile.
                </p>
            </div>
            <div>
                <a href="${pageContext.request.contextPath}/student/applications" class="btn btn-outline" id="myApplicationsNavBtn">
                    My Applications &rarr;
                </a>
            </div>
        </div>
    </div>

    <!-- Drives List -->
    <c:choose>
        <c:when test="${empty driveItems}">
            <div class="card empty-state" style="padding: 3rem; text-align: center;">
                <div style="font-size: 3rem; margin-bottom: 1rem;">💼</div>
                <h3 style="font-size: 1.25rem; font-weight: 700; color: var(--text-primary); margin-bottom: 0.5rem;">
                    No Recruitment Drives Scheduled
                </h3>
                <p style="color: var(--text-secondary); max-width: 480px; margin: 0 auto 1.5rem;">
                    There are currently no active placement drives published by the Training &amp; Placement Cell. Please check back later.
                </p>
                <a href="${pageContext.request.contextPath}/student/dashboard" class="btn btn-outline">
                    Return to Dashboard
                </a>
            </div>
        </c:when>
        <c:otherwise>
            <div style="display: flex; flex-direction: column; gap: 1.5rem;">
                <c:forEach var="item" items="${driveItems}">
                    <div class="card" id="drive-card-${item.drive().id}" style="transition: border-color 0.15s ease;">
                        <div class="card-body" style="padding: 1.75rem;">
                            <!-- Header Row: Company, Role, Badges -->
                            <div style="display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 1rem; margin-bottom: 1rem;">
                                <div>
                                    <div style="display: flex; align-items: center; gap: 0.5rem; margin-bottom: 0.25rem;">
                                        <span class="badge badge-cutoff"><c:out value="${item.drive().id}" /></span>
                                        <span style="font-size: 0.875rem; font-weight: 600; color: var(--text-muted);">
                                            <c:out value="${item.companyName()}" />
                                        </span>
                                    </div>
                                    <h2 style="font-size: 1.35rem; font-weight: 700; color: var(--text-primary); margin: 0;">
                                        <c:out value="${item.drive().jobTitle}" />
                                    </h2>
                                </div>
                                <div>
                                    <c:choose>
                                        <c:when test="${item.hasApplied()}">
                                            <span class="badge badge-active" style="font-size: 0.85rem; padding: 0.4rem 0.8rem;">
                                                ✓ ALREADY APPLIED
                                            </span>
                                        </c:when>
                                        <c:when test="${item.isClosed()}">
                                            <span class="badge badge-closed" style="font-size: 0.85rem; padding: 0.4rem 0.8rem;">
                                                MANUALLY CLOSED
                                            </span>
                                        </c:when>
                                        <c:when test="${item.isExpired()}">
                                            <span class="badge badge-closed" style="font-size: 0.85rem; padding: 0.4rem 0.8rem;">
                                                CLOSED (DEADLINE PASSED)
                                            </span>
                                        </c:when>
                                        <c:when test="${item.eligibilityResult().isEligible()}">
                                            <span class="badge badge-eligible" style="font-size: 0.85rem; padding: 0.4rem 0.8rem;">
                                                ★ ELIGIBLE TO APPLY
                                            </span>
                                        </c:when>
                                        <c:otherwise>
                                            <span class="badge badge-ineligible" style="font-size: 0.85rem; padding: 0.4rem 0.8rem;">
                                                NOT ELIGIBLE
                                            </span>
                                        </c:otherwise>
                                    </c:choose>
                                </div>
                            </div>

                            <!-- Criteria Metadata Grid -->
                            <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 1rem; padding: 1rem 0; border-top: 1px solid var(--border); border-bottom: 1px solid var(--border); margin-bottom: 1.25rem; font-size: 0.875rem;">
                                <div>
                                    <span style="color: var(--text-muted); display: block; font-size: 0.75rem; text-transform: uppercase;">Minimum CGPA</span>
                                    <strong style="color: var(--text-primary); font-size: 1rem;">
                                        <c:out value="${item.drive().minimumCgpa}" /> / 10.00
                                    </strong>
                                </div>
                                <div>
                                    <span style="color: var(--text-muted); display: block; font-size: 0.75rem; text-transform: uppercase;">Allowed Branches</span>
                                    <strong style="color: var(--text-primary);">
                                        <c:out value="${item.drive().eligibleBranches}" />
                                    </strong>
                                </div>
                                <div>
                                    <span style="color: var(--text-muted); display: block; font-size: 0.75rem; text-transform: uppercase;">Application Deadline</span>
                                    <strong style="color: ${item.isOpen() ? 'var(--text-primary)' : 'var(--danger)'};">
                                        <c:out value="${item.drive().applicationDeadline}" />
                                    </strong>
                                </div>
                            </div>

                            <!-- Eligibility Evaluation Message & Action Box -->
                            <div style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 1rem;">
                                <div style="flex: 1; min-width: 280px;">
                                    <c:choose>
                                        <c:when test="${item.hasApplied()}">
                                            <p style="margin: 0; font-size: 0.85rem; color: var(--success); font-weight: 500;">
                                                You applied on <c:out value="${item.existingApplication().appliedAt}" />. Status: <strong><c:out value="${item.existingApplication().status}" /></strong>.
                                            </p>
                                        </c:when>
                                        <c:when test="${item.isClosed()}">
                                            <p style="margin: 0; font-size: 0.85rem; color: var(--danger);">
                                                Recruitment drive has been manually closed by placement administration.
                                            </p>
                                        </c:when>
                                        <c:when test="${item.isExpired()}">
                                            <p style="margin: 0; font-size: 0.85rem; color: var(--text-muted);">
                                                Application registration closed on <c:out value="${item.drive().applicationDeadline}" />.
                                            </p>
                                        </c:when>
                                        <c:when test="${item.eligibilityResult().isEligible()}">
                                            <p style="margin: 0; font-size: 0.85rem; color: var(--success); font-weight: 500;">
                                                ✓ You satisfy all minimum CGPA cutoff and branch eligibility criteria.
                                            </p>
                                        </c:when>
                                        <c:otherwise>
                                            <ul style="margin: 0; padding-left: 1.25rem; font-size: 0.85rem; color: var(--danger);">
                                                <c:forEach var="reason" items="${item.eligibilityResult().reasons()}">
                                                    <li><c:out value="${reason}" /></li>
                                                </c:forEach>
                                            </ul>
                                        </c:otherwise>
                                    </c:choose>
                                </div>

                                <!-- Action Buttons -->
                                <div>
                                    <c:choose>
                                        <c:when test="${item.hasApplied()}">
                                            <a href="${pageContext.request.contextPath}/student/applications" class="btn btn-outline" style="font-size: 0.875rem;">
                                                View Application &rarr;
                                            </a>
                                        </c:when>
                                        <c:when test="${item.canApply()}">
                                            <form action="${pageContext.request.contextPath}/student/applications" method="POST" style="margin: 0;" id="apply-form-${item.drive().id}">
                                                <input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}'/>" />
                                                <input type="hidden" name="driveId" value="<c:out value='${item.drive().id}'/>" />
                                                <button type="submit" class="btn btn-primary" id="apply-btn-${item.drive().id}" style="font-size: 0.875rem;">
                                                    Apply for Drive &rarr;
                                                </button>
                                            </form>
                                        </c:when>
                                        <c:otherwise>
                                            <button type="button" class="btn btn-outline" disabled style="opacity: 0.5; cursor: not-allowed; font-size: 0.875rem;">
                                                Ineligible
                                            </button>
                                        </c:otherwise>
                                    </c:choose>
                                </div>
                            </div>
                        </div>
                    </div>
                </c:forEach>
            </div>
        </c:otherwise>
    </c:choose>
</div>

<%@ include file="/WEB-INF/views/common/footer.jspf" %>
