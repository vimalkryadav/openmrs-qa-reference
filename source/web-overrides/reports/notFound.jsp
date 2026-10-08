<%@ include file="/WEB-INF/template/include.jsp"%>
<%@ include file="../run/localHeader.jsp"%>
<div id="page"><div id="container"><h1>Report unavailable</h1>
<p role="alert"><c:out value="${message}"/></p>
<p><a href="${pageContext.request.contextPath}/module/reporting/dashboard/index.form">Report Dashboard</a> |
<a href="${pageContext.request.contextPath}/module/reporting/reports/manageReports.form">Report Administration</a> |
<a href="${pageContext.request.contextPath}/module/reporting/reports/reportHistory.form">Report History</a></p></div></div>
<%@ include file="/WEB-INF/template/footer.jsp"%>
