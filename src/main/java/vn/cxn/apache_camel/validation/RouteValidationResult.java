package vn.cxn.apache_camel.validation;

import java.util.ArrayList;
import java.util.List;

public class RouteValidationResult {
    private boolean isValid = true;
    private String stage;
    private List<ValidationError> errors = new ArrayList<>();
    private List<ValidationWarning> warnings = new ArrayList<>();
    private List<String> replaceCandidates = new ArrayList<>();

    public RouteValidationResult() {}

    public boolean getIsValid() {
        return isValid;
    }

    public void setIsValid(boolean valid) {
        isValid = valid;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public List<ValidationError> getErrors() {
        return errors;
    }

    public void setErrors(List<ValidationError> errors) {
        this.errors = errors;
    }

    public List<ValidationWarning> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<ValidationWarning> warnings) {
        this.warnings = warnings;
    }

    public List<String> getReplaceCandidates() {
        return replaceCandidates;
    }

    public void setReplaceCandidates(List<String> replaceCandidates) {
        this.replaceCandidates = replaceCandidates;
    }
}
