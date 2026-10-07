/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.reporting.web.controller;

import org.apache.commons.lang.StringUtils;
import org.openmrs.module.reporting.web.util.StructuredReportParameters;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Cohort;
import org.openmrs.api.context.Context;
import org.openmrs.module.htmlwidgets.web.WidgetUtil;
import org.openmrs.module.reporting.data.DataDefinition;
import org.openmrs.module.reporting.dataset.definition.LogicDataSetDefinition;
import org.openmrs.module.reporting.dataset.IterableSqlDataSet;
import org.openmrs.module.reporting.dataset.DataSet;
import org.openmrs.module.reporting.report.ReportData;
import org.openmrs.module.reporting.dataset.definition.PatientDataSetDefinition;
import org.openmrs.module.reporting.evaluation.EvaluationContext;
import org.openmrs.module.reporting.evaluation.MissingDependencyException;
import org.openmrs.module.reporting.evaluation.parameter.Parameter;
import org.openmrs.module.reporting.evaluation.parameter.ParameterException;
import org.openmrs.module.reporting.evaluation.parameter.Parameterizable;
import org.openmrs.module.reporting.evaluation.parameter.ParameterizableUtil;
import org.openmrs.web.WebConstants;
import org.openmrs.web.WebUtil;
import org.springframework.beans.propertyeditors.CustomDateEditor;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

@Controller
public class QueryParameterFormController {

	/* Logger */
	private Log log = LogFactory.getLog(this.getClass());

	
	/**
	 * Allows us to bind a custom editor for a class.
	 * @param binder
	 */
    @InitBinder
    public void initBinder(WebDataBinder binder) { 
        // Runtime values belong to EvaluationContext, never to the managed definition.
        binder.setDisallowedFields("columnDefinitions", "columnDefinitions*", "sortCriteria", "sortCriteria*");
    	binder.registerCustomEditor(Date.class, new CustomDateEditor(Context.getDateFormat(), true)); 
    }
	
	/**
	 * Processes the form when a user submits.
	 */	
	@RequestMapping("/module/reporting/parameters/queryParameter.form")
	public ModelAndView processForm(
			HttpServletRequest request,	
			HttpServletResponse response,	
			@RequestParam(value = "uuid", required=false) String uuid,
			@RequestParam(value = "type", required=false) Class<Parameterizable> type,
			@RequestParam(value = "action", required=false) String action,
			@RequestParam(value = "format", required=false) String format,
			@RequestParam(value = "successView", required=false) String successView,
			@ModelAttribute("parameterizable") Parameterizable parameterizable, 
			BindingResult bindingResult) throws Exception {
		
		if ( parameterizable == null ) {
			parameterizable = ParameterizableUtil.getParameterizable(uuid, type);
		}
		
        Map<String,Object> rawStructuredValues=new HashMap<String,Object>();
        for (Parameter parameter : parameterizable.getParameters()) rawStructuredValues.put(parameter.getName(),request.getParameter(parameter.getName()));
        try {
            for (Map.Entry<String,Object> entry : StructuredReportParameters.model(parameterizable,rawStructuredValues).entrySet()) request.setAttribute(entry.getKey(),entry.getValue());
        } catch (IllegalArgumentException invalid) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            request.setAttribute("structuredParameterError",invalid.getMessage());
            return new ModelAndView("/module/reporting/parameters/queryParameterForm");
        }

		if (parameterizable != null && parameterizable.getParameters().isEmpty() && StringUtils.isEmpty(action)) {
			action = "preview";
		}
		
