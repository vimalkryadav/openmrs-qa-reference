import java.lang.reflect.Proxy;
import java.util.Date;
import java.util.concurrent.ScheduledFuture;
import org.openmrs.module.openconceptlab.ImportService;
import org.openmrs.module.openconceptlab.Subscription;
import org.openmrs.module.openconceptlab.client.OclClient;
import org.openmrs.module.openconceptlab.importer.Importer;
import org.openmrs.module.openconceptlab.scheduler.UpdateScheduler;
import org.openmrs.module.openconceptlab.web.rest.resources.ImportResource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** Network sentinels fail if either scheduled or manually forced remote work reaches a client. */
public class OclOfflineRegression {
    public static void main(String[] args) {
        Importer importer = new Importer();
        importer.setOclClient(new OclClient("/tmp") {
            @Override public OclResponse fetchOclConcepts(String url, String token) {
                throw new AssertionError("Remote HTTP client invoked");
            }
            @Override public OclResponse fetchOclConcepts(String url, String token, String version) {
                throw new AssertionError("Incremental HTTP client invoked");
            }
            @Override public OclResponse fetchSnapshotUpdates(String url, String token, Date date) {
                throw new AssertionError("Snapshot HTTP client invoked");
            }
        });
        importer.setImportService((ImportService) Proxy.newProxyInstance(
            ImportService.class.getClassLoader(), new Class<?>[] {ImportService.class},
            (proxy, method, arguments) -> { throw new AssertionError("Import row or subscription accessed: " + method); }));
        try { importer.runTask(); throw new AssertionError("Remote task accepted"); }
        catch (IllegalStateException expected) {
            if (!expected.getMessage().contains("Remote OCL imports are disabled")) throw expected;
        }
        try { new ImportResource().save(new org.openmrs.module.openconceptlab.Import()); throw new AssertionError("REST accepted remote import"); }
        catch (org.openmrs.module.webservices.rest.web.response.IllegalRequestException expected) {
            if (!expected.getMessage().contains("Remote OCL imports are disabled")) throw expected;
        }
        final int[] saved = {0};
        UpdateScheduler scheduler = new UpdateScheduler();
        scheduler.setImportService((ImportService) Proxy.newProxyInstance(
            ImportService.class.getClassLoader(), new Class<?>[] {ImportService.class},
            (proxy, method, arguments) -> {
                if (method.getName().equals("saveSubscription")) { saved[0]++; return null; }
                throw new AssertionError("Unexpected database operation: " + method);
            }));
        scheduler.setScheduler(new ThreadPoolTaskScheduler() {
            @Override public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Date start, long period) {
                throw new AssertionError("Remote scheduled task armed");
            }
        });
        Subscription sub = new Subscription(); sub.setDays(1); sub.setHours(0); sub.setMinutes(0);
        scheduler.schedule(sub);
        if (saved[0] != 1) throw new AssertionError("Local settings not saved");
        System.out.println("PASS: REST guard, direct importer network sentinel, scheduler sentinel, local settings persistence");
    }
}
