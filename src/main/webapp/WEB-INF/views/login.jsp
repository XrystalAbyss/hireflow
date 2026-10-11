<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Sign In - HireFlow Portal" />
<c:set var="breadcrumbCurrent" value="Sign In" />
<%@ include file="/WEB-INF/views/common/header.jspf" %>

<div style="max-width: 480px; margin: 3rem auto;">
    <div class="card" style="box-shadow: var(--shadow-lg);">
        <div class="card-header" style="text-align: center; padding: 2rem 1.5rem 1rem;">
            <div style="display: inline-flex; align-items: center; justify-content: center; width: 48px; height: 48px; background: rgba(56, 189, 248, 0.1); border-radius: 50%; color: var(--primary); margin-bottom: 0.75rem;">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
                    <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
                </svg>
            </div>
            <h1 style="font-size: 1.5rem; font-weight: 700; color: var(--text-primary); margin: 0 0 0.5rem;">
                Sign In to HireFlow
            </h1>
            <p style="font-size: 0.875rem; color: var(--text-muted); margin: 0;">
                Campus Recruitment & Placement Cell Portal
            </p>
        </div>

        <div class="card-body" style="padding: 1.5rem 2rem 2rem;">
            <%@ include file="/WEB-INF/views/common/flash.jspf" %>

            <form action="${pageContext.request.contextPath}/login" method="POST" id="loginForm" autocomplete="on">
                <!-- CSRF Protection Token -->
                <input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}'/>" />

                <!-- Safe Return URL Preservation -->
                <c:if test="${not empty returnUrl}">
                    <input type="hidden" name="returnUrl" value="<c:out value='${returnUrl}'/>" />
                </c:if>

                <div class="form-group">
                    <label for="username" class="form-label">Username</label>
                    <input type="text"
                           id="username"
                           name="username"
                           class="form-control"
                           placeholder="e.g. rahul.sharma or tpc.officer"
                           value="<c:out value='${submittedUsername}'/>"
                           required
                           autofocus
                           maxlength="50"
                           autocomplete="username" />
                </div>

                <div class="form-group" style="margin-top: 1.25rem;">
                    <label for="password" class="form-label">Password</label>
                    <input type="password"
                           id="password"
                           name="password"
                           class="form-control"
                           placeholder="Enter your account password"
                           required
                           maxlength="128"
                           autocomplete="current-password" />
                </div>

                <div style="margin-top: 1.75rem;">
                    <button type="submit" class="btn btn-primary" id="loginSubmitBtn" style="width: 100%; justify-content: center; padding: 0.75rem;">
                        Sign In &rarr;
                    </button>
                </div>
            </form>

            <div style="margin-top: 1.5rem; padding-top: 1.25rem; border-top: 1px solid var(--border-color); font-size: 0.75rem; color: var(--text-muted); text-align: center; line-height: 1.5;">
                <p style="margin: 0;">
                    🛡️ Protected by BCrypt password hashing, session rotation, and brute-force rate limiting.
                </p>
            </div>
        </div>
    </div>
</div>

<%@ include file="/WEB-INF/views/common/footer.jspf" %>
