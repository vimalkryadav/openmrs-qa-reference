<%@ include file="/WEB-INF/template/include.jsp"%>
<%@ include file="/WEB-INF/template/header.jsp"%>

<openmrs:require privilege="Manage LOGIC" otherwise="/login.htm" redirect="/module/logic/manageTokens.list" />

<%@ include file="localHeader.jsp"%>

<openmrs:htmlInclude file="/moduleResources/logic/css/datatables.css" />
<openmrs:htmlInclude file="/scripts/jquery/jquery.min.js" />
<openmrs:htmlInclude file="/moduleResources/logic/js/jquery.dataTables.min.js" />

<script>
	$j = jQuery.noConflict();
	$j(document).ready(function() {
        function text(value) { return $j("<span>").text(value == null ? "" : value).html(); }
			$j('.datatable').dataTable( {
				"bProcessing": true,
				"bServerSide": true,
				"sAjaxSource": "listTokensQuery.form",
				"bSort": false,
				"bPaginate": true,
				"bLengthChange": false,
				"iDisplayLength": 20,
				"oSearch": { sSearch: "" },
				"aoColumns": [
					{ "fnRender": function(oObj) { return '<a href="logic.form?token=' + encodeURIComponent(oObj.aData[1]) + '"><spring:message code="logic.token.manage.test"/></a>'; } },
					{ "fnRender": function(oObj) { return '<a href="editTokenRegistration.form?id=' + oObj.aData[4] + '">' + text(oObj.aData[1]) + '</a>'; } },
					{ "fnRender": function(oObj) { return text(String(oObj.aData[2] || "").split(".").pop()) } },
                    { "fnRender": function(oObj) { return text(oObj.aData[3]); } },
					{ "bVisible": false }
				]
			});
	});
</script>

<h2><spring:message code="logic.token.manage.title"/></h2>

<p><a href="editTokenRegistration.form">Add New Token</a></p>
<table class="datatable">
	<thead>
		<tr>
			<th></th>
			<th><spring:message code="logic.TokenRegistration.token"/></th>
			<th><spring:message code="logic.TokenRegistration.ruleProvider"/></th>
			<th><spring:message code="logic.TokenRegistration.configuration"/></th>
			<th>ID</th>
		</tr>
	</thead>
	<tbody>
	</tbody>
</table>

<br/>
<br/>

<%@ include file="/WEB-INF/template/footer.jsp"%>
