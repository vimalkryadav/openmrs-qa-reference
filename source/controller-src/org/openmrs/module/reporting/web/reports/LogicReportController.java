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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.openmrs.api.context.Context;
import org.openmrs.module.reporting.dataset.definition.LogicDataSetDefinition;
import org.openmrs.module.reporting.dataset.definition.service.DataSetDefinitionService;
import org.openmrs.module.reporting.report.definition.ReportDefinition;
import org.openmrs.module.reporting.report.definition.service.ReportDefinitionService;
import org.openmrs.module.reporting.web.util.ReportInputValidation;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;

/** Stock row-report creation with validation before either definition is persisted. */
@Controller
public class LogicReportController {
    @RequestMapping("/module/reporting/reports/logicReport.form")
    public String showFormOrRedirect() { return "/module/reporting/reports/logicReport"; }

    @RequestMapping("/module/reporting/reports/logicReportCreate.form")
    public String createLogicReport(HttpServletRequest request, HttpServletResponse response, ModelMap model) {
        String name = ReportInputValidation.metadataText(request, "name");
        String description = ReportInputValidation.metadataText(request, "description");
        try {
            name = ReportInputValidation.metadataName(name);
            if (description.length() > 5000) throw new IllegalArgumentException("Description must be 5000 characters or fewer");
        } catch (IllegalArgumentException invalid) {
            response.setStatus(400);
            model.addAttribute("validationError", invalid.getMessage());
            model.addAttribute("submittedName", name);
            model.addAttribute("submittedDescription", description);
            return "/module/reporting/reports/logicReport";
        }
        LogicDataSetDefinition dataset = new LogicDataSetDefinition();
        dataset.setName((name.length() > 249 ? name.substring(0, 249) : name) + " (DSD)");
        Context.getService(DataSetDefinitionService.class).saveDefinition(dataset);
        ReportDefinition report = new ReportDefinition();
        report.setName(name);
        report.setDescription(description);
        report.addDataSetDefinition("dataset", dataset, null);
        Context.getService(ReportDefinitionService.class).saveDefinition(report);
        return "redirect:../datasets/logicDataSetEditor.form?uuid=" + dataset.getUuid();
    }
}
