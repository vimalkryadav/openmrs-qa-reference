/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.calculation.web.controller;

import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Cohort;
import org.openmrs.api.context.Context;
import org.openmrs.calculation.CalculationRegistration;
import org.openmrs.calculation.api.CalculationRegistrationService;
import org.openmrs.calculation.patient.PatientCalculation;
import org.openmrs.calculation.patient.PatientCalculationService;
import org.openmrs.calculation.result.CalculationResultMap;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Primary Controller for listing, deleting, testing Calculation Registrations
 */
@Controller
public class CalculationRegistrationController {

	/** Logger for this class and subclasses */
	protected final Log log = LogFactory.getLog(getClass());

	/**
	 * Shows the page to list token registrations
	 */
	@RequestMapping(value = {"/module/calculation/calculationRegistrations", "/module/calculation/calculationRegistrations.list", "/module/calculation/calculationRegistrations.form"})
	public void listCalculationRegistrations(Model model) {
		CalculationRegistrationService calculationRegistrationService = Context.getService(CalculationRegistrationService.class);
		model.addAttribute("calculationRegistrations", calculationRegistrationService.getAllCalculationRegistrations());
	}

	/**
	 * Page which tests patient calculations
	 */
    @RequestMapping(value = {"/module/calculation/patientCalculationTest", "/module/calculation/patientCalculationTest.form"})
    public String patientCalculationTest(Model model,
            @RequestParam(value="id") Integer id,
            @RequestParam(value="patientIds", required=false) String patientIds,
            @RequestParam(value="randomIds", required=false) String randomIds,
            javax.servlet.http.HttpServletRequest request, javax.servlet.http.HttpServletResponse response) {
        model.addAttribute("id", id);
        model.addAttribute("patientIds", patientIds);
        model.addAttribute("randomIds", randomIds);
        try {
            CalculationRegistrationService service = Context.getService(CalculationRegistrationService.class);
            CalculationRegistration registration = service.getCalculationRegistration(id);
            if (registration == null) { response.setStatus(404); throw new IllegalArgumentException("Calculation registration not found"); }
            model.addAttribute("calculationRegistration", registration);
            PatientCalculation calculation = service.getCalculation(registration.getToken(), PatientCalculation.class);
            if (calculation == null) throw new IllegalArgumentException("Calculation is unavailable");
            java.util.List<java.util.Map<String,Object>> parameters = new java.util.ArrayList<java.util.Map<String,Object>>();
            java.util.Map<String,Object> values = new java.util.HashMap<String,Object>();
            org.openmrs.calculation.parameter.ParameterDefinitionSet definitions = calculation.getParameterDefinitionSet();
            if (definitions != null) for (org.openmrs.calculation.parameter.ParameterDefinition definition : definitions) {
                String datatype = definition.getDatatype();
                int generic = datatype.indexOf('<');
                String collection = generic < 0 ? "" : datatype.substring(0,generic);
                String type = generic < 0 ? datatype : datatype.substring(generic+1,datatype.lastIndexOf('>'));
                java.util.Map<String,Object> field = new java.util.LinkedHashMap<String,Object>();
                field.put("key",definition.getKey()); field.put("label",StringUtils.isBlank(definition.getName())?definition.getKey():definition.getName());
                field.put("type",type); field.put("collection",collection); field.put("required",definition.isRequired());
                Object shown=request.getParameter("parameter."+definition.getKey());
                if (shown==null) {
                    // Reporting depends on Calculation, so its optional defaults cannot create a reverse module dependency.
                    try {
                        Object data=calculation.getClass().getMethod("getDataDefinition").invoke(calculation);
                        Object saved=data.getClass().getMethod("getParameter",String.class).invoke(data,definition.getKey());
                        if (saved!=null) shown=saved.getClass().getMethod("getDefaultValue").invoke(saved);
                    } catch (NoSuchMethodException noSavedDefinition) { /* Other installed providers have no saved defaults. */ }
                }
                field.put("value",shown); parameters.add(field);
            }
            model.addAttribute("parameters",parameters);
            java.util.SortedSet<Integer> members;
            try { members=selectedPatients(patientIds,randomIds); }
            catch (IllegalArgumentException invalid) { model.addAttribute("invalidField",StringUtils.isNotBlank(patientIds)?"patientIds":"randomIds"); throw invalid; }
            if (members == null) return "/module/calculation/patientCalculationTest";
            for (java.util.Map<String,Object> field : parameters) {
                String key=(String)field.get("key"); String collection=(String)field.get("collection");
                try {
                    String type=(String)field.get("type"); String raw=request.getParameter("parameter."+key);
                    Object value;
                    if ("java.util.Date".equals(type) && collection.isEmpty()) value=strictDate(raw);
                    else {
                        if ("java.lang.Boolean".equals(type)) {
                            String[] submitted=request.getParameterValues("parameter."+key);
                            if (submitted!=null) for (String entry : submitted) if (StringUtils.isNotBlank(entry) && !"t".equals(entry) && !"f".equals(entry))
                                throw new IllegalArgumentException("Select True or False");
                        }
                        value=org.openmrs.module.htmlwidgets.web.WidgetUtil.getFromRequest(request,"parameter."+key,
                            Context.loadClass(type),collection.isEmpty()?null:Context.loadClass(collection).asSubclass(java.util.Collection.class));
                    }
                    if (Boolean.TRUE.equals(field.get("required")) && (value==null || "".equals(value) || (value instanceof java.util.Collection && ((java.util.Collection<?>)value).isEmpty())))
                        throw new IllegalArgumentException("A value is required");
                    if (value instanceof java.util.Collection && ((java.util.Collection<?>)value).contains(null)) throw new IllegalArgumentException("The selected value does not exist");
                    if (value!=null) { values.put(key,value); field.put("value",value); }
                } catch (Exception invalid) { model.addAttribute("invalidField",key); throw new IllegalArgumentException("Invalid value for "+field.get("label")+": "+invalid.getMessage(),invalid); }
            }
            long startTime=System.currentTimeMillis();
            CalculationResultMap result=Context.getService(PatientCalculationService.class).evaluate(members,calculation,values,null);
            model.addAttribute("evaluationTime",System.currentTimeMillis()-startTime);
            model.addAttribute("evaluated",true);
            java.util.List<java.util.Map<String,Object>> rows=new java.util.ArrayList<java.util.Map<String,Object>>();
            for (Integer patientId : new java.util.TreeSet<Integer>(result.keySet())) {
                org.openmrs.calculation.result.CalculationResult value=result.get(patientId);
                java.util.Map<String,Object> row=new java.util.LinkedHashMap<String,Object>();
                row.put("patientId",patientId); row.put("result",display(value));
                row.put("resultType",value==null?"SimpleResult":value.getClass().getSimpleName()); rows.add(row);
            }
            model.addAttribute("resultRows",rows);
        } catch (Exception invalid) {
            model.addAttribute("error",StringUtils.isBlank(invalid.getMessage())?"Calculation could not be evaluated":invalid.getMessage());
        }
        return "/module/calculation/patientCalculationTest";
    }

