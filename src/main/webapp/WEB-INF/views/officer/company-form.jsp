<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="isEdit" value="${formMode == 'edit'}" />
<c:set var="pageTitle" value="${isEdit ? 'Edit Corporate Partner - HireFlow' : 'Register Corporate Partner - HireFlow'}" />
<c:set var="activeNav" value="companies" />
<c:set var="breadcrumbCurrent" value="${isEdit ? 'Edit Company' : 'New Company'}" />
<%@ include file="/WEB-INF/views/common/header.jspf" %>

<div style="max-width: 700px; margin: 2rem auto; padding: 0 1rem;">
    <div class="card">
        <div class="card-body" style="padding: 2.5rem;">
            <div style="margin-bottom: 2rem;">
                <a href="${pageContext.request.contextPath}/officer/companies" style="color: var(--primary); font-size: 0.875rem; text-decoration: none; font-weight: 600;">
                    &larr; Back to Companies Directory
                </a>
                <h1 style="font-size: 1.75rem; font-weight: 800; color: var(--text-primary); margin: 0.75rem 0 0.25rem;">
                    <c:out value="${isEdit ? 'Edit Corporate Partner' : 'Register Corporate Partner'}"/>
                </h1>
                <p style="color: var(--text-muted); margin: 0; font-size: 0.875rem;">
                    <c:out value="${isEdit ? 'Update registered employer details and industry sector classification.' : 'Add a visiting campus recruitment employer to the university placement registry.'}"/>
                </p>
            </div>

            <form action="${pageContext.request.contextPath}/officer/companies${isEdit ? '/'.concat(company.id).concat('/update') : ''}"
                  method="POST" id="companyForm">
                <input type="hidden" name="_csrf" value="<c:out value='${csrfToken}'/>" />

                <div class="form-group" style="margin-bottom: 1.5rem;">
                    <label for="companyId" class="form-label" style="font-weight: 600; display: block; margin-bottom: 0.5rem;">
                        Company ID <span style="color: var(--danger);">*</span>
                    </label>
                    <c:choose>
                        <c:when test="${isEdit}">
                            <input type="text" id="companyId" name="id" class="form-control"
                                   value="<c:out value='${company.id}'/>" readonly
                                   style="background-color: var(--bg-hover); cursor: not-allowed; font-weight: 600;" />
                            <small style="color: var(--text-muted); font-size: 0.75rem;">
                                Primary identifier cannot be modified once registered.
                            </small>
                        </c:when>
                        <c:otherwise>
                            <input type="text" id="companyId" name="id" class="form-control"
                                   placeholder="e.g. CMP-001 or GOOGLE" required autofocus
                                   style="text-transform: uppercase;" />
                            <small style="color: var(--text-muted); font-size: 0.75rem;">
                                Alphanumeric identifier (will be converted to uppercase).
                            </small>
                        </c:otherwise>
                    </c:choose>
                </div>

                <div class="form-group" style="margin-bottom: 1.5rem;">
                    <label for="companyName" class="form-label" style="font-weight: 600; display: block; margin-bottom: 0.5rem;">
                        Company Name <span style="color: var(--danger);">*</span>
                    </label>
                    <input type="text" id="companyName" name="name" class="form-control"
                           value="<c:out value='${isEdit ? company.name : ""}'/>"
                           placeholder="e.g. Google India Pvt. Ltd." required maxlength="150" />
                </div>

                <div class="form-group" style="margin-bottom: 2rem;">
                    <label for="companyIndustry" class="form-label" style="font-weight: 600; display: block; margin-bottom: 0.5rem;">
                        Industry / Sector <span style="color: var(--danger);">*</span>
                    </label>
                    <input type="text" id="companyIndustry" name="industry" class="form-control"
                           value="<c:out value='${isEdit ? company.industry : ""}'/>"
                           placeholder="e.g. Information Technology, Cloud Computing, Core Engineering" required maxlength="150" />
                </div>

                <div style="display: flex; gap: 1rem; align-items: center; justify-content: flex-end;">
                    <a href="${pageContext.request.contextPath}/officer/companies" class="btn btn-outline">
                        Cancel
                    </a>
                    <button type="submit" class="btn btn-primary" id="btnSubmitCompany">
                        <c:out value="${isEdit ? 'Save Changes' : 'Register Partner'}"/>
                    </button>
                </div>
            </form>
        </div>
    </div>
</div>

<%@ include file="/WEB-INF/views/common/footer.jspf" %>
