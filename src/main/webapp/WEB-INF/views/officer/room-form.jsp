<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="${isEdit ? 'Edit Room' : 'Add Room'} - HireFlow" />
<c:set var="activeNav" value="officer-rooms" />
<c:set var="breadcrumbCurrent" value="${isEdit ? 'Edit Room' : 'Add Room'}" />
<%@ include file="/WEB-INF/views/common/header.jspf" %>

<div style="max-width: 650px; margin: 2rem auto; padding: 0 1rem;">
    <!-- Navigation Back Link -->
    <div style="margin-bottom: 1.5rem;">
        <a href="${pageContext.request.contextPath}/officer/rooms" style="color: var(--text-muted); text-decoration: none; font-size: 0.9rem;">
            &larr; Back to Rooms List
        </a>
    </div>

    <!-- Form Card -->
    <div class="card">
        <div class="card-body" style="padding: 2rem;">
            <div style="margin-bottom: 1.5rem;">
                <span style="font-size: 0.8rem; font-weight: 700; color: var(--primary); text-transform: uppercase;">
                    Venue Management
                </span>
                <h1 style="font-size: 1.75rem; font-weight: 800; color: var(--text-primary); margin: 0.25rem 0 0.5rem;">
                    <c:out value="${isEdit ? 'Edit Interview Room' : 'Add Interview Room'}"/>
                </h1>
                <p style="color: var(--text-muted); font-size: 0.9rem; margin: 0;">
                    Configure venue details and candidate seating capacity.
                </p>
            </div>

            <form action="${pageContext.request.contextPath}/officer/rooms<c:if test='${isEdit}'>/${room.id}/update</c:if>"
                  method="POST" id="roomForm">
                <input type="hidden" name="_csrf" value="<c:out value='${csrfToken}'/>" />

                <!-- Room ID Field -->
                <div style="margin-bottom: 1.25rem;">
                    <label for="roomId" style="display: block; font-size: 0.875rem; font-weight: 600; margin-bottom: 0.4rem; color: var(--text-primary);">
                        Room ID <span style="color: var(--danger);">*</span>
                    </label>
                    <input type="text" id="roomId" name="id"
                           value="<c:out value='${isEdit ? room.id : param.id}'/>"
                           <c:if test="${isEdit}">readonly style="background-color: var(--bg-secondary); cursor: not-allowed;"</c:if>
                           placeholder="e.g. RM-101" required maxlength="32"
                           class="form-control" style="width: 100%; padding: 0.65rem 0.85rem; border: 1px solid var(--border-color); border-radius: 6px; font-family: monospace;" />
                    <small style="color: var(--text-muted); font-size: 0.75rem; display: block; margin-top: 0.25rem;">
                        Unique identifier (alphanumeric uppercase, max 32 chars).
                    </small>
                </div>

                <!-- Room Name Field -->
                <div style="margin-bottom: 1.25rem;">
                    <label for="roomName" style="display: block; font-size: 0.875rem; font-weight: 600; margin-bottom: 0.4rem; color: var(--text-primary);">
                        Room / Venue Name <span style="color: var(--danger);">*</span>
                    </label>
                    <input type="text" id="roomName" name="name"
                           value="<c:out value='${isEdit ? room.name : param.name}'/>"
                           placeholder="e.g. Technical Interview Lab 1" required maxlength="150"
                           class="form-control" style="width: 100%; padding: 0.65rem 0.85rem; border: 1px solid var(--border-color); border-radius: 6px;" />
                </div>

                <!-- Room Capacity Field -->
                <div style="margin-bottom: 2rem;">
                    <label for="roomCapacity" style="display: block; font-size: 0.875rem; font-weight: 600; margin-bottom: 0.4rem; color: var(--text-primary);">
                        Seating Capacity <span style="color: var(--danger);">*</span>
                    </label>
                    <input type="number" id="roomCapacity" name="capacity" min="1" max="100"
                           value="<c:out value='${isEdit ? room.capacity : (not empty param.capacity ? param.capacity : 1)}'/>"
                           required class="form-control"
                           style="width: 100%; padding: 0.65rem 0.85rem; border: 1px solid var(--border-color); border-radius: 6px;" />
                    <small style="color: var(--text-muted); font-size: 0.75rem; display: block; margin-top: 0.25rem;">
                        Must be at least 1 (individual 1:1 or panel interview capacity).
                    </small>
                </div>

                <!-- Action Buttons -->
                <div style="display: flex; gap: 1rem; justify-content: flex-end;">
                    <a href="${pageContext.request.contextPath}/officer/rooms" class="btn btn-outline" id="btnCancel">
                        Cancel
                    </a>
                    <button type="submit" class="btn btn-primary" id="btnSubmitRoom">
                        <c:out value="${isEdit ? 'Save Changes' : 'Add Room'}"/>
                    </button>
                </div>
            </form>
        </div>
    </div>
</div>

<%@ include file="/WEB-INF/views/common/footer.jspf" %>
