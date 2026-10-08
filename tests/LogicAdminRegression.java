import java.lang.reflect.Proxy;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import org.openmrs.logic.LogicCriteria;
import org.openmrs.logic.impl.LogicCriteriaImpl;
import org.openmrs.logic.web.controller.LogicWebInput;

/** Runtime-independent regression coverage for literal rule source and alias criteria. */
public class LogicAdminRegression {
    private static int checks;
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
    public static void main(String[] args) {
        String source = "Map<String,Object> parameters; return new Result(\"A & B <tag> &amp;\");";
        HttpServletRequest original = (HttpServletRequest) Proxy.newProxyInstance(
                LogicAdminRegression.class.getClassLoader(), new Class[]{HttpServletRequest.class},
                (proxy, method, values) -> method.getName().equals("getParameter") ? source : null);
        HttpServletRequest wrapper = new HttpServletRequestWrapper(original) {
            public String getParameter(String name) { return "ENCODED"; }
        };
        check(LogicWebInput.text(wrapper,"ruleContent").equals(source), "Rule source is lossless before HTML output escaping");
        LogicCriteria originalCriteria = new LogicCriteriaImpl("Alias").gt(3).last();
        String before = originalCriteria.toString();
        LogicCriteria resolved = LogicCriteriaImpl.forDataSource(originalCriteria,"CD4 COUNT");
        check(originalCriteria.getRootToken().equals("Alias"), "Alias cache key stays unchanged");
        check(originalCriteria.toString().equals(before), "Caller expression stays unchanged");
        check(resolved.getRootToken().equals("CD4 COUNT"), "Resolve provider key");
        check(resolved.toString().equals(new LogicCriteriaImpl("CD4 COUNT").gt(3).last().toString()), "Preserve comparison and last transform exactly");
        LogicCriteria nested = new LogicCriteriaImpl("Alias").last().gt(3).not();
        LogicCriteria expected = new LogicCriteriaImpl("gender").last().gt(3).not();
        check(LogicCriteriaImpl.forDataSource(nested,"gender").toString().equals(expected.toString()), "Preserve nested unary and inherited transforms without duplicating them");
        LogicCriteria literal = new LogicCriteriaImpl("Alias").equalTo("Alias");
        check(LogicCriteriaImpl.forDataSource(literal,"gender").toString().equals(new LogicCriteriaImpl("gender").equalTo("Alias").toString()), "Do not rewrite comparison literals matching alias text");
        org.openmrs.logic.rule.definition.RuleDefinition rule = new org.openmrs.logic.rule.definition.RuleDefinition();
        rule.setName("Rule"); rule.setLanguage("Groovy"); rule.setRuleContent("return new Result(1);");
        org.springframework.validation.BeanPropertyBindingResult ruleErrors = new org.springframework.validation.BeanPropertyBindingResult(rule,"rule");
        new org.openmrs.logic.rule.definition.RuleDefinitionValidator().validate(rule,ruleErrors);
        check(!ruleErrors.hasErrors(), "Ordinary rule validates");
        rule.setName(new String(new char[256]).replace('\0','x'));
        rule.setDescription(new String(new char[1001]).replace('\0','x'));
        rule.setRuleContent(new String(new char[2049]).replace('\0','x'));
        ruleErrors = new org.springframework.validation.BeanPropertyBindingResult(rule,"rule");
        new org.openmrs.logic.rule.definition.RuleDefinitionValidator().validate(rule,ruleErrors);
        check(ruleErrors.hasFieldErrors("name"),"Rule name bound");
        check(ruleErrors.hasFieldErrors("description"),"Rule description bound");
        check(ruleErrors.hasFieldErrors("ruleContent"),"Rule source bound");
        org.openmrs.logic.token.TokenRegistration token = new org.openmrs.logic.token.TokenRegistration();
        token.setToken(" "); token.setProviderClassName("Provider"); token.setProviderToken("Alias");
        token.setConfiguration(new String(new char[2001]).replace('\0','x'));
        org.springframework.validation.BeanPropertyBindingResult tokenErrors = new org.springframework.validation.BeanPropertyBindingResult(token,"tokenRegistration");
        new org.openmrs.logic.token.TokenRegistrationValidator().validate(token,tokenErrors);
        check(tokenErrors.hasFieldErrors("token"),"Whitespace token is required");
        check(tokenErrors.hasFieldErrors("configuration"),"Token configuration bound");
        System.out.println("PASS Logic Admin regression: " + checks + " checks");
    }
}
