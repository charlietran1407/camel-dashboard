package vn.cxn.apache_camel.service.component;

import java.util.List;

/**
 * Result of a missing-component download operation. When {@link #isRestartRequired()} is true, the
 * caller should inform the client that the application must be restarted before the route can be
 * deployed. The new JARs are already saved to the loader.path directory and will be picked up
 * automatically by Spring Boot's PropertiesLauncher on the next startup.
 */
public class MissingComponentDownloadResult {

    private final List<String> downloadedArtifacts;
    private final List<String> alreadyPresent;
    private final boolean restartRequired;

    public MissingComponentDownloadResult(
            List<String> downloadedArtifacts,
            List<String> alreadyPresent,
            boolean restartRequired) {
        this.downloadedArtifacts = downloadedArtifacts;
        this.alreadyPresent = alreadyPresent;
        this.restartRequired = restartRequired;
    }

    /** Artifact IDs (groupId:artifactId:version) that were downloaded during this call. */
    public List<String> getDownloadedArtifacts() {
        return downloadedArtifacts;
    }

    /** Components already available on the current classpath — no download needed. */
    public List<String> getAlreadyPresent() {
        return alreadyPresent;
    }

    /**
     * True when at least one new JAR was downloaded. The caller must restart the application to
     * activate it via the -Dloader.path mechanism.
     */
    public boolean isRestartRequired() {
        return restartRequired;
    }

    public boolean hasDownloads() {
        return !downloadedArtifacts.isEmpty();
    }

    @Override
    public String toString() {
        return "MissingComponentDownloadResult{"
                + "downloaded="
                + downloadedArtifacts
                + ", alreadyPresent="
                + alreadyPresent
                + ", restartRequired="
                + restartRequired
                + '}';
    }
}
