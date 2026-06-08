package vn.cxn.apache_camel.validation;

import java.util.List;

/**
 * Thrown by the deployment pipeline when new Camel component JARs were downloaded to the
 * loader-path directory but the JVM must be restarted before they become active on the classpath.
 *
 * <p>The route version has been saved successfully; the client should inform the operator that a
 * restart is required and then re-deploy the version afterwards.
 */
public class MissingComponentsDownloadedException extends RuntimeException {

    private final List<String> downloadedArtifacts;

    public MissingComponentsDownloadedException(
            List<String> downloadedArtifacts, String loaderPath) {
        super(buildMessage(downloadedArtifacts, loaderPath));
        this.downloadedArtifacts = downloadedArtifacts;
    }

    public List<String> getDownloadedArtifacts() {
        return downloadedArtifacts;
    }

    private static String buildMessage(List<String> artifacts, String loaderPath) {
        return "Missing Camel components were downloaded and staged in '"
                + loaderPath
                + "': "
                + artifacts
                + ". Please restart the application (the JARs will be picked up automatically via"
                + " -Dloader.path), then re-deploy this route version.";
    }
}
