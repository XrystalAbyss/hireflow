<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="${isEdit ? 'Edit Interviewer' : 'Register Interviewer'} - HireFlow" />
<c:set var="activeNav" value="officer-interviewers" />
<c:set var="breadcrumbCurrent" value="${isEdit ? 'Edit Interviewer' : 'Register Interviewer'}" />
<%@ include file="/WEB-INF/views/common/header.jspf" %>

<div style="max-width: 700px; margin: 2rem auto; padding: 0 1rem;">
    <!-- Navigation Back Link -->
    <div style="margin-bottom: 1.5rem;">
        <a href="${pageContext.request.contextPath}/officer/interviewers" style="color: var(--text-muted); text-decoration: none; font-size: 0.9rem;">
            &larr; Back to Interviewers List
        </a>
    </div>

    <!-- Form Card -->
    <div class="card">
        <div class="card-body" style="padding: 2rem;">
            <div style="margin-bottom: 1.5rem;">
                <span style="font-size: 0.8rem; font-weight: 700; color: var(--primary); text-transform: uppercase;">
                    Resource Management
                </span>
                <h1 style="font-size: 1.75rem; font-weight: 800; color: var(--text-primary); margin: 0.25rem 0 0.5rem;">
                    <c:out value="${isEdit ? 'Edit Corporate Interviewer' : 'Register New Interviewer'}"/>
                </h1>
                <p style="color: var(--text-muted); font-size: 0.9rem; margin: 0;">
                    Configure evaluation personnel for campus interview scheduling.
                </p>
            </div>

            <form action="${pageContext.request.contextPath}/officer/interviewers<c:if test='${isEdit}'>/${interviewer.id}/update</c:if>"
                  method="POST" id="interviewerForm">
                <input type="hidden" name="_csrf" value="<c:out value='${csrfToken}'/>" />

                <!-- Interviewer ID Field -->
                <div style="margin-bottom: 1.25rem;">
                    <label for="interviewerId" style="display: block; font-size: 0.875rem; font-weight: 600; margin-bottom: 0.4rem; color: var(--text-primary);">
                        Interviewer ID <span style="color: var(--danger);">*</span>
                    </label>
                    <input type="text" id="interviewerId" name="id"
                           value="<c:out value='${isEdit ? interviewer.id : param.id}'/>"
                           <c:if test="${isEdit}">readonly style="background-color: var(--bg-secondary); cursor: not-allowed;"</c:if>
                           placeholder="e.g. INT-GOOG-01" required maxlength="32"
                           class="form-control" style="width: 100%; padding: 0.65rem 0.85rem; border: 1px solid var(--border-color); border-radius: 6px; font-family: monospace;" />
                    <small style="color: var(--text-muted); font-size: 0.75rem; display: block; margin-top: 0.25rem;">
                        Unique identifier (alphanumeric uppercase, max 32 chars).
                    </small>
                </div>

                <!-- Interviewer Name Field -->
                <div style="margin-bottom: 1.25rem;">
                    <label for="interviewerName" style="display: block; font-size: 0.875rem; font-weight: 600; margin-bottom: 0.4rem; color: var(--text-primary);">
                        Full Name <span style="color: var(--danger);">*</span>
                    </label>
                    <input type="text" id="interviewerName" name="name"
                           value="<c:out value='${isEdit ? interviewer.name : param.name}'/>"
                           placeholder="e.g. Dr. Jane Smith" required maxlength="150"
                           class="form-control" style="width: 100%; padding: 0.65rem 0.85rem; border: 1px solid var(--border-color); border-radius: 6px;" />
                </div>

                <!-- Employer Company Dropdown -->
                <div style="margin-bottom: 2rem;">
                    <label for="companyId" style="display: block; font-size: 0.875rem; font-weight: 600; margin-bottom: 0.4rem; color: var(--text-primary);">
                        Corporate Partner <span style="color: var(--danger);">*</span>
                    </label>
                    <select id="companyId" name="companyId" required class="form-control"
                            style="width: 100%; padding: 0.65rem 0.85rem; border: 1px solid var(--border-color); border-radius: 6px; background-color: var(--bg-surface); color: var(--text-primary);">
                        <option value="">-- Select Corporate Partner --</option>
                        <c:forEach var="company" items="${companies}">
                            <option value="<c:out value='${company.id}'/>"
                                    <c:if test="${(isEdit && interviewer.companyId == company.id) || (not empty param.companyId && param.companyId == company.id)}">selected</c:if>>
                                <c:out value="${company.name}"/> (<c:out value="${company.id}"/>)
                            </option>
                        </c:forEach>
                    </select>
                    <small style="color: var(--text-muted); font-size: 0.75rem; display: block; margin-top: 0.25rem;">
                        Interviewer will be matched with recruitment drives published by this company.
                    </small>
                </div>

                <!-- Action Buttons -->
                <div style="display: flex; gap: 1rem; justify-content: flex-end;">
                    <a href="${pageContext.request.contextPath}/officer/interviewers" class="btn btn-outline" id="btnCancel">
                        Cancel
                    </a>
                    <button type="submit" class="btn btn-primary" id="btnSubmitInterviewer">
                        <c:out value="${isEdit ? 'Save Changes' : 'Register Interviewer'}"/>
                    </button>
                </div>
            </form>
        </div>
    </div>
</div>

<%@ include file="/WEB-INF/views/common/footer.jspf" %>
