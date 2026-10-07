import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;

import org.openmrs.module.initializer.api.InitializerService;
import org.openmrs.module.patientdocuments.renderer.PatientIdStickerXmlReportRenderer;
import org.openmrs.module.reporting.common.ResultSetIterator;
import org.openmrs.module.reporting.common.SqlRunner;
import org.openmrs.module.reporting.dataset.IterableSqlDataSet;
import org.openmrs.module.reporting.dataset.SimpleDataSet;
import org.openmrs.module.reporting.dataset.definition.IterableSqlDataSetDefinition;
import org.openmrs.module.reporting.report.Report;
import org.openmrs.module.reporting.report.ReportData;
import org.openmrs.module.reporting.report.ReportDesign;
import org.openmrs.module.reporting.report.definition.ReportDefinition;
import org.openmrs.module.reporting.report.processor.LoggingReportProcessor;
import org.openmrs.module.reporting.report.renderer.TextTemplateRenderer;
import org.openmrs.module.reporting.web.reports.renderers.TextTemplateFormController;

/** Standalone behavioral regressions against compiled overrides; no server or clinical DB required. */
public class ReferenceBatchRegression {
    private static int checks;

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        return null;
    }

    private static class Jdbc {
        boolean closed, rolledBack, readOnly;
        int statementsClosed, row = -1;
        final int[] values;
        final Connection connection;
        Statement active;

        Jdbc(int... values) {
            this.values = values;
            connection = proxy(Connection.class, (p, m, a) -> {
                switch (m.getName()) {
                    case "getAutoCommit": return false;
                    case "setReadOnly": readOnly = (Boolean) a[0]; return null;
                    case "isClosed": return closed;
                    case "close": closed = true; return null;
                    case "rollback": rolledBack = true; return null;
                    case "createStatement": return statement();
                    default: return defaultValue(m.getReturnType());
                }
            });
        }

        Statement statement() {
            Statement statement = proxy(Statement.class, (p, m, a) -> {
                switch (m.getName()) {
                    case "executeQuery": throw new SQLException("SET does not produce a result set");
                    case "execute":
                        String sql = (String) a[0];
                        if (sql.contains("broken")) throw new SQLException("unknown column broken");
                        return sql.startsWith("SELECT");
                    case "getResultSet": return resultSet();
                    case "getConnection": return connection;
                    case "close": statementsClosed++; return null;
                    default: return defaultValue(m.getReturnType());
                }
            });
            active = statement;
            return statement;
        }

        ResultSet resultSet() {
            Statement owner = active;
            ResultSetMetaData metadata = proxy(ResultSetMetaData.class, (p, m, a) -> {
                if (m.getName().equals("getColumnCount")) return 1;
                if (m.getName().equals("getColumnLabel")) return "qa_value";
                return defaultValue(m.getReturnType());
            });
            return proxy(ResultSet.class, (p, m, a) -> {
                switch (m.getName()) {
                    case "getStatement": return owner;
                    case "getMetaData": return metadata;
                    case "next": return ++row < values.length;
                    case "getObject": return values[row];
                    default: return defaultValue(m.getReturnType());
                }
            });
        }
    }

    private static void iterableSql() {
        Jdbc jdbc = new Jdbc(7, 9);
        ResultSetIterator rows = new SqlRunner(jdbc.connection).executeSqlToIterator(
                "SELECT @proof AS qa_value", Collections.<String, Object>singletonMap("proof", 7));
        check(rows.getColumns().get(0).getName().equals("qa_value"), "metadata before streaming");
        check(jdbc.readOnly && !jdbc.closed, "stream owns an open read-only connection");
        check(jdbc.statementsClosed == 1, "SET statement closed before stream");
        check(rows.hasNext() && rows.hasNext(), "hasNext is idempotent");
        check(rows.next().getColumnValue("qa_value").equals(7), "first scalar");
        check(rows.next().getColumnValue("qa_value").equals(9), "next without hasNext");
        check(!rows.hasNext() && jdbc.closed && jdbc.rolledBack, "exhaustion rolls back and closes");
        try { rows.next(); throw new AssertionError("exhausted iterator returned a row"); }
        catch (NoSuchElementException expected) { checks++; }

        Jdbc empty = new Jdbc();
        ResultSetIterator zero = new SqlRunner(empty.connection).executeSqlToIterator("SELECT 7 WHERE 1=0", null);
        check(zero.getColumns().size() == 1 && !zero.hasNext() && empty.closed, "zero rows retain metadata and close");
        for (String sql : Arrays.asList("SELECT broken", "SET @x=1")) {
            Jdbc bad = new Jdbc();
            try { new SqlRunner(bad.connection).executeSqlToIterator(sql, null); throw new AssertionError("invalid SQL returned null"); }
            catch (IllegalArgumentException expected) {
                check(expected.getCause() != null && bad.closed && bad.rolledBack, "failure carries cause and closes connection");
            }
        }
        Jdbc partial = new Jdbc(7, 9);
        ResultSetIterator stream = new SqlRunner(partial.connection).executeSqlToIterator("SELECT 7", null);
        stream.closeConnection();
        check(partial.closed && partial.rolledBack && !stream.hasNext(), "explicit stream close is safe");
        for (int i = 0; i < 2; i++) {
            try { stream.next(); throw new AssertionError("closed iterator accepted next"); }
            catch (NoSuchElementException expected) { checks++; }
        }
        Jdbc large = new Jdbc(new int[60]);
        ResultSetIterator cursor = new SqlRunner(large.connection).executeSqlToIterator("SELECT 7", null);
        SimpleDataSet preview = new IterableSqlDataSet(null,
                new IterableSqlDataSetDefinition(), cursor).getPreview(50);
        check(preview.getRows().size() == 50 && large.row == 49, "preview stops at exactly50 without reading51");
        check(large.closed && large.rolledBack, "early preview cutoff closes and rolls back");
        check(preview.getRows().size() == 50, "detached preview survives closed cursor");
    }

    private static class PreviewController extends TextTemplateFormController {
        ReportDesign draft(String script) throws Exception {
            return createPreviewDesign(new ReportDefinition(), "owned-design", TextTemplateRenderer.class, script, "");
        }
    }

    private static void preview() throws Exception {
        PreviewController controller = new PreviewController();
        ReportDesign saved = controller.draft("SAVED #qa#");
        ReportDesign draft = controller.draft("UNSAVED #qa#");
        TextTemplateRenderer renderer = new TextTemplateRenderer() {
            @Override public ReportDesign getDesign(String uuid) { throw new AssertionError("preview looked up persistent design"); }
            @Override public Map<String, Object> getBaseReplacementData(ReportData data, ReportDesign design) {
                return Collections.<String, Object>singletonMap("qa", 9);
            }
        };
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        renderer.renderWithDesign(new ReportData(), draft, output);
        check(output.toString("UTF-8").equals("UNSAVED 9"), "preview renders unsaved content");
        check(new String(saved.getResourceByName("template").getContents(), "UTF-8").equals("SAVED #qa#"), "draft has independent resource graph");
        check(draft.getId() == null && draft.getResourceByName("template").getId() == null, "preview objects have no persistence IDs");
    }

    private static void stickers() throws Exception {
        Map<String, String> config = new HashMap<>();
        InitializerService initializer = proxy(InitializerService.class, (p, m, a) -> {
            String value = config.get(a == null ? null : a[0]);
            if (m.getName().equals("getValueFromKey")) return value;
            if (m.getName().equals("getBooleanFromKey")) return value == null ? null : Boolean.valueOf(value);
            return defaultValue(m.getReturnType());
        });
        PatientIdStickerXmlReportRenderer renderer = new PatientIdStickerXmlReportRenderer();
        Field service = renderer.getClass().getDeclaredField("initializerService");
        service.setAccessible(true); service.set(renderer, initializer);
        Method include = renderer.getClass().getDeclaredMethod("shouldIncludeColumn", String.class);
        include.setAccessible(true);
        for (String field : Arrays.asList("identifier", "patientname", "gender", "dob", "age", "fulladdress")) {
            check((Boolean) include.invoke(renderer, "patientdocuments.patientIdSticker.fields." + field), "default identity field " + field);
        }
        check(!(Boolean) include.invoke(renderer, "patientdocuments.patientIdSticker.fields.secondaryIdentifier"), "secondary identifier stays opt-in");
        config.put("report.patientIdSticker.fields.name", "false");
        check(!(Boolean) include.invoke(renderer, "patientdocuments.patientIdSticker.fields.patientname"), "explicit false respected");
        config.put("report.patientIdSticker.fields.gender", "true");
        check((Boolean) include.invoke(renderer, "patientdocuments.patientIdSticker.fields.gender"), "optional explicit true respected");
    }

    public static void main(String[] args) throws Exception {
        iterableSql(); preview(); stickers();
        org.openmrs.Cohort clinical = new org.openmrs.Cohort();
        clinical.setCohortId(321); clinical.setUuid("clinical-owned-test"); clinical.setName("Clinical cohort");
        clinical.addMember(7);
        org.openmrs.module.reporting.cohort.definition.StaticCohortDefinition query = new org.openmrs.module.reporting.cohort.definition.StaticCohortDefinition();
        query.setCohort(clinical); query.setName("Reporting label"); query.setUuid("query-owned-test"); query.setId(22);
        if (!clinical.getName().equals("Clinical cohort") || clinical.getCohortId() != 321 || !clinical.getUuid().equals("clinical-owned-test") || clinical.getSize() != 1) throw new AssertionError("Query metadata mutated cohort");
        if (!query.getName().equals("Reporting label") || !query.getUuid().equals("query-owned-test") || query.getId() != 22) throw new AssertionError("Independent query metadata missing");
        checks += 2;
        org.openmrs.module.reporting.cohort.definition.StaticCohortDefinition legacy = new org.openmrs.module.reporting.cohort.definition.StaticCohortDefinition(clinical);
        if (legacy.isIndependentMetadata() || !legacy.getUuid().equals(clinical.getUuid())) throw new AssertionError("Legacy reference identity changed");
        checks++;
        org.openmrs.module.reporting.report.renderer.TextTemplateRenderer textRenderer = new org.openmrs.module.reporting.report.renderer.TextTemplateRenderer();
        try { textRenderer.renderWithDesign(new ReportData(), new org.openmrs.module.reporting.report.ReportDesign(), new java.io.ByteArrayOutputStream()); throw new AssertionError("Missing template accepted"); }
        catch (org.openmrs.module.reporting.report.renderer.RenderingException expected) { if (!expected.getMessage().contains("A template resource is required")) throw expected; checks++; }
        org.openmrs.module.reporting.report.renderer.CohortDetailReportRenderer detailRenderer = new org.openmrs.module.reporting.report.renderer.CohortDetailReportRenderer();
        org.openmrs.api.context.Context.setUserContext(new org.openmrs.api.context.UserContext(null));
        org.openmrs.module.reporting.report.ReportRequest detailRequest = new org.openmrs.module.reporting.report.ReportRequest();
        detailRequest.setRenderingMode(new org.openmrs.module.reporting.report.renderer.RenderingMode(detailRenderer, "Workbook", "owned:xls", 1));
        if (!detailRenderer.getRenderedContentType(detailRequest).equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) throw new AssertionError("CohortDetail workbook MIME");
        checks++;
        detailRequest.setRenderingMode(new org.openmrs.module.reporting.report.renderer.RenderingMode(detailRenderer, "HTML", "owned:html", 1));
        if (!detailRenderer.getRenderedContentType(detailRequest).equals("text/html")) throw new AssertionError("CohortDetail HTML MIME");
        checks++;
        String raw = "<mapped><string>A &amp; B</string></mapped>";
        String escaped = org.apache.commons.lang.StringEscapeUtils.escapeHtml(raw);
        if (!raw.equals(org.openmrs.module.reporting.propertyeditor.MappedEditor.decodeTransportXml(escaped))) throw new AssertionError("transport decode");
        checks++;
        if (!raw.equals(org.openmrs.module.reporting.propertyeditor.MappedEditor.decodeTransportXml(raw))) throw new AssertionError("raw XML entities changed");
        checks++;
        final ReportDesign excelDesign = new ReportDesign();
        org.openmrs.module.reporting.report.renderer.ExcelTemplateRenderer excelRenderer = new org.openmrs.module.reporting.report.renderer.ExcelTemplateRenderer() {
            @Override public ReportDesign getDesign(String uuid) { return excelDesign; }
        };
        check(excelRenderer.getRenderedContentType(detailRequest).contains("openxmlformats"), "Excel fallback MIME matches XSSFWorkbook");
        org.openmrs.module.reporting.report.ReportDesignResource template = new org.openmrs.module.reporting.report.ReportDesignResource();
        template.setName("template"); template.setContents(new byte[]{'P','K',3,4}); excelDesign.addResource(template);
        check(excelRenderer.getRenderedContentType(detailRequest).contains("openxmlformats"), "ZIP template MIME");
        template.setContents(new byte[]{(byte)0xd0,(byte)0xcf,0x11});
        check(excelRenderer.getRenderedContentType(detailRequest).equals("application/vnd.ms-excel"), "OLE template keeps XLS MIME");
        LoggingReportProcessor processor = new LoggingReportProcessor();
        Report saved = new Report(); saved.setRenderedOutput(new byte[]{1, 2});
        processor.process(saved, new Properties()); checks++;
        Report failed = new Report(); failed.setErrorMessage("Owned failure");
        processor.process(failed, new Properties()); checks++;
        Report live = new Report(); live.setReportData(new ReportData());
        processor.process(live, new Properties()); checks++;
        System.out.println("PASS " + checks + " focused reference assertions");
    }
}
