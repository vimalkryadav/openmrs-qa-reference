<%@ include file="/WEB-INF/template/include.jsp"%>
<%@ include file="/WEB-INF/template/header.jsp"%>
<openmrs:require privilege="Manage Token Registrations" otherwise="/login.htm" redirect="/module/calculation/patientCalculationTest.form" />
<%@ include file="localHeader.jsp" %>
<%@ taglib prefix="wgt" uri="/WEB-INF/view/module/htmlwidgets/resources/htmlwidgets.tld" %>

<style>
	.calculationTable th {text-align:left;}
	.resultTable th,td {padding-left:10px; padding-right:10px; text-align:left;}
</style>

<h3><spring:message code="calculation.CalculationRegistration.test.title"/></h3>

<table width="100%">
	<tr>
		<td valign="top" style="white-space:nowrap;">
			<table class="calculationTable">
				<tr>
					<th><spring:message code="calculation.CalculationRegistration.token"/></th>
					<td><a href="calculationRegistration.form?id=${calculationRegistration.id}">${calculationRegistration.token}</a></td>
				</tr>
				<tr>
					<th><spring:message code="calculation.CalculationRegistration.providerClassName"/></th>
					<td>${calculationRegistration.providerClassName}</td>
				</tr>
				<tr>
					<th><spring:message code="calculation.CalculationRegistration.calculationName"/></th>
					<td>${calculationRegistration.calculationName}</td>
				</tr>
				<tr>
					<th><spring:message code="calculation.CalculationRegistration.configuration"/></th>
					<td>${calculationRegistration.configuration}</td>
				</tr>
			</table>
			<br/>
			<form novalidate>
				<input type="hidden" name="id" value="${id}"/>
				<label for="calculation-patient-count">First active patients (0&ndash;1000)</label>
				<input type="number" min="0" max="1000" aria-describedby="calculation-error" id="calculation-patient-count" name="randomIds" aria-invalid="${invalidField == 'randomIds'}" size="10" value="<c:out value='${randomIds}'/>"/>
				<br/>
				 - <spring:message code="general.or"/> -
				<br/>
				<label for="calculation-patient-ids">Patient ids (up to 1000, comma separated)</label><br/>
				<textarea aria-describedby="calculation-error" id="calculation-patient-ids" name="patientIds" aria-invalid="${invalidField == 'patientIds'}" rows="4" cols="30"><c:out value="${patientIds}"/></textarea>
				<br/><br/>
				<c:if test="${!empty parameters}">
					Parameters<br/>
					<table>
						<c:forEach items="${parameters}" var="pd">
							<tr>
								<td><label for="calculation-param-${pd.key}"><c:out value="${pd.label}"/><c:if test="${pd.required}"> *</c:if>:</label></td>
								<td><c:choose><c:when test="${invalidField == pd.key}"><input type="text" id="calculation-param-${pd.key}" name="parameter.${pd.key}" aria-invalid="true" aria-describedby="calculation-error" value="<c:out value='${pd.value}'/>"/></c:when><c:when test="${empty pd.collection and pd.type == 'java.lang.Boolean'}"><label><input type="radio" name="parameter.${pd.key}" value="f" aria-describedby="calculation-error" <c:if test="${fn:toLowerCase(pd.value) == 'false' or fn:toLowerCase(pd.value) == 'f'}">checked="checked"</c:if>/> False</label> <label><input type="radio" name="parameter.${pd.key}" value="t" aria-describedby="calculation-error" <c:if test="${fn:toLowerCase(pd.value) == 'true' or fn:toLowerCase(pd.value) == 't'}">checked="checked"</c:if>/> True</label></c:when><c:when test="${not empty pd.collection}"><wgt:widget id="calculation-param-${pd.key}" name="parameter.${pd.key}" type="${pd.collection}" genericTypes="${pd.type}" defaultValue="${pd.value}" attributes="aria-describedby=calculation-error"/></c:when><c:otherwise><wgt:widget id="calculation-param-${pd.key}" name="parameter.${pd.key}" type="${pd.type}" defaultValue="${pd.value}" attributes="aria-describedby=calculation-error"/></c:otherwise></c:choose></td>
							</tr>
						</c:forEach>
					</table>
					<br/><br/>
				</c:if>
				<input type="submit" value="<spring:message code="calculation.CalculationRegistration.test"/>"/>
			</form>
		</td>
		<td valign="top" style="width:100%;">
			<c:if test="${!empty error}">
				<div id="calculation-error" role="alert"><c:out value="${error}"/></div>
			</c:if>
			<c:if test="${evaluated}">
				<spring:message code="calculation.CalculationRegistration.test.evaluationTime"/>: ${evaluationTime} ms
				<br/>
				<c:if test="${empty resultRows}"><p role="status">No results for the selected patients.</p></c:if>
                <table class="resultTable">
					<tr>
						<th><spring:message code="calculation.CalculationRegistration.test.patientId"/></th>
						<th><spring:message code="calculation.CalculationRegistration.test.result"/></th>
						<th><spring:message code="calculation.CalculationRegistration.test.resultType"/></th>
					</tr>
					<c:forEach items="${resultRows}" var="entry">
						<tr>
							<td>${entry.patientId}</td>
							<td><c:out value="${entry.result}"/></td>
							<td><c:out value="${entry.resultType}"/></td>
						</tr>
					</c:forEach>
				</table>
			</c:if>
		</td>
	</tr>
</table>

<%@ include file="/WEB-INF/template/footer.jsp"%>
