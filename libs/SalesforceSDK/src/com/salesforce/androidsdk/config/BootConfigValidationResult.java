/*
 * Copyright (c) 2017-present, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use of this software in source and binary forms, with or
 * without modification, are permitted provided that the following conditions
 * are met:
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * - Neither the name of salesforce.com, inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission of salesforce.com, inc.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
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
