/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.reporting.common;

import java.util.List;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Cohort;
import org.openmrs.api.context.Context;
import org.openmrs.logic.LogicCriteria;
import org.openmrs.logic.LogicException;
import org.openmrs.module.reporting.evaluation.EvaluationContext;
import org.openmrs.util.PrivilegeConstants;

@Deprecated
public class LogicUtil {
	
	protected static Log log = LogFactory.getLog(LogicUtil.class);
	
	/**
	 * Tries to parse a String into a {@link LogicCriteria}. This method is safe to run on versions
	 * of logic before 0.4.1--if the parser runs for more than 2 seconds, this method will throw an
	 * exception.
	 * 
	 * @see http://tickets.openmrs.org/browse/LOGIC-53
	 * @param logic
	 */
	public static LogicCriteria parse(final String logic) throws LogicException {
		// these need to be final so we can refer to them from the thread
		// therefore they must be arrays so they can be final but have the value they're holding change
		final LogicCriteria[] resultHolder = new LogicCriteria[1];
		final Exception[] exceptionHolder = new Exception[1];
		
		// create a thread that will parse the input and store its success or failure in a holder 
		Thread parser = new Thread(new Runnable() {
			
			public void run() {
				Context.openSession();
				try {
					resultHolder[0] = Context.getLogicService().parse(logic);
				}
				catch (Exception ex) {
					exceptionHolder[0] = ex;
				}
				finally {
					Context.closeSession();
				}
			}
		});
		
		// try running the thread for up to 2 seconds
		long startTime = System.nanoTime();
        parser.setDaemon(true);
		parser.start();
		while (parser.isAlive() && resultHolder[0] == null && exceptionHolder[0] == null && System.nanoTime() - startTime < 2000000000L) {
			try {
				parser.join(250);
			}
			catch (InterruptedException ex) {}
		}
		
		// End the wait even when an invalid expression returns null. The frozen domain clock
        // must not control this timeout; Java 21 no longer supports Thread.stop().
		if (exceptionHolder[0] == null && resultHolder[0] == null) {
			parser.interrupt();
		}
		
		// if an error was singalled, we throw it
		if (exceptionHolder[0] != null) {
			if (exceptionHolder[0] instanceof LogicException)
				throw (LogicException) exceptionHolder[0];
			else
				throw new LogicException(exceptionHolder[0]);
		}
		
		// if success was signalled, return that, otherwise throw a generic logic exception
		if (resultHolder[0] != null)
			return resultHolder[0];
		else
			throw new LogicException(parser.isAlive() ? "Took too long to parse" : "Invalid logic expression");
	}
	
	/**
	 * Validates the specified logic expression
	 * 
	 * @param logicExpression the logic expression to be validated
	 * @return true if the specified expression is valid otherwise false
	 * @throws LogicException
	 */
	public static boolean isValidLogicExpression(String logicExpression) throws LogicException {
		
		Integer testPatientId = null;
		try {
			Context.addProxyPrivilege(PrivilegeConstants.SQL_LEVEL_ACCESS);
			List<List<Object>> results = Context.getAdministrationService().executeSQL(
			    "select min(patient_id) from patient", true);
			if (CollectionUtils.isNotEmpty(results) && CollectionUtils.isNotEmpty(results.get(0)))
				testPatientId = Integer.parseInt(results.get(0).get(0).toString());
		}
		finally {
			Context.removeProxyPrivilege(PrivilegeConstants.SQL_LEVEL_ACCESS);
		}
		
		if (testPatientId != null) {
			EvaluationContext evaluationContext = new EvaluationContext();
			Cohort cohort = new Cohort();
			cohort.addMember(testPatientId);
			evaluationContext.setBaseCohort(cohort);
			try {
				Context.getLogicService().eval(cohort, logicExpression);
				return true;
			}
			catch (LogicException ex) {
				log.error("Invalid Logic expression:" + logicExpression, ex);
			}
			
		} else {
			throw new LogicException(Context.getMessageSourceService().getMessage("reporting.noPatientData", null,
			    "No Patient Data found", Context.getLocale()));
		}
		
		return false;
	}
}
