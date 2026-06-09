package vn.cxn.apache_camel.util;

public final class ExceptionUtil {

    private ExceptionUtil() {
        // Prevent instantiation
    }

    /**
     * Traverses the exception chain and builds a detailed, nested error message in the format:
     * ClassSimpleName: Message -> ClassSimpleName: Message...
     *
     * @param e the root exception
     * @return the combined nested error message string
     */
    public static String buildErrorMessage(Throwable e) {
        if (e == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        Throwable current = e;
        while (current != null) {
            String className = current.getClass().getSimpleName();
            String msg = current.getMessage();
            String currentMsg =
                    (msg != null && !msg.isBlank()) ? className + ": " + msg : className;
            if (sb.length() > 0) {
                sb.append(" -> ");
            }
            sb.append(currentMsg);
            current = current.getCause();
        }
        return sb.toString();
    }
}
