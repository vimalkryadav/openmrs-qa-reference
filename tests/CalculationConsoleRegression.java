import java.util.*;
import org.openmrs.calculation.result.ListResult;
import org.openmrs.calculation.result.SimpleResult;
import org.openmrs.calculation.web.controller.CalculationRegistrationController;
import org.openmrs.module.reporting.calculation.ReportingCalculationUtil;
import org.openmrs.module.reporting.data.patient.definition.PatientIdentifierDataDefinition;
import org.openmrs.module.reporting.evaluation.EvaluationContext;

/** Console guards execute before database access; adapter inputs are copied, not retained. */
public class CalculationConsoleRegression {
    public static void main(String[] args) {
        org.openmrs.module.reporting.common.MessageUtil.setMessageSource(new org.springframework.context.support.StaticMessageSource());
        org.openmrs.api.context.Context.setUserContext(new org.openmrs.api.context.UserContext(null));
        int checked=0;
        for (String count : Arrays.asList("-1","1001","bad","2.5")) {
            try { CalculationRegistrationController.selectedPatients(null,count); throw new AssertionError("Invalid cohort count accepted"); }
            catch (IllegalArgumentException expected) { if (!expected.getMessage().contains("0 to 1000")) throw expected; checked++; }
        }
        for (String ids : Arrays.asList("abc","0","-1","1,,2")) {
            try { CalculationRegistrationController.selectedPatients(ids,null); throw new AssertionError("Invalid patient ids accepted"); }
            catch (IllegalArgumentException expected) { if (!expected.getMessage().contains("positive whole numbers")) throw expected; checked++; }
        }
        if (CalculationRegistrationController.selectedPatients(null,null)!=null) throw new AssertionError("Blank form evaluated"); checked++;
        ListResult values=new ListResult(); values.add(new SimpleResult("M",null,null)); values.add(new SimpleResult(42,null,null));
        if (!"[M, 42]".equals(CalculationRegistrationController.display(values))) throw new AssertionError("Unreadable ListResult"); checked++;
        for (String raw : Arrays.asList("31/02/2026","29/09/2026junk","2026-02-31")) {
            try { CalculationRegistrationController.strictDate(raw); throw new AssertionError("Invalid date accepted"); }
            catch (IllegalArgumentException expected) { checked++; }
        }
        if (CalculationRegistrationController.strictDate("2026-09-29")==null) throw new AssertionError("ISO date rejected"); checked++;
        PatientIdentifierDataDefinition definition=new PatientIdentifierDataDefinition();
        if (!ReportingCalculationUtil.getParameterDefinitionSet(definition).isEmpty()) throw new AssertionError("Fixed fields became runtime parameters"); checked++;
        org.openmrs.module.reporting.data.patient.definition.StaticValuePatientDataDefinition typed=new org.openmrs.module.reporting.data.patient.definition.StaticValuePatientDataDefinition();
        org.openmrs.module.reporting.evaluation.parameter.Parameter required=new org.openmrs.module.reporting.evaluation.parameter.Parameter("staticValue","Value",Integer.class);
        required.setRequired(true); typed.addParameter(required);
        if (!ReportingCalculationUtil.getParameterDefinitionSet(typed).getParameterByKey("staticValue").isRequired()) throw new AssertionError("Saved required flag ignored"); checked++;
        required.setDefaultValue(42);
        if (ReportingCalculationUtil.getParameterDefinitionSet(typed).getParameterByKey("staticValue").isRequired()) throw new AssertionError("Saved default ignored"); checked++;
        try {
            org.openmrs.module.reporting.calculation.PatientDataCalculation age=new org.openmrs.module.reporting.calculation.PatientDataCalculationProvider().getCalculation("org.openmrs.module.reporting.data.person.definition.AgeDataDefinition",null);
            if (age.getParameterDefinitionSet().getParameterByKey("effectiveDate").isRequired()) throw new AssertionError("Optional class property became required"); checked++;
        } catch (org.openmrs.calculation.InvalidCalculationException invalid) { throw new AssertionError(invalid); }
        Map<String,Object> parameters=new HashMap<String,Object>(); parameters.put("value",42);
        EvaluationContext context=ReportingCalculationUtil.getEvaluationContextForCalculation(Arrays.asList(1),parameters,null);
        parameters.put("value",7);
        if (!Integer.valueOf(42).equals(context.getParameterValue("value"))) throw new AssertionError("Runtime context retained mutable caller values"); checked++;
        System.out.println("PASS "+checked+" Calculation console and Reporting adapter assertions");
    }
}
