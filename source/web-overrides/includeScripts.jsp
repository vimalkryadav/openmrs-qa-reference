<!-- Include css from core -->
<link href="<openmrs:contextPath/>/scripts/jquery-ui/css/<spring:theme code='jqueryui.theme.name' />/jquery-ui.custom.css" type="text/css" rel="stylesheet" />

<!-- Include css from reporting module -->
<openmrs:htmlInclude file="${pageContext.request.contextPath}/moduleResources/reporting/scripts/jquery/autocomplete/css/jquery.autocomplete.css" />
<openmrs:htmlInclude file="${pageContext.request.contextPath}/moduleResources/reporting/scripts/jquery/dataTables/css/page.css"/>
<openmrs:htmlInclude file="${pageContext.request.contextPath}/moduleResources/reporting/scripts/jquery/dataTables/css/table.css"/>
<openmrs:htmlInclude file="${pageContext.request.contextPath}/moduleResources/reporting/scripts/jquery/dataTables/css/custom.css"/>
<openmrs:htmlInclude file="${pageContext.request.contextPath}/moduleResources/reporting/css/reporting.css"/>

<!-- Include javascript from core. Not needed on most pages unless they don't include the header -->
<openmrs:htmlInclude file="/scripts/jquery/jquery.min.js" />
<openmrs:htmlInclude file="/scripts/jquery-ui/js/jquery-ui.custom.min.js" />
<openmrs:htmlInclude file="/scripts/jquery-ui/js/jquery-ui-timepicker-addon.js" />
<openmrs:htmlInclude file="/scripts/jquery-ui/js/jquery-ui-datepicker-i18n.js" />
<openmrs:htmlInclude file="/scripts/jquery-ui/js/jquery-ui-timepicker-i18n.js" />

<!-- Include javascript from reporting module -->
<openmrs:htmlInclude file="${pageContext.request.contextPath}/moduleResources/reporting/scripts/jquery/dataTables/jquery.dataTables.min.js"/>
<openmrs:htmlInclude file='${pageContext.request.contextPath}/moduleResources/reporting/scripts/jquery/autocomplete/jquery.autocomplete.js'/>
<openmrs:htmlInclude file='${pageContext.request.contextPath}/moduleResources/reporting/scripts/jquery/autocomplete/jquery.ajaxQueue.js'/>
<openmrs:htmlInclude file='${pageContext.request.contextPath}/moduleResources/reporting/scripts/reporting.js'/>

<style>
/* Long user supplied report names must not widen the legacy tables. */
#page { max-width:100%; }
#container h1, #container h2, #container a, #container td, .metadataField { overflow-wrap:anywhere; word-break:normal; }
#container table.reporting-data-table, #report-history-table, #report-design-table { table-layout:fixed; width:100%; }
#container table.reporting-data-table th:last-child { width:70px; }
#container td[nowrap], #container td[nowrap=true] { white-space:normal; }
#container h2 { max-height:9em; overflow:auto; }
</style>
<script>
// DataTables 1.x must sort visible text, not anchor attributes or script markup.
if ($j.fn.dataTableExt) {
    $j.fn.dataTableExt.oSort['report-visible-asc'] = function(a,b) {
        function label(value) { var d=document.createElement('div'); d.innerHTML=value; var scripts=d.querySelectorAll('script'); for(var i=0;i<scripts.length;i++) scripts[i].parentNode.removeChild(scripts[i]); return d.textContent.trim().toLocaleLowerCase(); }
        a=label(a); b=label(b); return a < b ? -1 : a > b ? 1 : 0;
    };
    $j.fn.dataTableExt.oSort['report-visible-desc'] = function(a,b) { return -$j.fn.dataTableExt.oSort['report-visible-asc'](a,b); };
    $j.fn.dataTableExt.oSort['report-created-asc'] = function(a,b) {
        function timestamp(value) { var d=document.createElement('div'); d.innerHTML=value; var time=d.querySelector('[data-created]'); return time ? Number(time.getAttribute('data-created')) : 0; }
        return timestamp(a)-timestamp(b);
    };
    $j.fn.dataTableExt.oSort['report-created-desc'] = function(a,b) { return -$j.fn.dataTableExt.oSort['report-created-asc'](a,b); };
}
</script>
