import java.lang.reflect.Proxy;
import java.text.SimpleDateFormat;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import org.openmrs.module.reporting.web.util.ReportInputValidation;

/** Pure validation checks using the exact compiled runtime helpers, without a DB. */
public class ReportingQaRegression {
    private static int checks;
    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
    }
    private static void rejects(Runnable action, String label) {
        try { action.run(); throw new AssertionError(label); }
        catch (IllegalArgumentException expected) { checks++; }
    }
    public static void main(String[] args) {
        String literal = "Women & Children's \"Clinic\" <b>report</b> &amp;";
        HttpServletRequest original = (HttpServletRequest) Proxy.newProxyInstance(
                ReportingQaRegression.class.getClassLoader(), new Class[]{HttpServletRequest.class},
                (proxy, method, values) -> method.getName().equals("getParameter") ? literal : null);
        HttpServletRequest wrapped = new HttpServletRequestWrapper(original) {
            public String getParameter(String name) { return "ENCODED"; }
        };
        check(ReportInputValidation.metadataText(wrapped, "name").equals(literal), "Raw text including intentional entity survives wrapper");
        check(ReportInputValidation.metadataName("  Name  ").equals("Name"), "Trim name");
        check(ReportInputValidation.metadataName(new String(new char[255]).replace('\0','x')).length()==255,"255 character name");
        rejects(() -> ReportInputValidation.metadataName("   "), "Whitespace name");
        rejects(() -> ReportInputValidation.metadataName(new String(new char[256]).replace('\0','x')), "256 character name");
        SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy");
        check(ReportInputValidation.parseDate("29/02/2024", format) != null, "Leap day");
        rejects(() -> ReportInputValidation.parseDate("31/02/2027",format),"Impossible day");
        rejects(() -> ReportInputValidation.parseDate("29/02/2026",format),"Nonleap day");
        rejects(() -> ReportInputValidation.parseDate("abc",format),"Text date");
        rejects(() -> ReportInputValidation.parseDate("01/01/2027 extra",format),"Trailing date junk");
        check(ReportInputValidation.scheduleError("0 30 7 ? * SUN,WED") == null,"Valid weekly cron");
        check(ReportInputValidation.scheduleError("0 0 12 1 10 ? 2027") == null,"Valid once cron");
        check(ReportInputValidation.scheduleError("") != null,"Empty cron");
        check(ReportInputValidation.scheduleError("0 0 12 NaN NaN ? NaN") != null,"NaN cron");
        check(ReportInputValidation.scheduleError("0 0 12 * * ? " + new String(new char[100]).replace('\0','x')) != null,"Long cron");
        check(ReportInputValidation.scheduleError("0 0 12 * * ? junk extra") != null,"Trailing cron junk");
        System.out.println("PASS Reporting QA regression: " + checks + " checks");
    }
}
