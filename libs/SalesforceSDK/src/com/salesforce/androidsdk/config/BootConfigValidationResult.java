package com.salesforce.androidsdk.config;

/**
 * Class containing the results of validating the inputs of a particular boot config.
 * @author khawkins
 */
public class BootConfigValidationResult {
    private boolean validationSucceeded;
    private String validationMessage;

    /**
     * Create a BootConfigValidationResult with boolean result and no message.
     * @param validationSucceeded The boolean result, whether the validation succeeded or failed.
     */
    public BootConfigValidationResult(boolean validationSucceeded) {
        this(validationSucceeded, "");
    }

    /**
     * Create a BootConfigValidationResult with a boolean result and message.
     * @param validationSucceeded The boolean result, whether the validation succeeded or failed.
     * @param validationMessage A message associated with the validation process.
     */
    public BootConfigValidationResult(boolean validationSucceeded, String validationMessage) {
        this.validationSucceeded = validationSucceeded;
        this.validationMessage = validationMessage;
    }

    /**
     *
     * @return The boolean result, whether the validation succeeded or failed.
     */
    public boolean getValidationSucceeded() {
        return validationSucceeded;
    }

    /**
     *
     * @return The message associated with the validation process.
     */
    public String getValidationMessage() {
        return validationMessage;
    }
}
