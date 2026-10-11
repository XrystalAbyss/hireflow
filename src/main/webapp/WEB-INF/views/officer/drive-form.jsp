<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="isEdit" value="${formMode == 'edit'}" />
<c:set var="pageTitle" value="${isEdit ? 'Edit Recruitment Drive - HireFlow' : 'Publish Recruitment Drive - HireFlow'}" />
<c:set var="activeNav" value="officer-drives" />
<c:set var="breadcrumbCurrent" value="${isEdit ? 'Edit Drive' : 'New Drive'}" />
<%@ include file="/WEB-INF/views/common/header.jspf" %>

<div style="max-width: 750px; margin: 2rem auto; padding: 0 1rem;">
    <div class="card">
        <div class="card-body" style="padding: 2.5rem;">
            <div style="margin-bottom: 2rem;">
                <a href="${pageContext.request.contextPath}/officer/drives" style="color: var(--primary); font-size: 0.875rem; text-decoration: none; font-weight: 600;">
                    &larr; Back to Recruitment Drives
                </a>
                <h1 style="font-size: 1.75rem; font-weight: 800; color: var(--text-primary); margin: 0.75rem 0 0.25rem;">
                    <c:out value="${isEdit ? 'Edit Recruitment Drive' : 'Publish Recruitment Drive'}"/>
                </h1>
                <p style="color: var(--text-muted); margin: 0; font-size: 0.875rem;">
                    <c:out value="${isEdit ? 'Modify drive requirements, eligible branches, or update registration deadline.' : 'Configure a new campus recruitment drive with academic cutoffs and branch constraints.'}"/>
                </p>
            </div>

            <form action="${pageContext.request.contextPath}/officer/drives${isEdit ? '/'.concat(drive.id).concat('/update') : ''}"
                  method="POST" id="driveForm">
                <input type="hidden" name="_csrf" value="<c:out value='${csrfToken}'/>" />

                <div class="form-group" style="margin-bottom: 1.5rem;">
                    <label for="driveId" class="form-label" style="font-weight: 600; display: block; margin-bottom: 0.5rem;">
                        Recruitment Drive ID <span style="color: var(--danger);">*</span>
                    </label>
                    <c:choose>
                        <c:when test="${isEdit}">
                            <input type="text" id="driveId" name="id" class="form-control"
                                   value="<c:out value='${drive.id}'/>" readonly
                                   style="background-color: var(--bg-hover); cursor: not-allowed; font-weight: 600;" />
                            <small style="color: var(--text-muted); font-size: 0.75rem;">
                                Primary drive identifier cannot be modified once published.
                            </small>
                        </c:when>
                        <c:otherwise>
                            <input type="text" id="driveId" name="id" class="form-control"
                                   placeholder="e.g. DRV-001 or GOOGLE-SWE-2026" required autofocus
                                   style="text-transform: uppercase;" />
                            <small style="color: var(--text-muted); font-size: 0.75rem;">
                                Unique drive reference identifier.
                            </small>
                        </c:otherwise>
                    </c:choose>
                </div>

                <div class="form-group" style="margin-bottom: 1.5rem;">
                    <label for="companyId" class="form-label" style="font-weight: 600; display: block; margin-bottom: 0.5rem;">
                        Visiting Employer <span style="color: var(--danger);">*</span>
                    </label>
                    <select id="companyId" name="companyId" class="form-control" required>
                        <option value="">-- Select Corporate Partner --</option>
                        <c:forEach var="c" items="${companies}">
                            <option value="${c.id}" ${isEdit and drive.companyId == c.id ? 'selected' : ''}>
                                <c:out value="${c.name}"/> (<c:out value="${c.id}"/>)
                            </option>
                        </c:forEach>
                    </select>
                </div>

                <div class="form-group" style="margin-bottom: 1.5rem;">
                    <label for="jobTitle" class="form-label" style="font-weight: 600; display: block; margin-bottom: 0.5rem;">
                        Job Role / Designation <span style="color: var(--danger);">*</span>
                    </label>
                    <input type="text" id="jobTitle" name="jobTitle" class="form-control"
                           value="<c:out value='${isEdit ? drive.jobTitle : ""}'/>"
                           placeholder="e.g. Associate Software Engineer, Cloud Analyst" required maxlength="150" />
                </div>

                <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 1.5rem; margin-bottom: 1.5rem;">
                    <div class="form-group">
                        <label for="minimumCgpa" class="form-label" style="font-weight: 600; display: block; margin-bottom: 0.5rem;">
                            Minimum CGPA Cutoff <span style="color: var(--danger);">*</span>
                        </label>
                        <input type="number" step="0.01" min="0.00" max="10.00" id="minimumCgpa" name="minimumCgpa"
                               class="form-control" value="<c:out value='${isEdit ? drive.minimumCgpa : "7.00"}'/>" required />
                        <small style="color: var(--text-muted); font-size: 0.75rem;">
                            Academic cutoff between 0.00 and 10.00.
                        </small>
                    </div>

                    <div class="form-group">
                        <label for="applicationDeadline" class="form-label" style="font-weight: 600; display: block; margin-bottom: 0.5rem;">
                            Application Deadline <span style="color: var(--danger);">*</span>
                        </label>
                        <input type="date" id="applicationDeadline" name="applicationDeadline" class="form-control"
                               value="<c:out value='${isEdit ? drive.applicationDeadline : ""}'/>" required />
                        <small style="color: var(--text-muted); font-size: 0.75rem;">
                            Registration cutoff date (YYYY-MM-DD).
                        </small>
                    </div>
                </div>

                <div class="form-group" style="margin-bottom: 2rem;">
                    <label for="eligibleBranches" class="form-label" style="font-weight: 600; display: block; margin-bottom: 0.5rem;">
                        Eligible Academic Branches <span style="color: var(--danger);">*</span>
                    </label>
                    <c:set var="branchesString" value="" />
                    <c:if test="${isEdit and not empty drive.eligibleBranches}">
                        <c:forEach var="b" items="${drive.eligibleBranches}" varStatus="loop">
                            <c:set var="branchesString" value="${branchesString}${b}${loop.last ? '' : ', '}" />
                        </c:forEach>
                    </c:if>
                    <input type="text" id="eligibleBranches" name="eligibleBranches" class="form-control"
                           value="<c:out value='${branchesString}'/>"
                           placeholder="e.g. CSE, IT, ECE" required />
                    <small style="color: var(--text-muted); font-size: 0.75rem;">
                        Comma-separated academic branches permitted to submit applications.
                    </small>
                </div>

                <div style="display: flex; gap: 1rem; align-items: center; justify-content: flex-end;">
                    <a href="${pageContext.request.contextPath}/officer/drives" class="btn btn-outline">
                        Cancel
                    </a>
                    <button type="submit" class="btn btn-primary" id="btnSubmitDrive">
                        <c:out value="${isEdit ? 'Save Changes' : 'Publish Drive'}"/>
                    </button>
                </div>
            </form>
        </div>
    </div>
</div>

<%@ include file="/WEB-INF/views/common/footer.jspf" %>
