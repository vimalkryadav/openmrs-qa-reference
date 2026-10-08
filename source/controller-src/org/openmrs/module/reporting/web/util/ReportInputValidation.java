package org.openmrs.module.reporting.web.util;

import java.text.DateFormat;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;
import org.openmrs.api.context.Context;
import org.openmrs.module.htmlwidgets.web.WidgetUtil;
import org.openmrs.module.reporting.report.ReportProcessorConfiguration;
import org.openmrs.module.reporting.report.ReportProcessorConfiguration.ProcessorMode;
import org.openmrs.module.reporting.report.ReportRequest;
import org.openmrs.module.reporting.report.ReportRequest.Status;

/** Shared input and dispatch guards for the legacy Reports forms. */
public final class ReportInputValidation {
    private ReportInputValidation() { }

    public static Object parse(Object value, Class<?> type, Class<? extends Collection> collectionType) {
        if (type != Date.class) return WidgetUtil.parseInput(value, type, collectionType);
        if (collectionType != null) {
            Collection<Object> result = Set.class.isAssignableFrom(collectionType)
                    ? new LinkedHashSet<Object>() : new ArrayList<Object>();
            Iterable<?> values = value instanceof Iterable ? (Iterable<?>)value
                    : java.util.Arrays.asList(value instanceof Object[] ? (Object[])value : new Object[]{value});
            for (Object item : values) result.add(parseDate(item, Context.getDateFormat()));
            return result;
        }
        return parseDate(value, Context.getDateFormat());
    }

    public static Date parseDate(Object value, DateFormat suppliedFormat) {
        if (value instanceof Date) return (Date)value;
        if (value == null || value.toString().trim().isEmpty()) return null;
        String text = value.toString().trim();
        DateFormat format = (DateFormat)suppliedFormat.clone();
        format.setLenient(false);
        ParsePosition position = new ParsePosition(0);
        Date parsed = format.parse(text, position);
        if (parsed == null || position.getIndex() != text.length())
            throw new IllegalArgumentException("Enter a valid date in the displayed date format");
        return parsed;
    }

    public static boolean canProcess(ReportRequest request, ReportProcessorConfiguration processor) {
        if (request == null || processor == null || Boolean.TRUE.equals(processor.getRetired())) return false;
        ProcessorMode mode = processor.getProcessorMode();
        if (mode != ProcessorMode.ON_DEMAND && mode != ProcessorMode.ON_DEMAND_AND_AUTOMATIC) return false;
        boolean success = request.getStatus() == Status.COMPLETED || request.getStatus() == Status.SAVED;
        if (!(success && Boolean.TRUE.equals(processor.getRunOnSuccess()))
                && !(request.getStatus() == Status.FAILED && Boolean.TRUE.equals(processor.getRunOnError()))) return false;
        if (processor.getReportDesign() != null) {
            if (request.getRenderingMode() == null) return false;
            String design = processor.getReportDesign().getUuid();
            String argument = request.getRenderingMode().getArgument();
            if (design == null || argument == null || !(argument.equals(design) || argument.startsWith(design + ":"))) return false;
        }
        return true;
    }
}
