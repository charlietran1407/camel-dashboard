package vn.cxn.apache_camel.validation;

public class ValidationError {
    private String code;
    private String severity = "ERROR";
    private String message;
    private String location;

    private java.util.List<String> args = new java.util.ArrayList<>();

    public ValidationError() {}

    public ValidationError(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public ValidationError(String code, String message, String location) {
        this.code = code;
        this.message = message;
        this.location = location;
    }

    public ValidationError(
            String code, String message, String location, java.util.List<String> args) {
        this.code = code;
        this.message = message;
        this.location = location;
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

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public java.util.List<String> getArgs() {
        return args;
    }

    public void setArgs(java.util.List<String> args) {
        this.args = args;
    }
}
