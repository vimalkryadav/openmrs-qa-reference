/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.reporting.web.reports;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.module.ModuleUtil;
import org.openmrs.module.reporting.ReportingConstants;
import org.openmrs.module.reporting.common.DateUtil;
import org.openmrs.module.reporting.common.ObjectUtil;
import org.openmrs.module.reporting.propertyeditor.ReportDefinitionEditor;
import org.openmrs.module.reporting.report.Report;
import org.openmrs.module.reporting.report.ReportData;
import org.openmrs.module.reporting.report.ReportProcessorConfiguration;
import org.openmrs.module.reporting.report.ReportProcessorConfiguration.ProcessorMode;
import org.openmrs.module.reporting.report.ReportRequest;
import org.openmrs.module.reporting.report.ReportRequest.PriorityComparator;
import org.openmrs.module.reporting.report.ReportRequest.Status;
import org.openmrs.module.reporting.report.definition.ReportDefinition;
import org.openmrs.module.reporting.report.processor.ReportProcessor;
import org.openmrs.module.reporting.report.renderer.RenderingMode;
import org.openmrs.module.reporting.report.service.ReportService;
import org.openmrs.module.reporting.report.util.ReportUtil;
import org.openmrs.module.reporting.web.renderers.WebReportRenderer;
import org.openmrs.module.reporting.web.util.AjaxUtil;
import org.openmrs.propertyeditor.UserEditor;
import org.openmrs.util.OpenmrsConstants;
import org.openmrs.util.OpenmrsUtil;
import org.openmrs.web.WebConstants;
import org.springframework.beans.propertyeditors.CustomDateEditor;
import org.springframework.beans.propertyeditors.CustomNumberEditor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Controller
public class ReportHistoryController {

