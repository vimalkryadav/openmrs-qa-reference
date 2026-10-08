<%@ include file="../definition/rowObjectEditorScript.jsp" %>
<textarea id="structured-parameter-model" hidden="hidden"><c:out value="${structuredGroupsJson}"/></textarea>
<script>
$j(function(){
    var data=document.getElementById('structured-parameter-model').value;
    if(data)JSON.parse(data).forEach(function(group){window.reportingObjectEditor(group,group.fields,group.prefix);});
});
</script>
