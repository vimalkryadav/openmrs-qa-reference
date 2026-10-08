/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.logic.token;

import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;

/**
 * Validator for {@link TokenRegistration}
 */
public class TokenRegistrationValidator implements Validator {

	/**
	 * @see org.springframework.validation.Validator#supports(java.lang.Class)
	 */
	@SuppressWarnings("unchecked")
	public boolean supports(Class c) {
		return c.equals(TokenRegistration.class);
	}

	/**
	 * @see org.springframework.validation.Validator#validate(java.lang.Object, org.springframework.validation.Errors)
	 */
	public void validate(Object obj, Errors errors) {
		ValidationUtils.rejectIfEmptyOrWhitespace(errors, "token", "error.null");
		ValidationUtils.rejectIfEmptyOrWhitespace(errors, "providerClassName", "error.null");
		ValidationUtils.rejectIfEmptyOrWhitespace(errors, "configuration", "error.null");
		ValidationUtils.rejectIfEmptyOrWhitespace(errors, "providerToken", "error.null");
        TokenRegistration token = (TokenRegistration)obj;
        String[] fields = {"token", "providerClassName", "configuration", "providerToken"};
        String[] values = {token.getToken(), token.getProviderClassName(), token.getConfiguration(), token.getProviderToken()};
        for (int i=0; i<fields.length; i++) {
            int maximum = fields[i].equals("configuration") ? 2000 : 512;
            if (values[i] != null && values[i].length() > maximum)
                errors.rejectValue(fields[i], "error.invalid", "Use " + maximum + " characters or fewer");
        }
    }

}
