<%@ include file="/WEB-INF/template/include.jsp"%>
<%@ include file="/WEB-INF/template/header.jsp"%>

<openmrs:require privilege="Manage LOGIC" otherwise="/login.htm" redirect="/admin/index.htm" />

<%@ include file="localHeader.jsp"%>

<openmrs:htmlInclude file="/scripts/jquery/jquery-1.3.2.min.js" />
<script type="text/javascript">
	var $j = jQuery.noConflict();
</script>

<openmrs:htmlInclude file="/scripts/jquery-ui/js/jquery-ui-1.7.2.custom.min.js" />
<openmrs:htmlInclude file="/scripts/jquery-ui/css/redmond/jquery-ui-1.7.2.custom.css" />

<script type="text/javascript">
    var progressTimer;
    var logicRunning = false;
    var settingsSaving = false;
    var savedClassFilter;
    var classFilter;
    function updateRunAvailability() {
        var dirty = classFilter && classFilter.value !== savedClassFilter;
        $j('#runnow').prop('disabled', logicRunning || settingsSaving || dirty);
        $j('#settingsStatus').text(settingsSaving ? 'Saving settings...' :
            dirty ? 'Save or cancel your settings changes before running initialization.' : '');
    }
    function bindSettingsGuard() {
        classFilter = document.querySelector('input[id^="gp_"]');
        if (!classFilter) return;
        savedClassFilter = classFilter.value;
        var actions = document.getElementById(classFilter.id + '_actions');
        var saving = document.getElementById(classFilter.id + '_saving');
        classFilter.addEventListener('input', function() {
            actions.style.display = '';
            updateRunAvailability();
        });
        actions.querySelectorAll('input')[1].onclick = function() {
            classFilter.value = savedClassFilter;
            actions.style.display = 'none';
            updateRunAvailability();
        };
        // Scope the stock portlet callback to this setting; the persisted value
        // changes only after DWR confirms success, never when Save is clicked.
        var setProperty = DWRAdministrationService.setGlobalProperty;
        DWRAdministrationService.setGlobalProperty = function(property, value, callback) {
            if (property !== 'logic.defaultTokens.conceptClasses')
                return setProperty.apply(this, arguments);
            settingsSaving = true;
            classFilter.disabled = true;
            updateRunAvailability();
            function finish() {
                settingsSaving = false;
                classFilter.disabled = false;
                saving.style.display = 'none';
                updateRunAvailability();
            }
            function saveFailed() {
                finish();
                actions.style.display = '';
                $j('#settingsStatus').text('Could not save settings. Save again or cancel your changes.');
            }
            return setProperty(property, value, {
                timeout: 10000,
                callback: function(result) {
                    savedClassFilter = value == null ? '' : value;
                    if (typeof callback === 'function') callback(result);
                    finish();
                },
                errorHandler: saveFailed,
                warningHandler: saveFailed
            });
        };
        updateRunAvailability();
    }
    function showFailure() {
        clearTimeout(progressTimer);
        $j('#loading').hide();
        logicRunning = false;
        updateRunAvailability();
        $j('#runnow').show();
        $j('#statusText').text('Could not initialize Logic rules. Check your connection and try again.');
    }
    function showComplete() {
        clearTimeout(progressTimer);
        $j('#loading').hide();
        $j('#complete').show();
        logicRunning = false;
        updateRunAvailability();
        $j('#runnow').show();
        $j('#statusText').text('<spring:message code="logic.init.status.complete" javaScriptEscape="true"/>');
    }
    function followProgress() {
        $j.ajax({url:'status.form',dataType:'json',cache:false,
            success:function(data){
                if (data.running) progressTimer=setTimeout(followProgress,1500);
                else showComplete();
            },error:showFailure});
    }
    function run() {
        updateRunAvailability();
        if ($j('#runnow').prop('disabled')) return;
        logicRunning = true;
        updateRunAvailability();
        $j('#complete').hide();
        $j('#loading').show();
        $j('#statusText').text('<spring:message code="logic.init.status.running" javaScriptEscape="true"/>');
        $j.ajax({url:'load.form',type:'POST',dataType:'json',cache:false,
            success:showComplete,error:showFailure});
    }
    $j(document).ready(function(){
        bindSettingsGuard();
        $j.ajax({url:'status.form',dataType:'json',cache:false,
            success:function(data){if(data.running){logicRunning=true;updateRunAvailability();$j('#loading').show();followProgress();}},
            error:showFailure});
    });
</script>

<h2><spring:message code="logic.init.title"/></h2>
<p style="width: 700px; white-space: normal;"><spring:message code="logic.init.description"/></p>

<p>
	<spring:message code="logic.init.propertyHelp"/>
	<openmrs:portlet url="globalProperties" parameters="propertyPrefix=logic.defaultTokens.conceptClasses|hidePrefix=false"/>
</p>

<p id="settingsStatus" role="status" aria-live="polite"></p>

<input class="btn-submit" id="runnow" name="runnow" accesskey="r" value="<spring:message code="logic.init.submit.button"/>" type="button" style="width: 100px;" onclick="javascript:run();"/>

<p>
	<img id="loading" src="${pageContext.request.contextPath}/images/loading.gif" style="display: none; margin-right: 10px;"/>
	<img id="complete" src="${pageContext.request.contextPath}/images/checkmark.png" style="display: none; margin-right: 10px;"/>
	<span id="statusText" role="status" aria-live="polite"></span></p>

<%@ include file="/WEB-INF/template/footer.jsp"%>
