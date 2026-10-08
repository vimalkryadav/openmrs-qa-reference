package org.openmrs.module.reporting.web.util;

import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

/** Keep report/query names consistent without trimming descriptions or SQL text. */
@ControllerAdvice(basePackages="org.openmrs.module.reporting.web")
public class ReportingNameBinding {
    @InitBinder
    public void bindNames(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, "name", new StringTrimmerEditor(false));
    }
}
