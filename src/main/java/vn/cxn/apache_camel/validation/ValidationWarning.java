package vn.cxn.apache_camel.validation;

public class ValidationWarning {
    private String code;
    private String severity = "WARNING";
    private String message;

    private java.util.List<String> args = new java.util.ArrayList<>();

    public ValidationWarning() {}

    public ValidationWarning(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public ValidationWarning(String code, String message, java.util.List<String> args) {
        this.code = code;
        this.message = message;
        if (args != null) {
            this.args = args;
        }
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public java.util.List<String> getArgs() {
        return args;
    }

    public void setArgs(java.util.List<String> args) {
        this.args = args;
    }
}
