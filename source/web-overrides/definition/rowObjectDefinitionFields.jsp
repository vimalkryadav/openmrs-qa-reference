<input type="hidden" id="${p.field.name}" name="parameter.${p.field.name}.value"/>
<div id="object-editor-${p.field.name}"></div>
<c:if test="${p.field.name == 'columnDefinitions'}">
<textarea id="object-editor-data" hidden="hidden"><c:out value="${objectEditorJson}"/></textarea>
<%@ include file="rowObjectEditorScript.jsp" %>
<script>
$j(function() {
    window.reportingObjectEditor(JSON.parse(document.getElementById('object-editor-data').value),
        {columnDefinitions:'columnDefinitions',sortCriteria:'sortCriteria',rowFilters:'rowFilters'},'object');
    ['columnDefinitions','sortCriteria','rowFilters'].forEach(function(name){
        var mode=document.getElementById('selectValue'+name);
        if(mode && mode.value!=='t'){$j(mode).val('f').trigger('change');}
    });
});
</script>
</c:if>
