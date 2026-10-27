<%@ page contentType="text/html;charset=UTF-8" language="java" isErrorPage="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Error - HireFlow Placement Portal" />
<c:set var="breadcrumbCurrent" value="System Notice" />
<%@ include file="/WEB-INF/views/common/header.jspf" %>

<c:set var="statusCode" value="${pageContext.errorData.statusCode != null && pageContext.errorData.statusCode != 0 ? pageContext.errorData.statusCode : (requestScope['jakarta.servlet.error.status_code'] != null ? requestScope['jakarta.servlet.error.status_code'] : 404)}" />

<div style="max-width: 640px; margin: 3rem auto; text-align: center;">
    <div class="card" style="box-shadow: var(--shadow-lg);">
        <div class="card-body" style="padding: 2.5rem;">
            <div style="font-size: 4rem; font-weight: 800; line-height: 1; margin-bottom: 1rem; color: ${statusCode == 403 ? 'var(--warning)' : (statusCode == 404 ? 'var(--primary)' : 'var(--danger)')};">
                <c:out value="${statusCode}" />
            </div>

            <h1 style="font-size: 1.75rem; font-weight: 700; margin-bottom: 0.75rem; color: var(--text-primary);">
                <c:choose>
                    <c:when test="${statusCode == 400}">Bad Request (Invalid Parameters)</c:when>
                    <c:when test="${statusCode == 403}">Access Denied (Forbidden)</c:when>
                    <c:when test="${statusCode == 404}">Placement Resource Not Found</c:when>
                    <c:when test="${statusCode == 500}">Internal Server Notice</c:when>
                    <c:otherwise>System Notification</c:otherwise>
                </c:choose>
            </h1>

            <p style="color: var(--text-secondary); margin-bottom: 2rem; line-height: 1.6; font-size: 0.95rem;">
                <c:choose>
                    <c:when test="${statusCode == 400}">
                        The parameters supplied with this request failed input validation checks. Please review your search and filter criteria.
                    </c:when>
                    <c:when test="${statusCode == 403}">
                        You do not have permission to access the requested protected resource without authenticated credentials.
                    </c:when>
                    <c:when test="${statusCode == 404}">
                        The requested page, recruitment drive, or placement resource does not exist in the institutional directory.
                    </c:when>
                    <c:when test="${statusCode == 500}">
                        An unexpected condition occurred while processing this request. Diagnostic telemetry has been safely recorded.
                    </c:when>
                    <c:otherwise>
                        The requested operation could not be completed at this time.
                    </c:otherwise>
                </c:choose>
            </p>

            <div style="display: flex; gap: 1rem; justify-content: center; flex-wrap: wrap;">
                <a href="${pageContext.request.contextPath}/drives" class="btn btn-primary">
                    &larr; View Recruitment Drives
                </a>
                <a href="${pageContext.request.contextPath}/" class="btn btn-outline" style="color: var(--text-primary); border-color: var(--border-light);">
                    Return to Home
                </a>
            </div>
        </div>
    </div>
</div>

<%@ include file="/WEB-INF/views/common/footer.jspf" %>