    public static java.util.Date strictDate(String raw) {
        if (StringUtils.isBlank(raw)) return null;
        java.text.DateFormat format=raw.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")?new java.text.SimpleDateFormat("yyyy-MM-dd"):(java.text.DateFormat)Context.getDateFormat().clone();
        format.setLenient(false);
        java.text.ParsePosition position=new java.text.ParsePosition(0);
        java.util.Date value=format.parse(raw,position);
        if (value==null || position.getIndex()!=raw.length()) throw new IllegalArgumentException("Use a valid date in the displayed date format");
        return value;
    }

    public static java.util.SortedSet<Integer> selectedPatients(String patientIds, String firstCount) {
        java.util.SortedSet<Integer> members=new java.util.TreeSet<Integer>();
        if (StringUtils.isNotBlank(patientIds)) {
            String[] tokens=patientIds.split(",",-1);
            if (tokens.length>1000) throw new IllegalArgumentException("At most 1000 patient ids may be tested at once");
            for (String token : tokens) {
                try { int value=Integer.parseInt(token.trim()); if (value<=0) throw new NumberFormatException(); members.add(value); }
                catch (NumberFormatException invalid) { throw new IllegalArgumentException("Patient ids must be positive whole numbers separated by commas"); }
            }
            String ids=StringUtils.join(members,",");
            java.util.Set<Integer> existing=new java.util.HashSet<Integer>();
            for (List<Object> row : Context.getAdministrationService().executeSQL("select p.patient_id from patient p inner join person pe on pe.person_id=p.patient_id where p.voided=0 and pe.voided=0 and p.patient_id in ("+ids+")",true)) existing.add(((Number)row.get(0)).intValue());
            java.util.SortedSet<Integer> missing=new java.util.TreeSet<Integer>(members); missing.removeAll(existing);
            if (!missing.isEmpty()) throw new IllegalArgumentException("Unknown or voided patient ids: "+StringUtils.join(missing,", "));
        } else if (StringUtils.isNotBlank(firstCount)) {
            int count;
            try { count=Integer.parseInt(firstCount.trim()); }
            catch (NumberFormatException invalid) { throw new IllegalArgumentException("Patient count must be a whole number from 0 to 1000"); }
            if (count<0 || count>1000) throw new IllegalArgumentException("Patient count must be a whole number from 0 to 1000");
            for (List<Object> row : Context.getAdministrationService().executeSQL("select p.patient_id from patient p inner join person pe on pe.person_id=p.patient_id where p.voided=0 and pe.voided=0 order by p.patient_id limit "+count,true)) members.add(((Number)row.get(0)).intValue());
        } else return null;
        return members;
    }

    public static String display(Object value) {
        if (value==null) return "";
        if (value instanceof org.openmrs.calculation.result.ListResult) return display(((org.openmrs.calculation.result.ListResult)value).getValues());
        if (value instanceof org.openmrs.calculation.result.CalculationResult) return display(((org.openmrs.calculation.result.CalculationResult)value).getValue());
        if (value instanceof java.util.Collection) {
            java.util.List<String> values=new java.util.ArrayList<String>();
            for (Object item : (java.util.Collection<?>)value) values.add(display(item));
            return "["+StringUtils.join(values,", ")+"]";
        }
        return String.valueOf(value);
    }

}
