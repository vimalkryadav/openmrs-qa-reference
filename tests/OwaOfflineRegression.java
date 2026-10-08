import java.io.File;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.io.FileUtils;
import org.openmrs.module.owa.impl.DefaultAppManager;
import org.openmrs.module.owa.web.controller.OwaRestController;

/** Exercises the installed manager without a database and rejects every remote request before reading it. */
public class OwaOfflineRegression {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("owa-local-regression-");
        try {
            DefaultAppManager manager = new DefaultAppManager() {
                @Override public String getAppFolderPath() { return directory.toString(); }
                @Override public String getAppBaseUrl() { return "/owa"; }
            };
            File fixtures = new File(args[0]);
            manager.installApp(new File(fixtures, "valid.zip"), "valid.zip", "/openmrs");
            if (manager.getApps().size() != 1) throw new AssertionError("Local installation missing");
            manager.installApp(new File(fixtures, "update.zip"), "update.zip", "/openmrs");
            Path app = directory.resolve("qa-offline-app");
            if (Files.exists(app.resolve("obsolete.txt"))) throw new AssertionError("Replacement retained old files");
            for (String invalid : new String[]{"invalid.zip", "empty.zip", "missing-manifest.zip", "missing-launch.zip", "bad-manifest.zip", "traversal.zip", "symlink.zip"}) {
                try { manager.installApp(new File(fixtures, invalid), invalid, "/openmrs"); throw new AssertionError("Accepted " + invalid); }
                catch (IOException expected) { }
                if (!Files.readString(app.resolve("pages/index.html")).contains("v2")) throw new AssertionError("Invalid archive damaged existing app");
                try (java.util.stream.Stream<Path> entries = Files.list(directory)) {
                    if (entries.count() != 1) throw new AssertionError("Staging data leaked");
                }
            }
            if (!manager.deleteApp("QA Offline Packaged App") || !manager.getApps().isEmpty()) throw new AssertionError("Deletion failed");
            final int[] errors = {0};
            HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(), new Class<?>[]{HttpServletRequest.class},
                (proxy, method, arguments) -> { throw new AssertionError("Remote request accessed: " + method); });
            HttpServletResponse response = (HttpServletResponse) Proxy.newProxyInstance(HttpServletResponse.class.getClassLoader(), new Class<?>[]{HttpServletResponse.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("sendError") && arguments[0].equals(400)) { errors[0]++; return null; }
                    throw new AssertionError("Unexpected response method " + method);
                });
            new OwaRestController().install(null, request, response);
            if (errors[0] != 1) throw new AssertionError("Remote request not rejected");
            System.out.println("PASS: local install, replacement, seven invalid archives preserve app, no staging leaks, deletion, remote request sentinel");
        } finally { FileUtils.deleteDirectory(directory.toFile()); }
    }
}
