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
package org.openmrs.logic.rule.definition;

import org.apache.commons.lang.StringUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;


/**
 * Validator for {@link RuleDefinition}
 */
public class RuleDefinitionValidator implements Validator {

	@SuppressWarnings("unchecked")
	public boolean supports(Class c) {
		return c.equals(RuleDefinition.class);
	}

	public void validate(Object obj, Errors errors) {
		RuleDefinition rule = (RuleDefinition) obj;
		if (StringUtils.isBlank(rule.getName()))
			errors.rejectValue("name", "error.null");
		if (StringUtils.isBlank(rule.getLanguage()))
			errors.rejectValue("language", "error.null");
		if (StringUtils.isBlank(rule.getRuleContent()))
			errors.rejectValue("ruleContent", "error.null");
        if (rule.getName() != null && rule.getName().length() > 255) errors.rejectValue("name","error.invalid", "Use 255 characters or fewer");
        if (rule.getDescription() != null && rule.getDescription().length() > 1000) errors.rejectValue("description","error.invalid", "Use 1000 characters or fewer");
        if (rule.getRuleContent() != null && rule.getRuleContent().length() > 2048) errors.rejectValue("ruleContent","error.invalid", "Use 2048 characters or fewer");
    }

}
