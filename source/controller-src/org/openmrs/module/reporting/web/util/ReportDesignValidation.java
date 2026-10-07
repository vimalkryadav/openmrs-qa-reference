package org.openmrs.module.reporting.web.util;

import org.openmrs.api.context.Context;
import org.openmrs.module.reporting.report.definition.ReportDefinition;
import org.openmrs.module.reporting.report.definition.service.ReportDefinitionService;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Reject incomplete design metadata before an existing design is changed. */
public final class ReportDesignValidation {
    private ReportDesignValidation() { }
    public static ReportDefinition requireMetadata(String name, String reportUuid, HttpServletResponse response) throws IOException {
        if (name == null || name.trim().isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Report design name is required");
            return null;
        }
        if (reportUuid == null || reportUuid.trim().isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Report definition is required");
            return null;
        }
        ReportDefinition definition = Context.getService(ReportDefinitionService.class).getDefinitionByUuid(reportUuid);
        if (definition == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Report definition not found");
            return null;
        }
        return definition;
    }
}
