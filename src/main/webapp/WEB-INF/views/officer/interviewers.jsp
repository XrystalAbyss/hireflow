<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Interviewers - HireFlow" />
<c:set var="activeNav" value="officer-interviewers" />
<c:set var="breadcrumbCurrent" value="Corporate Interviewers" />
<%@ include file="/WEB-INF/views/common/header.jspf" %>

<div style="max-width: 1200px; margin: 2rem auto; padding: 0 1rem;">
    <!-- Page Header -->
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 2rem; flex-wrap: wrap; gap: 1rem;">
        <div>
            <div style="display: flex; align-items: center; gap: 0.5rem; margin-bottom: 0.5rem;">
                <a href="${pageContext.request.contextPath}/officer/dashboard" style="color: var(--text-muted); text-decoration: none; font-size: 0.85rem;">
                    &larr; Officer Console
                </a>
                <span style="color: var(--text-muted); font-size: 0.85rem;">/</span>
                <span style="font-size: 0.85rem; font-weight: 700; color: var(--primary); text-transform: uppercase;">
                    Interview Resources
                </span>
            </div>
            <h1 style="font-size: 2rem; font-weight: 800; color: var(--text-primary); margin: 0 0 0.25rem;">
                Corporate Interviewers
            </h1>
            <p style="color: var(--text-muted); margin: 0; font-size: 0.95rem;">
                Manage corporate evaluation personnel assigned to campus recruitment drives.
            </p>
        </div>
        <div style="display: flex; gap: 0.75rem; align-items: center;">
            <a href="${pageContext.request.contextPath}/officer/interviewers/new" class="btn btn-primary" id="btnNewInterviewer">
                + Register Interviewer
            </a>
            <a href="${pageContext.request.contextPath}/officer/scheduling" class="btn btn-outline" id="btnGoToScheduling">
                🗓️ Go to Scheduling
            </a>
        </div>
    </div>

    <!-- Interviewers Catalog Card -->
    <div class="card">
        <div class="card-body" style="padding: 1.5rem;">
            <c:choose>
                <c:when test="${not empty items}">
                    <div style="overflow-x: auto;">
                        <table style="width: 100%; border-collapse: collapse; font-size: 0.9rem;" id="interviewersTable">
                            <thead>
                                <tr style="border-bottom: 2px solid var(--border-color); text-align: left;">
                                    <th style="padding: 0.75rem 1rem;">Interviewer ID</th>
                                    <th style="padding: 0.75rem 1rem;">Interviewer Name</th>
                                    <th style="padding: 0.75rem 1rem;">Employer Company</th>
                                    <th style="padding: 0.75rem 1rem;">Confirmed Sessions</th>
                                    <th style="padding: 0.75rem 1rem; text-align: right;">Actions</th>
                                </tr>
                            </thead>
                            <tbody>
                                <c:forEach var="item" items="${items}">
                                    <tr style="border-bottom: 1px solid var(--border-color);" id="interviewerRow-${item.interviewer.id}">
                                        <td style="padding: 0.75rem 1rem; font-weight: 700; font-family: monospace; color: var(--primary);">
                                            <c:out value="${item.interviewer.id}"/>
                                        </td>
                                        <td style="padding: 0.75rem 1rem; font-weight: 600; color: var(--text-primary);">
                                            <c:out value="${item.interviewer.name}"/>
                                        </td>
                                        <td style="padding: 0.75rem 1rem; color: var(--text-secondary);">
                                            🏢 <c:out value="${item.companyName}"/>
                                            <span style="font-size: 0.75rem; color: var(--text-muted); font-family: monospace;">(<c:out value="${item.interviewer.companyId}"/>)</span>
                                        </td>
                                        <td style="padding: 0.75rem 1rem;">
                                            <c:choose>
                                                <c:when test="${item.confirmedSessionCount > 0}">
                                                    <span class="badge badge-active">
                                                        <c:out value="${item.confirmedSessionCount}"/> confirmed
                                                    </span>
                                                </c:when>
                                                <c:otherwise>
                                                    <span class="badge badge-outline" style="border: 1px solid var(--border-color); color: var(--text-muted);">
                                                        0 active
                                                    </span>
                                                </c:otherwise>
                                            </c:choose>
                                        </td>
                                        <td style="padding: 0.75rem 1rem; text-align: right; white-space: nowrap;">
                                            <a href="${pageContext.request.contextPath}/officer/interviewers/${item.interviewer.id}/edit"
                                               class="btn btn-outline" style="font-size: 0.8rem; padding: 0.35rem 0.65rem;"
                                               id="editInterviewerBtn-${item.interviewer.id}">
                                                Edit
                                            </a>
                                            <c:choose>
                                                <c:when test="${item.confirmedSessionCount == 0}">
                                                    <form action="${pageContext.request.contextPath}/officer/interviewers/${item.interviewer.id}/delete"
                                                          method="POST" style="display: inline;"
                                                          onsubmit="return confirm('Are you sure you want to delete interviewer ${item.interviewer.id}?');">
                                                        <input type="hidden" name="_csrf" value="<c:out value='${csrfToken}'/>" />
                                                        <button type="submit" class="btn btn-outline"
                                                                style="font-size: 0.8rem; padding: 0.35rem 0.65rem; color: var(--danger); border-color: rgba(239, 68, 68, 0.4);"
                                                                id="delInterviewerBtn-${item.interviewer.id}">
                                                            Delete
                                                        </button>
                                                    </form>
                                                </c:when>
                                                <c:otherwise>
                                                    <button type="button" class="btn btn-outline" disabled
                                                            title="Cannot delete interviewer referenced by confirmed sessions."
                                                            style="font-size: 0.8rem; padding: 0.35rem 0.65rem; opacity: 0.5; cursor: not-allowed;">
                                                        Locked
                                                    </button>
                                                </c:otherwise>
                                            </c:choose>
                                        </td>
                                    </tr>
                                </c:forEach>
                            </tbody>
                        </table>
                    </div>
                </c:when>
                <c:otherwise>
                    <div style="text-align: center; padding: 3rem 1rem; color: var(--text-muted);">
                        <div style="font-size: 2.5rem; margin-bottom: 1rem;">🧑‍💼</div>
                        <h3 style="font-size: 1.15rem; font-weight: 700; color: var(--text-primary); margin-bottom: 0.5rem;">
                            No Interviewers Registered
                        </h3>
                        <p style="font-size: 0.9rem; margin-bottom: 1.5rem;">
                            Register corporate interviewers to configure evaluators for campus recruitment drives.
                        </p>
                        <a href="${pageContext.request.contextPath}/officer/interviewers/new" class="btn btn-primary">
                            + Register First Interviewer
                        </a>
                    </div>
                </c:otherwise>
            </c:choose>
        </div>
    </div>
</div>

<%@ include file="/WEB-INF/views/common/footer.jspf" %>
