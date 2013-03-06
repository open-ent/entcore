/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.one.core.module;

import java.util.logging.Filter;
import java.util.logging.LogRecord;

/**
 *
 * @author dwak
 */
public class ApplicationLogFilter implements Filter{
	
	String name = "";

	public ApplicationLogFilter(String appli) {
		name = appli;
	}

	@Override
	public boolean isLoggable(LogRecord record) {
		return record.getParameters()[0].equals(name);
	}
	
}
