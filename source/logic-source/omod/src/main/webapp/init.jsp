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
    function showFailure() {
        clearTimeout(progressTimer);
        $j('#loading').hide();
        $j('#runnow').prop('disabled', false).show();
        $j('#statusText').text('Could not initialize Logic rules. Check your connection and try again.');
    }
    function showComplete() {
        clearTimeout(progressTimer);
        $j('#loading').hide();
        $j('#complete').show();
        $j('#runnow').prop('disabled', false).show();
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
        $j('#runnow').prop('disabled',true);
        $j('#complete').hide();
        $j('#loading').show();
        $j('#statusText').text('<spring:message code="logic.init.status.running" javaScriptEscape="true"/>');
        $j.ajax({url:'load.form',type:'POST',dataType:'json',cache:false,
            success:showComplete,error:showFailure});
    }
    $j(document).ready(function(){
        $j.ajax({url:'status.form',dataType:'json',cache:false,
            success:function(data){if(data.running){$j('#runnow').prop('disabled',true);$j('#loading').show();followProgress();}},
            error:showFailure});
    });
</script>

<h2><spring:message code="logic.init.title"/></h2>
<p style="width: 700px; white-space: normal;"><spring:message code="logic.init.description"/></p>

<p>
	<spring:message code="logic.init.propertyHelp"/>
	<openmrs:portlet url="globalProperties" parameters="propertyPrefix=logic.defaultTokens.conceptClasses|hidePrefix=false"/>
</p>

<input class="btn-submit" id="runnow" name="runnow" accesskey="r" value="<spring:message code="logic.init.submit.button"/>" type="button" style="width: 100px;" onclick="javascript:run();"/>

<p>
	<img id="loading" src="${pageContext.request.contextPath}/images/loading.gif" style="display: none; margin-right: 10px;"/>
	<img id="complete" src="${pageContext.request.contextPath}/images/checkmark.png" style="display: none; margin-right: 10px;"/>
	<span id="statusText" role="status" aria-live="polite"></span></p>

<%@ include file="/WEB-INF/template/footer.jsp"%>
