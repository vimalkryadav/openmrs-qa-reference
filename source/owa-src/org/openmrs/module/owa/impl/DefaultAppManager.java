/*
 * Copyright (c) 2004-2014, University of Oslo
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * Neither the name of the HISP project nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.openmrs.module.owa.impl;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.ObjectMapper;
import org.openmrs.GlobalProperty;
import org.openmrs.api.context.Context;
import org.openmrs.module.Module;
import org.openmrs.module.ModuleUtil;
import org.openmrs.module.ModuleFactory;
import org.openmrs.module.owa.App;
import org.openmrs.module.owa.AppRequirements;
import org.openmrs.module.owa.AppRequiredModule;
import org.openmrs.module.owa.AppManager;
import org.openmrs.module.owa.OwaListener;
import org.openmrs.util.OpenmrsConstants;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.lang.StringBuilder;

public class DefaultAppManager implements AppManager {

	private static final Log log = LogFactory.getLog(DefaultAppManager.class);

	@Autowired(required = false)
	private List<OwaListener> owaListeners;

	/**
	 * In-memory singleton list holding state for apps.
	 */
	private List<App> apps = new ArrayList();

	private void init() {
		reloadApps();
	}

	public void setOwaListeners(List<OwaListener> owaListeners) {
		this.owaListeners = owaListeners;
	}

	@Override
	public List<App> getApps() {
		String baseUrl = getAppBaseUrl();

		for (App app : apps) {
			app.setBaseUrl(baseUrl);
		}

		return apps;
	}


    @Override
    public void installApp(File file, String fileName, String rootPath) throws IOException {
        if (file.length() > 32L * 1024 * 1024) throw new IOException("OWA archive exceeds 32 MiB");
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        App app;
        java.util.Set<String> names = new java.util.HashSet<>();
        try (ZipFile zip = new ZipFile(file)) {
            long total = 0;
            Enumeration<? extends ZipArchiveEntry> entries = zip.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                String name = archivePath(entry.getName());
                if (!names.add(name) || entry.isUnixSymlink() || entry.getGeneralPurposeBit().usesEncryption()) {
                    throw new IOException("Duplicate, symbolic-link, or encrypted archive entry");
                }
                if (names.size() > 4096 || entry.getSize() < 0 || (total += entry.getSize()) > 128L * 1024 * 1024) {
                    throw new IOException("OWA archive exceeds 4096 files or 128 MiB expanded");
                }
            }
            ZipArchiveEntry manifest = zip.getEntry("manifest.webapp");
            if (manifest == null) throw new IOException("Manifest file could not be found in app");
            if (manifest.getSize() > 1024 * 1024) throw new IOException("manifest.webapp exceeds 1 MiB");
            try (InputStream in = zip.getInputStream(manifest)) { app = mapper.readValue(in, App.class); }
            if (app == null || StringUtils.isBlank(app.getName())) throw new IOException("Manifest name is required");
            String launch = archivePath((StringUtils.isBlank(app.getLaunchPath()) ? "index.html" : app.getLaunchPath()).replaceFirst("^/", ""));
            ZipArchiveEntry launchEntry = zip.getEntry(launch);
            if (launchEntry == null || launchEntry.isDirectory()) throw new IOException("The manifest launch_path must name a file in the archive");
            app.setLaunchPath(launch);
        }
        String deployedName = StringUtils.isNotBlank(app.getDeployedName()) ? app.getDeployedName()
                : fileName.substring(0, fileName.lastIndexOf('.'));
        if (!deployedName.matches("[A-Za-z0-9][A-Za-z0-9_. -]{0,127}")) throw new IOException("Invalid app deployment folder");
        File base = new File(getAppFolderPath());
        java.nio.file.Path stage = java.nio.file.Files.createTempDirectory(base.toPath(), ".owa-stage-");
        java.nio.file.Path destination = new File(base, deployedName).toPath();
        java.nio.file.Path backup = null;
        try {
            unzip(file, stage.toFile());
            app.setBaseUrl(getAppBaseUrl());
            app.setFolderName(deployedName);
            if (app.getActivities() != null && app.getActivities().getOpenmrs() != null
                    && "*".equals(app.getActivities().getOpenmrs().getHref())) {
                app.getActivities().getOpenmrs().setHref(rootPath);
            }
            mapper.writeValue(stage.resolve("manifest.webapp").toFile(), app);
            if (java.nio.file.Files.exists(destination)) {
                backup = base.toPath().resolve(".owa-backup-" + java.util.UUID.randomUUID());
                java.nio.file.Files.move(destination, backup);
            }
            try { java.nio.file.Files.move(stage, destination); }
            catch (IOException failure) {
                if (backup != null) java.nio.file.Files.move(backup, destination);
                throw failure;
            }
            for (App previous : new ArrayList<App>(getApps())) {
                if (app.getName().equals(previous.getName()) && !deployedName.equals(previous.getFolderName())) {
                    deleteApp(previous.getName());
                }
            }
            if (backup != null) FileUtils.deleteDirectory(backup.toFile());
            if (owaListeners != null) for (OwaListener listener : owaListeners) {
                try { listener.installedApp(app); }
                catch (Exception failure) { log.error("installedApp listener failed", failure); }
            }
            reloadApps();
        } finally {
            if (java.nio.file.Files.exists(stage)) FileUtils.deleteDirectory(stage.toFile());
        }
    }

    private static String archivePath(String name) throws IOException {
        if (name == null || name.isEmpty() || name.startsWith("/") || name.indexOf('\\') >= 0
                || name.indexOf(':') >= 0 || name.indexOf(0) >= 0) throw new IOException("Invalid app file path");
        for (String part : name.split("/")) if ("..".equals(part)) throw new IOException("Invalid app file path");
        return java.nio.file.Paths.get(name).normalize().toString();
    }

    private void unzip(File file, File destination) throws IOException {
        long total = 0;
        try (ZipFile zip = new ZipFile(file)) {
            Enumeration<? extends ZipArchiveEntry> entries = zip.getEntries();
            byte[] buffer = new byte[8192];
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                File target = new File(destination, archivePath(entry.getName()));
                if (!target.getCanonicalPath().startsWith(destination.getCanonicalPath() + File.separator)) {
                    throw new IOException("Invalid app file path");
                }
                if (entry.isDirectory()) { target.mkdirs(); continue; }
                target.getParentFile().mkdirs();
                try (InputStream in = zip.getInputStream(entry); OutputStream out = new FileOutputStream(target)) {
                    int count;
                    while ((count = in.read(buffer)) != -1) {
                        total += count;
                        if (total > 128L * 1024 * 1024) throw new IOException("OWA archive exceeds 128 MiB expanded");
                        out.write(buffer, 0, count);
                    }
                }
            }
        }
    }

	@Override
	public boolean exists(String appName) {
		for (App app : getApps()) {
			if (app.getName().equals(appName) || app.getFolderName().equals(appName)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public App getAppByName(String appName) {
		for (App app : getApps()) {
			if (app.getName().equalsIgnoreCase(appName)) {
				return app;
			}
		}
		return null;
	}

	@Override
	public boolean deleteApp(String name) {
		for (App app : getApps()) {
			if (app.getName().equals(name) || app.getFolderName().equals(name)) {
				try {
					String folderPath = getAppFolderPath() + File.separator + app.getFolderName();
					FileUtils.forceDelete(new File(folderPath));
					if (owaListeners != null) {
						for (OwaListener listener : owaListeners) {
							try {
								listener.deletedApp(app);
							}
							catch (Exception ex) {
								log.error("deleteApp listener " + listener + " failed", ex);
							}
						}
					}
					return true;
				}
				catch (IOException ex) {
					log.error("Could not delete app: " + name, ex);
					return false;
				}
				finally {
					reloadApps(); // Reload app state
				}
			}
		}

		return false;
	}

	@Override
	public String getAppFolderPath() {
		String appFolderPath = Context.getAdministrationService().getGlobalProperty(KEY_APP_FOLDER_PATH);

		File folder = new File(appFolderPath);
		if (!folder.exists()) {
			setAppFolderPath(appFolderPath); // If the global property is set, make sure the folder exists
		}

		return appFolderPath;
	}

	@Override
	public void setAppFolderPath(String appFolderPath) {
		if (!appFolderPath.isEmpty()) {
			try {
				File folder = new File(appFolderPath);
				if (!folder.exists()) {
					FileUtils.forceMkdir(folder);
				}
			}
			catch (IOException ex) {
				log.error(ex.getLocalizedMessage(), ex);
			}
		}
		Context.getAdministrationService().saveGlobalProperty(new GlobalProperty(KEY_APP_FOLDER_PATH, appFolderPath));
	}

	@Override
	public String getAppBaseUrl() {
		return Context.getAdministrationService().getGlobalProperty(KEY_APP_BASE_URL);
	}

	@Override
	public void setAppBaseUrl(String appBaseUrl) {
		Context.getAdministrationService().saveGlobalProperty(new GlobalProperty(KEY_APP_BASE_URL, appBaseUrl));
	}

	@Override
	public String getAppStoreUrl() {
		return Context.getAdministrationService().getGlobalProperty(KEY_APP_STORE_URL, DEFAULT_APP_STORE_URL);
	}

	@Override
	public void setAppStoreUrl(String appStoreUrl) {
		Context.getAdministrationService().saveGlobalProperty(new GlobalProperty(KEY_APP_STORE_URL, appStoreUrl));
	}

	// -------------------------------------------------------------------------
	// Supportive methods
	// -------------------------------------------------------------------------

	/**
	 * Sets the list of apps with detected apps from the file system.
	 */
	@Override
	public void reloadApps() {
		List<App> appList = new ArrayList<>();
		ObjectMapper mapper = new ObjectMapper();
		mapper.configure(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES, false);

		if (null != getAppFolderPath()) {
			File appFolderPath = new File(getAppFolderPath());
			if (appFolderPath.isDirectory()) {
				File[] listFiles = appFolderPath.listFiles();
				for (File folder : listFiles) {
					if (folder.isDirectory() && !folder.getName().startsWith(".owa-")) {
						File appManifest = new File(folder, "manifest.webapp");
						if (appManifest.exists()) {
							try {
								App app = mapper.readValue(appManifest, App.class);
								app.setFolderName(folder.getName());
								appList.add(app);
							}
							catch (IOException ex) {
								log.error("app manifest is non-standard", ex);
							}
						} else {
							log.error("app doesn't have a manifest");
						}
					}
				}
			} else {
				log.error("appFolder settings is not a directory");
			}
		} else {
			log.error("Incorrect appFolder Path");
		}

		this.apps = appList;
		log.info("Detected apps: " + apps);
		if (owaListeners != null) {
			for (OwaListener listener : owaListeners) {
				try {
					listener.appsReloaded(appList);
				} catch (Exception ex) {
					log.error("appsReloaded listener " + listener + " failed", ex);
				}
			}
		}
	}

	/**
	 * Returns String message of missing requirements and empty String if all the requirements are
	 * installed or if no special requirements are needed.
     *
	 * @param file uploaded file of owa that contains Manifest.webapp entry
	 * @param startedModules list of started modules
	 * @return message about missing requirements
	 */
	public String extractMissingRequirementsMessage(File file, List<Module> startedModules) throws IOException {

		StringBuilder errorMessage = new StringBuilder("");

		App app = getAppDefinition(file);
		AppRequirements appRequirements = null;

		if (null != app.getActivities().getOpenmrs().getRequirements()) {
			appRequirements = app.getActivities().getOpenmrs().getRequirements();

			if (null != appRequirements.getCoreVersion()) {
				if (!ModuleUtil.matchRequiredVersions(OpenmrsConstants.OPENMRS_VERSION_SHORT,
				    appRequirements.getCoreVersion())) {

					errorMessage.append("OpenMRS-core version: ").append(appRequirements.getCoreVersion());
				}
			}

			if (null != appRequirements.getRequiredModules()) {
				for (AppRequiredModule requiredModule : appRequirements.getRequiredModules()) {
					boolean moduleStarted = false;
					String reqVersion = requiredModule.getVersion();
					for (Module module : startedModules) {
						if (module.getPackageName().equals(requiredModule.getName())) {

							if (reqVersion != null && ModuleUtil.matchRequiredVersions(module.getVersion(), reqVersion)) {
								moduleStarted = true;
							}
							break;
						}
					}
					if (!moduleStarted) {
						errorMessage
						        .append(", ")
						        .append(
						            requiredModule.getName().replace("org.openmrs.module.", "").replace("org.openmrs.", ""))
						        .append(" version: ").append(reqVersion);
					}
				}
			}
		}
		return errorMessage.toString();
	}

	@Override
	public List<Module> getStartedModules() {
		List startedModules = new ArrayList(ModuleFactory.getStartedModules());
		return startedModules;
	}

	/**
	 * Returns App app definition from file
     *
	 * @param file zip file of owa that contains Manifest.webapp entry
	 * @return App extracted from the manifest.webapp file
	 */

	private static App getAppDefinition(File file) throws IOException {

        App app = null;
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES, false);

		try (ZipFile zipFile = new ZipFile(file)) {
			ZipArchiveEntry entry = zipFile.getEntry("manifest.webapp");

            try (InputStream inputStream = zipFile.getInputStream(entry)) {
				String manifest = org.apache.commons.io.IOUtils.toString(inputStream);
				app = mapper.readValue(manifest, App.class);

            }
		}
		return app;
	}
}