    private final Log log = LogFactory.getLog(getClass());

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        SimpleDateFormat dateFormat = Context.getDateFormat();
        dateFormat.setLenient(false);
        binder.registerCustomEditor(ReportDefinition.class, new ReportDefinitionEditor());
        binder.registerCustomEditor(User.class, new UserEditor());
        binder.registerCustomEditor(Integer.class, new CustomNumberEditor(Integer.class, true));
        binder.registerCustomEditor(Date.class, new CustomDateEditor(dateFormat, true, 10));
    }

    @RequestMapping("/module/reporting/reports/validateSchedule.form")
    public void validateSchedule(@RequestParam("expression") String expression, HttpServletResponse response) throws IOException {
        String error = org.openmrs.module.reporting.web.util.ReportInputValidation.scheduleError(expression);
        response.setContentType("text/plain;charset=UTF-8");
        if (error != null) { response.setStatus(400); response.getWriter().write(error); }
    }

    @RequestMapping("/module/reporting/reports/reportHistory.form")
    public void showReportHistory(ModelMap model,
                                  @RequestParam(value="reportDefinition", required=false) ReportDefinition reportDefinition,
                                  @RequestParam(value="requestedBy", required=false) User requestedBy,
                                  @RequestParam(value="statuses", required=false) Status[] statuses,
                                  @RequestParam(value="requestOnOrAfter", required=false) String afterText,
                                  @RequestParam(value="requestOnOrBefore", required=false) String beforeText) {

        Date requestOnOrAfter = null, requestOnOrBefore = null;
        try {
            requestOnOrAfter = org.openmrs.module.reporting.web.util.ReportInputValidation.parseDate(afterText, Context.getDateFormat());
            requestOnOrBefore = org.openmrs.module.reporting.web.util.ReportInputValidation.parseDate(beforeText, Context.getDateFormat());
            if (requestOnOrAfter != null && requestOnOrBefore != null && requestOnOrAfter.after(requestOnOrBefore))
                throw new IllegalArgumentException("Requested from must be on or before the end date");
        } catch (IllegalArgumentException invalid) { model.addAttribute("dateError", invalid.getMessage()); }
        model.addAttribute("afterText", afterText);
        model.addAttribute("beforeText", beforeText);
        Status[] historyStatuses = new Status[] {Status.COMPLETED, Status.SAVED, Status.FAILED};
        model.addAttribute("historyStatuses", historyStatuses);
        if (statuses == null) {
            statuses = historyStatuses;
        }

        model.addAttribute("reportDefinition", reportDefinition);
        model.addAttribute("requestedBy", requestedBy);
        model.addAttribute("statuses", Arrays.asList(statuses));
        model.addAttribute("requestOnOrAfter", requestOnOrAfter);
        model.addAttribute("requestOnOrBefore", requestOnOrBefore);

        requestOnOrBefore = DateUtil.getEndOfDayIfTimeExcluded(requestOnOrBefore);

        List<ReportRequest> history = model.containsAttribute("dateError") ? new ArrayList<ReportRequest>()
                : getReportService().getReportRequests(reportDefinition, requestOnOrAfter, requestOnOrBefore, statuses);
        if (requestedBy != null) {
            for (Iterator<ReportRequest> i = history.iterator(); i.hasNext();) {
                ReportRequest rr = i.next();
                if (!rr.getRequestedBy().equals(requestedBy)) {
                    i.remove();
                }
            }
        }
        Collections.sort(history, new PriorityComparator());
        Collections.reverse(history);
        model.addAttribute("history", history);

        List<RenderingMode> renderingModes = new ArrayList<RenderingMode>();
        for (ReportRequest reportRequest : history) {
            for (RenderingMode mode : getReportService().getRenderingModes(reportRequest.getReportDefinition().getParameterizable())) {
                if (OpenmrsUtil.nullSafeEquals(mode, reportRequest.getRenderingMode())) {
                    reportRequest.setRenderingMode(mode);
                }
            }
        }
        model.addAttribute("renderingModes", renderingModes);
    }

    @RequestMapping("/module/reporting/reports/deleteReportRequest.form")
    public String deleteReportRequest(@RequestParam("uuid") String uuid,
                                      @RequestParam(value="returnUrl", required=false) String returnUrl, HttpServletRequest httpRequest) {
        ReportService rs = Context.getService(ReportService.class);
        ReportRequest request = rs.getReportRequestByUuid(uuid);
        if (request != null) rs.purgeReportRequest(request);
        httpRequest.getSession().setAttribute(WebConstants.OPENMRS_MSG_ATTR, "Report request deleted");
        return "redirect:" + ObjectUtil.nvlStr(returnUrl, "reportHistory.form");
    }

    @RequestMapping("/module/reporting/reports/loadReportStatus.form")
    public String loadReportStatus(ModelMap model, @RequestParam("uuid") String uuid) {

        ReportService rs = Context.getService(ReportService.class);
        ReportRequest request = rs.getReportRequestByUuid(uuid);
        if (request == null) {
            model.addAttribute("json", Collections.singletonMap("status", "DELETED"));
            return "/module/reporting/json";
        }
        String status = request.getStatus().toString();
        List<String> reportLog = rs.loadReportLog(request);
        if ("REQUESTED".equals(status)) {
            for (String s : reportLog) {
                if (s.indexOf("Starting to process report") != -1) {
                    status = "PROCESSING"; // This shouldn't be needed, and is a hack.  Needed until we can work out txns
                }
            }
        }
        Map<String, Object> statusMap = new HashMap<String, Object>();
        statusMap.put("status", status);
        statusMap.put("log", reportLog);

        // This is needed due to differences in Spring versions in OpenMRS core and how Spring handles JSON
        Object json = ModuleUtil.compareVersion(OpenmrsConstants.OPENMRS_VERSION_SHORT, "1.11") >= 0 ? statusMap : AjaxUtil.toJson(statusMap);
        model.addAttribute("json", json);

        return "/module/reporting/json";
    }

    @RequestMapping("/module/reporting/reports/viewErrorDetails.form")
    public void viewErrorDetails(HttpServletResponse response, @RequestParam("uuid") String uuid) throws IOException {
        ReportRequest rr = Context.getService(ReportService.class).getReportRequestByUuid(uuid);
        String error = null;
        if (rr != null) {
            try { error = Context.getService(ReportService.class).loadReportError(rr); }
            catch (RuntimeException unavailable) { log.warn("Report error file is unavailable for " + uuid); }
        }
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write(error == null || error.trim().isEmpty()
                ? "Failure details are unavailable for this request. View the report log or run the report again to capture a new error." : error);
    }

    @RequestMapping("/module/reporting/reports/reportHistorySave.form")
    public String saveHistoryElement(@RequestParam("uuid") String uuid, @RequestParam(value="description", required=false) String description,
                                    HttpServletRequest request) {
        ReportService rs = Context.getService(ReportService.class);
        ReportRequest rr = rs.getReportRequestByUuid(uuid);
        if (rr == null) return "redirect:/module/reporting/reports/reportHistory.form";
        description = org.openmrs.module.reporting.web.util.ReportInputValidation.metadataText(request, "description").trim();
        if (description.length() > 1000) throw new IllegalArgumentException("Description must be 1000 characters or fewer");
        Report report = rs.loadReport(rr);
        rs.saveReport(report, description);
        request.getSession().setAttribute(WebConstants.OPENMRS_MSG_ATTR, "Report saved");
        return "redirect:/module/reporting/reports/reportHistoryOpen.form?uuid="+uuid;
    }

    @RequestMapping("/module/reporting/reports/reportHistoryOpen.form")
    public String openFromHistory(@RequestParam(value="uuid", required=false) String uuid, HttpServletResponse response, WebRequest request, ModelMap model) throws IOException {
        ReportService rs = Context.getService(ReportService.class);
        ReportRequest req = uuid == null ? null : rs.getReportRequestByUuid(uuid);
        if (req == null) {
            log.warn("Cannot load report request " + uuid);
            request.setAttribute(WebConstants.OPENMRS_ERROR_ATTR, "Cannot load report request", WebRequest.SCOPE_SESSION);
            return "redirect:/module/reporting/reports/reportHistory.form";
        }
        for (RenderingMode mode : getReportService().getRenderingModes(req.getReportDefinition().getParameterizable())) {
            if (OpenmrsUtil.nullSafeEquals(mode, req.getRenderingMode())) {
                req.setRenderingMode(mode);
            }
        }
        model.addAttribute("request", req);

        if (req.getStatus() == Status.REQUESTED) {
            model.addAttribute("positionInQueue", rs.getPositionInQueue(req));
        }
        if (req.getStatus() == Status.FAILED) {
            try { model.addAttribute("errorDetails", rs.loadReportError(req)); }
            catch (RuntimeException unavailable) { model.addAttribute("errorDetails", "Failure details are unavailable"); }
        }

        List<ReportProcessorConfiguration> onDemandProcessors = new ArrayList<ReportProcessorConfiguration>();
        List<ReportProcessorConfiguration> automaticProcessors = new ArrayList<ReportProcessorConfiguration>();

        for (ReportProcessorConfiguration c : ReportUtil.getAvailableReportProcessorConfigurations(req, ProcessorMode.values())) {
            ProcessorMode m = c.getProcessorMode();
            if (org.openmrs.module.reporting.web.util.ReportInputValidation.canProcess(req, c)) {
                onDemandProcessors.add(c);
            }
            if (m == ProcessorMode.AUTOMATIC || m == ProcessorMode.ON_DEMAND_AND_AUTOMATIC) {
                automaticProcessors.add(c);
            }
        }
        model.addAttribute("onDemandProcessors", onDemandProcessors);
        model.addAttribute("automaticProcessors", automaticProcessors);

        return "/module/reporting/reports/reportHistoryOpen";
    }

    @RequestMapping("/module/reporting/reports/viewReport.form")
    public ModelAndView viewReport(@RequestParam("uuid") String uuid, HttpServletResponse response, HttpServletRequest request) throws IOException {
        ReportRequest req = getReportService().getReportRequestByUuid(uuid);
        RenderingMode rm = req.getRenderingMode();
        String linkUrl = "/module/reporting/reports/reportHistoryOpen";

        if (rm.getRenderer() instanceof WebReportRenderer) {
            WebReportRenderer webRenderer = (WebReportRenderer) rm.getRenderer();
            linkUrl = webRenderer.getLinkUrl(req.getReportDefinition().getParameterizable());
            linkUrl = request.getContextPath() + (linkUrl.startsWith("/") ? "" : "/") + linkUrl;
            if (req != null) {
                ReportData reportData = getReportService().loadReportData(req);
                if (reportData != null) {
                    request.getSession().setAttribute(ReportingConstants.OPENMRS_REPORT_DATA, reportData);
                    request.getSession().setAttribute(ReportingConstants.OPENMRS_REPORT_ARGUMENT, rm.getArgument());
                    request.getSession().setAttribute(ReportingConstants.OPENMRS_LAST_REPORT_URL, linkUrl);
                }
            }
            return new ModelAndView(new RedirectView(linkUrl));
        }
        else {
            String filename = rm.getRenderer().getFilename(req).replace(" ", "_");
            response.setContentType(rm.getRenderer().getRenderedContentType(req));
            byte[] data = getReportService().loadRenderedOutput(req);

            if (data != null) {
                response.setHeader("Content-Disposition", "attachment; filename=" + filename);
                response.setHeader("Pragma", "no-cache");
                IOUtils.write(data, response.getOutputStream());
            }
            else {
                response.getWriter().write("There was an error retrieving the report");
            }
            return null;
        }

    }

    @RequestMapping("/module/reporting/reports/reportHistoryProcess.form")
    public String runOnDemandPostProcessor(@RequestParam("uuid") String requestUuid, @RequestParam("processorUuid") String processorUuid, HttpServletResponse response, HttpServletRequest request) throws IOException {
        ReportRequest req = getReportService().getReportRequestByUuid(requestUuid);
        ReportProcessorConfiguration rpc = getReportService().getReportProcessorConfigurationByUuid(processorUuid);
        if (req == null || rpc == null) { response.sendError(404, "Report request or processor not found"); return null; }
        if (!org.openmrs.module.reporting.web.util.ReportInputValidation.canProcess(req, rpc)) {
            response.sendError(400, "This processor is not eligible for this report request"); return null;
        }
        try {
            boolean completed = req.getStatus() == Status.COMPLETED || req.getStatus() == Status.SAVED;
            if ((completed && rpc.getRunOnSuccess()) || (req.getStatus() == Status.FAILED && rpc.getRunOnError())) {
                getReportService().logReportMessage(req, "Processing Report with " + rpc.getName() + "...");
                Class<?> processorType = Context.loadClass(rpc.getProcessorType());
                ReportProcessor processor = (ReportProcessor) processorType.newInstance();
                Report report = getReportService().loadReport(req);
                processor.process(report, rpc.getConfiguration());
                request.getSession().setAttribute(WebConstants.OPENMRS_MSG_ATTR, rpc.getName() + " " + Context.getMessageSourceService().getMessage("reporting.runReport.processorSuccess"));
            }
        }
        catch (Exception e) {
            log.warn("Report Processor Failed: " + rpc.getName(), e);
            getReportService().logReportMessage(req, "Report Processor Failed: " + rpc.getName());
            e.printStackTrace();
            request.getSession().setAttribute(WebConstants.OPENMRS_ERROR_ATTR, rpc.getName() + " " + Context.getMessageSourceService().getMessage("reporting.runReport.processorFailed"));
        }
        return "redirect:reportHistoryOpen.form?uuid=" + requestUuid;
    }


    private ReportService getReportService() {
        return Context.getService(ReportService.class);
    }
}
