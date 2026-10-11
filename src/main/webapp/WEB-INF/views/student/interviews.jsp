<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="My Confirmed Interview Schedule - HireFlow" />
<c:set var="activeNav" value="student-interviews" />
<c:set var="breadcrumbCurrent" value="My Interviews" />
<%@ include file="/WEB-INF/views/common/header.jspf" %>

<div style="max-width: 1000px; margin: 2rem auto; padding: 0 1rem;">
    <!-- Page Header -->
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 2rem; flex-wrap: wrap; gap: 1rem;">
        <div>
            <span style="font-size: 0.8rem; font-weight: 700; color: var(--primary); text-transform: uppercase;">
                Candidate Portal
            </span>
            <h1 style="font-size: 1.85rem; font-weight: 800; color: var(--text-primary); margin: 0.25rem 0 0.5rem;">
                📅 My Confirmed Interview Schedule
            </h1>
            <p style="color: var(--text-muted); font-size: 0.9rem; margin: 0;">
                Confirmed campus interview appointments scheduled by the Training &amp; Placement Cell for
                <strong><c:out value="${student != null ? student.name : user.displayName}"/></strong> (<code><c:out value="${user.studentId}"/></code>).
            </p>
        </div>
        <div>
            <a href="${pageContext.request.contextPath}/student/applications" class="btn btn-outline" id="btnBackToApplications">
                &larr; View Applications
            </a>
        </div>
    </div>

    <!-- Confirmed Appointments List -->
    <c:choose>
        <c:when test="${not empty interviews}">
            <div style="display: flex; flex-direction: column; gap: 1.25rem;">
                <c:forEach var="item" items="${interviews}">
                    <div class="card" style="border-left: 6px solid var(--primary);" id="interviewCard-${item.sessionId}">
                        <div class="card-body" style="padding: 1.5rem 1.75rem;">
                            <div style="display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 1rem; margin-bottom: 1rem;">
                                <div>
                                    <div style="font-size: 0.8rem; font-weight: 700; color: var(--primary); text-transform: uppercase; margin-bottom: 0.25rem;">
                                        🏢 <c:out value="${item.companyName}"/>
                                    </div>
                                    <h2 style="font-size: 1.35rem; font-weight: 800; color: var(--text-primary); margin: 0;">
                                        <c:out value="${item.jobTitle}"/>
                                    </h2>
                                    <div style="font-size: 0.8rem; color: var(--text-muted); margin-top: 0.25rem;">
                                        Drive ID: <code><c:out value="${item.driveId}"/></code> • Application ID: <code><c:out value="${item.applicationId}"/></code>
                                    </div>
                                </div>
                                <span class="badge" style="background: rgba(16, 185, 129, 0.15); color: var(--success); font-weight: 700; font-size: 0.85rem; padding: 0.4rem 0.8rem;">
                                    ✔ CONFIRMED INTERVIEW
                                </span>
                            </div>

                            <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 1rem; padding: 1rem; background: var(--bg-surface); border-radius: 8px; border: 1px solid var(--border-color);">
                                <div>
                                    <div style="font-size: 0.75rem; font-weight: 600; color: var(--text-muted); text-transform: uppercase;">
                                        Interview Date
                                    </div>
                                    <div style="font-size: 1.05rem; font-weight: 700; color: var(--text-primary); margin-top: 0.25rem;">
                                        🗓️ <c:out value="${item.date}"/>
                                    </div>
                                </div>

                                <div>
                                    <div style="font-size: 0.75rem; font-weight: 600; color: var(--text-muted); text-transform: uppercase;">
                                        Allocated Time Window
                                    </div>
                                    <div style="font-size: 1.05rem; font-weight: 700; color: var(--primary); margin-top: 0.25rem;">
                                        🕒 <c:out value="${item.startTimeOnly}"/> – <c:out value="${item.endTimeOnly}"/>
                                    </div>
                                </div>

                                <div>
                                    <div style="font-size: 0.75rem; font-weight: 600; color: var(--text-muted); text-transform: uppercase;">
                                        Assigned Venue / Room
                                    </div>
                                    <div style="font-size: 1.05rem; font-weight: 700; color: var(--text-primary); margin-top: 0.25rem;">
                                        🚪 <c:out value="${item.roomName}"/>
                                    </div>
                                </div>

                                <div>
                                    <div style="font-size: 0.75rem; font-weight: 600; color: var(--text-muted); text-transform: uppercase;">
                                        Interview Panel
                                    </div>
                                    <div style="font-size: 1.05rem; font-weight: 600; color: var(--text-secondary); margin-top: 0.25rem;">
                                        🧑‍💼 <c:out value="${item.interviewerName}"/>
                                    </div>
                                </div>
                            </div>

                            <div style="margin-top: 1rem; font-size: 0.85rem; color: var(--text-muted); line-height: 1.4;">
                                💡 <em>Please arrive at the venue at least 10 minutes prior to your allocated slot with a hard copy of your resume and college ID card.</em>
                            </div>
                        </div>
                    </div>
                </c:forEach>
            </div>
        </c:when>
        <c:otherwise>
            <div class="card">
                <div class="card-body" style="text-align: center; padding: 4rem 1.5rem; color: var(--text-muted);" id="emptyInterviewsState">
                    <div style="font-size: 3rem; margin-bottom: 1rem;">🗓️</div>
                    <h2 style="font-size: 1.25rem; font-weight: 700; color: var(--text-primary); margin-bottom: 0.5rem;">
                        No Confirmed Interviews Yet
                    </h2>
                    <p style="font-size: 0.95rem; margin-bottom: 1.5rem; max-width: 480px; margin-left: auto; margin-right: auto;">
                        You do not currently have any confirmed interview appointments scheduled.
                        When your shortlisted applications are scheduled by the placement cell, your confirmed appointment slots and room venues will appear here.
                    </p>
                    <a href="${pageContext.request.contextPath}/student/applications" class="btn btn-primary" id="btnCheckApplications">
                        Check Application Status
                    </a>
                </div>
            </div>
        </c:otherwise>
    </c:choose>
</div>

<%@ include file="/WEB-INF/views/common/footer.jspf" %>