		if (StringUtils.isEmpty(action)) {
			request.getSession().removeAttribute("results");
			return new ModelAndView("/module/reporting/parameters/queryParameterForm");
		}
		else {
		
			Object results = null;
			ModelAndView model = new ModelAndView();		
				
			if (parameterizable != null) {			
				EvaluationContext evaluationContext = new EvaluationContext();

				if (parameterizable instanceof DataDefinition || parameterizable instanceof LogicDataSetDefinition
                    || parameterizable instanceof PatientDataSetDefinition
                    || parameterizable instanceof org.openmrs.module.reporting.dataset.definition.RowPerObjectDataSetDefinition) {
				    Integer randomBaseCohortSize = 50;
				    // Preview must not materialize every patient in a large database.
                    Cohort baseCohort = new Cohort();
                    List<List<Object>> previewIds = Context.getAdministrationService().executeSQL(
                        "select p.patient_id from patient p inner join person pe on pe.person_id = p.patient_id where p.voided = 0 and pe.voided = 0 order by p.patient_id limit 50", true);
                    for (List<Object> row : previewIds) {
                        baseCohort.addMember(((Number) row.get(0)).intValue());
                    }
				    evaluationContext.setBaseCohort(baseCohort);
				    model.addObject("randomBaseCohortSize", randomBaseCohortSize);
                }
				
				Map<String, Object> parameterValues = new HashMap<String, Object>();
				if (parameterizable != null && parameterizable.getParameters() != null) { 
					for (Parameter p : parameterizable.getParameters()) {
                        try {
                            Object paramVal = StructuredReportParameters.targets(parameterizable).containsKey(p.getName())
                                ? StructuredReportParameters.parse(parameterizable,p.getName(),request.getParameter(p.getName()))
                                : WidgetUtil.getFromRequest(request, p.getName(), p.getType(), p.getCollectionType());
                            parameterValues.put(p.getName(), paramVal);
                        } catch (IllegalArgumentException invalidValue) {
                            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                            request.getSession().removeAttribute("results");
                            request.getSession().setAttribute(WebConstants.OPENMRS_ERROR_ATTR,
                                WebUtil.escapeHTML("Invalid value for " + p.getLabel() + ": " + invalidValue.getMessage()));
                            return new ModelAndView("/module/reporting/parameters/queryParameterForm");
                        }								
					}
				}
	
                try { StructuredReportParameters.validate(parameterizable,parameterValues); }
                catch (IllegalArgumentException invalidValue) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    request.getSession().removeAttribute("results");
                    request.getSession().setAttribute(WebConstants.OPENMRS_ERROR_ATTR,WebUtil.escapeHTML(invalidValue.getMessage()));
                    return new ModelAndView("/module/reporting/parameters/queryParameterForm");
                }

				// Set parameter values
				evaluationContext.setParameterValues(parameterValues);		
	
				model.addObject("evaluationContext", evaluationContext);
				try { 
					long startTime = System.nanoTime();
					// Evaluate the parameterizable and populate the model
					results = ParameterizableUtil.evaluateParameterizable(parameterizable, evaluationContext);						
                    if (results instanceof IterableSqlDataSet) {
                        results = ((IterableSqlDataSet) results).getPreview(50);
                        model.addObject("previewRowLimit", 50);
                    } else if (results instanceof ReportData) {
                        for (Map.Entry<String, DataSet> dataSet : ((ReportData) results).getDataSets().entrySet()) {
                            if (dataSet.getValue() instanceof IterableSqlDataSet) {
                                dataSet.setValue(((IterableSqlDataSet) dataSet.getValue()).getPreview(50));
                                model.addObject("previewRowLimit", 50);
                            }
                        }
                    }
					//model.addObject("results", results);
					request.getSession().setAttribute("results", results);
					long executionTime = (System.nanoTime() - startTime) / 1000000L;
					model.addObject("executionTime", new Double(executionTime/1000));
					
					// Use the success view if it's given, default view otherwise
					//successView = (!StringUtils.isEmpty(successView)) ? successView : defaultView;
					//successView += "?uuid=" + parameterizable.getUuid() + "&type=" + type + "&format=" + format; 
					model.setViewName("/module/reporting/parameters/queryParameterForm");
					
				} 
				catch (ParameterException e) { 
					log.error("unable to evaluate report: ", e);
					request.getSession().setAttribute(WebConstants.OPENMRS_ERROR_ATTR, "Unable to evaluate report: " + e.getMessage());
					request.getSession().removeAttribute("results");
					return new ModelAndView("/module/reporting/parameters/queryParameterForm");
				}
				catch (MissingDependencyException ex) {
					request.getSession().setAttribute(WebConstants.OPENMRS_ERROR_ATTR, "Missing dependency: " + ex.getMessage());
					request.getSession().removeAttribute("results");
					return new ModelAndView("/module/reporting/parameters/queryParameterForm");
				}
                catch (org.openmrs.module.reporting.evaluation.EvaluationException | IllegalArgumentException invalidEvaluation) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    request.getSession().removeAttribute("results");
                    request.getSession().setAttribute(WebConstants.OPENMRS_ERROR_ATTR,
                        WebUtil.escapeHTML("Unable to evaluate report: " + invalidEvaluation.getMessage()));
                    return new ModelAndView("/module/reporting/parameters/queryParameterForm");
                }

			}		
			
			log.debug("Returning model with view " + model.getViewName() + " and map " + model.getModelMap());
			return model;
		}
	}
	
	/**
	 * Retrieves/creates a form backing object.
	 */
	@ModelAttribute("parameterizable")
	public Parameterizable formBackingObject(
			@RequestParam(value = "uuid", required=false) String uuid,
			@RequestParam(value = "type", required=false) Class<Parameterizable> type) {
		
		if (type == null || uuid == null)
			return null;
		else
			return ParameterizableUtil.getParameterizable(uuid, type);

	}
	
}
