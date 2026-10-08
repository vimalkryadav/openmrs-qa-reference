package org.openmrs.logic.web.controller;

import java.util.Collections;
import java.util.List;

import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.logic.LogicException;
import org.openmrs.logic.LogicService;
import org.openmrs.logic.result.Result;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class LogicFormController {
	
	/**
	 * Provides auto-complete functionality via JQuery/AJAX the role field
	 * 
	 * @param token The full or partial logic rule token
	 * @param model The ModelMap to be used by view to render page
	 */
    @RequestMapping(value={"/module/logic/tokens", "/module/logic/tokens.form"}, produces="text/plain;charset=UTF-8")
    @ResponseBody
    public String tokenAutoComplete(@RequestParam("q") String token) {
        if (!Context.hasPrivilege("View Administration Functions")) return "";
        List<String> tokens = Context.getLogicService().getTokens(token);
        Collections.sort(tokens);
        return String.join("\n", tokens.subList(0, Math.min(100, tokens.size())));
    }

	/**
	 * Place holder for the logic tester form
	 * 
	 * @param model The ModelMap to be used by view to render page
	 */
	@RequestMapping(value = {"/module/logic/logic", "/module/logic/logic.form"}, method = RequestMethod.GET)
	public String showTestPage(@RequestParam(required = false, value = "patientId") Integer patientId,
	                         @RequestParam(required = false, value = "token") String token,
	                         ModelMap modelMap) {
		modelMap.addAttribute("authenticatedUser", Context.getAuthenticatedUser());
		modelMap.addAttribute("patientId", patientId == null ? 0 : patientId.intValue());
		if (token != null)
			modelMap.addAttribute("token", '"' + token + '"');
		
		if (patientId != null && patientId.intValue() > 0) {
			Patient patient = Context.getPatientService().getPatient(patientId);
			modelMap.addAttribute("patient", patient);
		}
        return "/module/logic/logic";
	}
	
	/**
	 * Runs the logic test using the LogicService
	 * 
	 * @param patientId The ID of the patient
	 * @param logicRule The logic rule token
	 * @param modelMap The ModelMap to be used by view to render page
	 * @throws Exception
	 */
	@RequestMapping({"/module/logic/run", "/module/logic/run.form"})
	public String runTest(@RequestParam(required = false, value = "patientId") Integer patientId,
	                    @RequestParam(required = false, value = "patientIdentifier") String patientIdentifier,
	                    @RequestParam(required = false, value = "patientName") String patientName,
	                    @RequestParam(value="logicRule", required=false) String logicRule, ModelMap modelMap) throws Exception {
		
		if (patientId != null && patientId > 0 && logicRule != null && logicRule.length() > 0) {
			try {
				Patient patient = Context.getPatientService().getPatient(patientId);
				
				LogicService logicService = Context.getLogicService();
				
				if (patient == null) throw new LogicException("Patient not found");
                org.openmrs.logic.LogicCriteria criteria = logicService.parse(logicRule);
                if (criteria == null) throw new LogicException("Invalid Logic Rule");
                Result result = logicService.eval(patient.getPatientId(), criteria); // CHICA-1151 pass in patientId instead of patient
				
				modelMap.addAttribute("patient", patient);
				modelMap.addAttribute("logicRule", logicRule);
				modelMap.addAttribute("result", result);
			}
			catch (LogicException e) {
				modelMap.addAttribute("error", "Invalid Logic Rule.");
			}
			catch (Exception e) {
				org.apache.commons.logging.LogFactory.getLog(getClass()).warn("Logic expression evaluation failed", e);
                modelMap.addAttribute("error", "Unable to evaluate this expression. Check the token or rule definition.");
			}
			
			modelMap.addAttribute("patientId", patientId);
			modelMap.addAttribute("patientIdentifier", patientIdentifier);
			modelMap.addAttribute("patientName", patientName);
			
		} else {
			modelMap.addAttribute("error", "Invalid parameters");
		}
        return "/module/logic/run";
	}
	
}
