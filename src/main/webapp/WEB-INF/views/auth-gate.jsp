<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Access Restricted - Authentication Gate - HireFlow" />
<c:set var="breadcrumbCurrent" value="Access Restricted" />
<%@ include file="/WEB-INF/views/common/header.jspf" %>

<div style="max-width: 680px; margin: 3rem auto;">
    <div class="card" style="border-top: 4px solid var(--danger); box-shadow: var(--shadow-lg);">
        <div class="card-body" style="padding: 2.5rem; text-align: center;">
            <div style="font-size: 3.5rem; margin-bottom: 1rem;">🔒</div>
            <h1 style="font-size: 1.75rem; font-weight: 800; color: var(--text-primary); margin-bottom: 0.75rem;">
                Protected Portal Boundary (403 Forbidden)
            </h1>
            <p style="color: var(--danger); font-weight: 600; font-size: 0.95rem; margin-bottom: 1.25rem;">
                <c:out value="${authReason != null ? authReason : 'Authentication is required to access protected portal modules.'}" />
            </p>

            <div style="background-color: #f8fafc; border: 1px solid var(--border-light); border-radius: var(--radius-sm); padding: 1.25rem; text-align: left; font-size: 0.85rem; color: var(--text-secondary); line-height: 1.6; margin-bottom: 2rem;">
                <p style="margin-bottom: 0.75rem;">
                    <strong>Requested Resource:</strong> <code style="color: var(--primary); font-weight: 600;"><c:out value="${targetUri != null ? targetUri : '/student/* or /officer/*'}" /></code>
                </p>
                <p style="margin-bottom: 0.75rem;">
                    <strong>Stage 3B Security Safeguard:</strong> The candidate self-service portal (<code>/student/*</code>) and placement officer console (<code>/officer/*</code>) enforce a strict <strong>deny-by-default</strong> authorization boundary.
                </p>
                <p style="margin-bottom: 0.75rem;">
                    Client-supplied query parameters (such as <code>?role=...</code> or <code>?studentId=...</code>) are strictly untrusted and cannot bypass authentication gates.
                </p>
                <p>
                    Please sign in with verified candidate student or placement officer credentials to access your protected workspace.
                </p>
            </div>

            <div style="display: flex; gap: 1rem; justify-content: center; flex-wrap: wrap;">
                <a href="${pageContext.request.contextPath}/login?returnUrl=<c:out value='${targetUri}'/>" class="btn btn-primary" id="gateLoginBtn">
                    Sign In to Proceed &rarr;
                </a>
                <a href="${pageContext.request.contextPath}/drives" class="btn btn-outline" style="color: var(--text-primary); border-color: var(--border-light);">
                    &larr; View Public Recruitment Drives
                </a>
                <a href="${pageContext.request.contextPath}/" class="btn btn-outline" style="color: var(--text-primary); border-color: var(--border-light);">
                    Return to Home
                </a>
            </div>
        </div>
    </div>
</div>

<%@ include file="/WEB-INF/views/common/footer.jspf" %>
