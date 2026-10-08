<%@ include file="/WEB-INF/template/include.jsp"%>
<%@ include file="/WEB-INF/template/header.jsp"%>

<openmrs:require privilege="Manage Rule Definitions" otherwise="/login.htm" redirect="/module/logic/manageRuleDefinitions.list" />

<%@ include file="localHeader.jsp"%>

<form method="post" action="deleteRuleDefinition.form">
	<input type="hidden" name="id" value="${ rule.id }"/>
	<input type="submit" value="<spring:message code="logic.rule.edit.delete"/>" style="float: right"/>
</form>

<a href="manageRuleDefinitions.list"><spring:message code="logic.rule.edit.back"/></a>

<h2><spring:message code="logic.rule.edit.title"/></h2>
<c:if test="${not empty rule.id}"><p>Java class name: <code>org.openmrs.module.logic.rule.CompiledRule${rule.id}</code></p></c:if>

<form method="post">
	<table>
		<tr valign="top">
			<th><spring:message code="logic.RuleDefinition.name"/></th>
			<td>
				<spring:bind htmlEscape="false" path="rule.name">
					<input type="text" maxlength="255" name="${status.expression}" value="<c:out value='${status.value}'/>"/>
					<c:if test="${status.errorMessage != ''}"><span class="error"><c:out value="${status.errorMessage}"/></span></c:if>
				</spring:bind>
			</td>
		</tr>
		<tr valign="top">
			<th><spring:message code="logic.RuleDefinition.description"/></th>
			<td>
				<spring:bind htmlEscape="false" path="rule.description">
					<textarea maxlength="1000" rows="3" cols="80" name="${status.expression}"><c:out value="${status.value}"/></textarea>
					<c:if test="${status.errorMessage != ''}"><span class="error"><c:out value="${status.errorMessage}"/></span></c:if>
				</spring:bind>
			</td>
		</tr>
		<tr valign="top">
			<th><spring:message code="logic.RuleDefinition.language"/></th>
			<td>
				<spring:bind htmlEscape="false" path="rule.language">
					<select name="${status.expression}">
						<option value=""></option>
						<c:forEach var="language" items="${ruleLanguages}">
							<option value="${language.name}" <c:if test="${language.name == status.value}">selected="selected"</c:if>>
						        ${language.name}
						    </option>
						</c:forEach>
					</select>
					<c:if test="${status.errorMessage != ''}"><span class="error"><c:out value="${status.errorMessage}"/></span></c:if>
				</spring:bind>
			</td>
		</tr>
		<tr valign="top">
			<th><spring:message code="logic.RuleDefinition.ruleContent"/></th>
			<td>
				<spring:bind htmlEscape="false" path="rule.ruleContent">
					<textarea maxlength="2048" rows="20" cols="80" name="${status.expression}"><c:out value="${status.value}"/></textarea>
					<c:if test="${status.errorMessage != ''}"><span class="error"><c:out value="${status.errorMessage}"/></span></c:if>
				</spring:bind>
			</td>
		</tr>
		<tr valign="top">
			<th></th>
			<td>
				<input type="submit" value="<spring:message code="general.save"/>"/>
				<input type="button" value="<spring:message code="general.cancel"/>" onClick="window.location = 'manageRuleDefinitions.list';"/>
			</td>
		</tr>
	</table>
</form>

<%@ include file="/WEB-INF/template/footer.jsp"%>