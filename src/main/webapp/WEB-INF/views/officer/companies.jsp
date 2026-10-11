<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Corporate Partners - HireFlow Officer Console" />
<c:set var="activeNav" value="companies" />
<c:set var="breadcrumbCurrent" value="Corporate Partners" />
<%@ include file="/WEB-INF/views/common/header.jspf" %>

<div style="max-width: 1100px; margin: 2rem auto; padding: 0 1rem;">
    <!-- Directory Header Card -->
    <div class="card" style="margin-bottom: 2rem;">
        <div class="card-body" style="padding: 2rem; display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 1rem;">
            <div>
                <h1 style="font-size: 1.75rem; font-weight: 800; color: var(--text-primary); margin: 0 0 0.25rem;">
                    🏢 Corporate Partners Directory
                </h1>
                <p style="color: var(--text-muted); margin: 0; font-size: 0.875rem;">
                    Manage participating recruitment organizations, enterprise partners, and industry classifications.
                </p>
            </div>
            <div>
                <a href="${pageContext.request.contextPath}/officer/companies/new" class="btn btn-primary" id="btnAddNewCompany">
                    + Register New Partner
                </a>
            </div>
        </div>
    </div>

    <!-- Companies Table Card -->
    <div class="card">
        <div class="card-body" style="padding: 1.75rem;">
            <c:choose>
                <c:when test="${not empty companies}">
                    <div style="overflow-x: auto;">
                        <table style="width: 100%; border-collapse: collapse; font-size: 0.95rem;">
                            <thead>
                                <tr style="border-bottom: 2px solid var(--border-color); text-align: left;">
                                    <th style="padding: 0.75rem 1rem;">Company ID</th>
                                    <th style="padding: 0.75rem 1rem;">Company Name</th>
                                    <th style="padding: 0.75rem 1rem;">Industry / Sector</th>
                                    <th style="padding: 0.75rem 1rem; text-align: right;">Actions</th>
                                </tr>
                            </thead>
                            <tbody>
                                <c:forEach var="company" items="${companies}">
                                    <tr style="border-bottom: 1px solid var(--border-color);" id="company-row-${company.id}">
                                        <td style="padding: 0.75rem 1rem; font-weight: 600;">
                                            <code><c:out value="${company.id}"/></code>
                                        </td>
                                        <td style="padding: 0.75rem 1rem; font-weight: 600; color: var(--text-primary);">
                                            <c:out value="${company.name}"/>
                                        </td>
                                        <td style="padding: 0.75rem 1rem; color: var(--text-secondary);">
                                            <c:out value="${company.industry}"/>
                                        </td>
                                        <td style="padding: 0.75rem 1rem; text-align: right;">
                                            <a href="${pageContext.request.contextPath}/officer/companies/${company.id}/edit"
                                               class="btn btn-outline" style="font-size: 0.85rem; padding: 0.35rem 0.75rem;"
                                               id="editCompanyBtn-${company.id}">
                                                Edit
                                            </a>
                                        </td>
                                    </tr>
                                </c:forEach>
                            </tbody>
                        </table>
                    </div>
                </c:when>
                <c:otherwise>
                    <div style="text-align: center; padding: 3rem 1rem; color: var(--text-muted);">
                        <div style="font-size: 2.5rem; margin-bottom: 1rem;">🏢</div>
                        <h3 style="font-size: 1.15rem; font-weight: 700; color: var(--text-primary); margin-bottom: 0.5rem;">
                            No Corporate Partners Registered
                        </h3>
                        <p style="font-size: 0.9rem; margin-bottom: 1.5rem;">
                            Add your first employer profile to enable recruitment drives.
                        </p>
                        <a href="${pageContext.request.contextPath}/officer/companies/new" class="btn btn-primary">
                            + Register New Partner
                        </a>
                    </div>
                </c:otherwise>
            </c:choose>
        </div>
    </div>
</div>

<%@ include file="/WEB-INF/views/common/footer.jspf" %>
