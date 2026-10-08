<%@ include file="/WEB-INF/template/include.jsp"%>
<%@ include file="/WEB-INF/template/header.jsp"%>

<openmrs:require privilege="Manage LOGIC" otherwise="/login.htm" redirect="/module/logic/manageLogicRules.list" />

<%@ include file="localHeader.jsp"%>

<form method="post" action="deleteToken.form">
	<input type="hidden" name="id" value="${ tokenRegistration.id }"/>
	<input type="submit" value="<spring:message code="logic.token.edit.delete"/>" style="float: right"/>
</form>

<h2><spring:message code="logic.token.edit.title"/></h2>

<form method="post">
	<table>
		<tr>
			<th><spring:message code="logic.TokenRegistration.token"/></th>
			<td>
				<spring:bind htmlEscape="false" path="tokenRegistration.token">
					<input maxlength="${status.expression == 'configuration' ? 2000 : 512}" size="40" type="text" name="${status.expression}" value="<c:out value='${status.value}'/>"/>
					<c:if test="${status.errorMessage != ''}"><span class="error"><c:out value="${status.errorMessage}"/></span></c:if>
				</spring:bind>
			</td>
		</tr>
		<tr>
			<th><spring:message code="logic.TokenRegistration.ruleProvider"/></th>
			<td>
				<spring:bind htmlEscape="false" path="tokenRegistration.providerClassName">
					<input maxlength="${status.expression == 'configuration' ? 2000 : 512}" size="40" type="text" name="${status.expression}" value="<c:out value='${status.value}'/>"/>
					<c:if test="${status.errorMessage != ''}"><span class="error"><c:out value="${status.errorMessage}"/></span></c:if>
				</spring:bind>
			</td>
		</tr>
		<tr>
			<th><spring:message code="logic.TokenRegistration.configuration"/></th>
			<td>
				<spring:bind htmlEscape="false" path="tokenRegistration.configuration">
					<input maxlength="${status.expression == 'configuration' ? 2000 : 512}" size="40" type="text" name="${status.expression}" value="<c:out value='${status.value}'/>"/>
					<c:if test="${status.errorMessage != ''}"><span class="error"><c:out value="${status.errorMessage}"/></span></c:if>
				</spring:bind>
			</td>
		</tr>
		<tr>
			<th><spring:message code="logic.TokenRegistration.providerToken"/></th>
			<td>
				<spring:bind htmlEscape="false" path="tokenRegistration.providerToken">
					<input maxlength="${status.expression == 'configuration' ? 2000 : 512}" size="40" type="text" name="${status.expression}" value="<c:out value='${status.value}'/>"/>
					<c:if test="${status.errorMessage != ''}"><span class="error"><c:out value="${status.errorMessage}"/></span></c:if>
					<spring:message code="logic.token.edit.providerToken.warning"/>
				</spring:bind>
			</td>
		</tr>
		<tr>
			<th></th>
			<td>
				<input type="submit" value="<spring:message code="general.save"/>"/>
				<input type="button" value="<spring:message code="general.cancel"/>" onClick="window.location = 'manageTokens.list';"/>
			</td>
		</tr>
	</table>
</form>

<%@ include file="/WEB-INF/template/footer.jsp"%>