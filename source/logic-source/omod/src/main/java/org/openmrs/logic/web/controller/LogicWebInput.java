package org.openmrs.logic.web.controller;

import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestWrapper;
import javax.servlet.http.HttpServletRequest;

/** Read literal rule source and metadata; the corresponding JSP sinks escape output. */
public final class LogicWebInput {
    private LogicWebInput() { }
    public static String text(HttpServletRequest request, String name) {
        ServletRequest original = request;
        while (original instanceof ServletRequestWrapper)
            original = ((ServletRequestWrapper) original).getRequest();
        String value = original.getParameter(name);
        return value == null ? "" : value;
    }
}
