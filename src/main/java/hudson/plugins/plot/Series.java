/*
 * Copyright (c) 2007-2009 Yahoo! Inc.  All rights reserved.
 * The copyrights to the contents of this file are licensed under the MIT License (http://www.opensource.org/licenses/mit-license.php)
 */

package hudson.plugins.plot;

import java.io.PrintStream;

import hudson.FilePath;

/**
 * Represents a plot data series configuration.
 *
 * @author Nigel Daley
 * @author Allen Reese
 */
public class Series {
    /**
     * Relative path to the data series property file. Mandatory.
     */
    public String file;

    /**
     * Data series legend label. Optional.
     */
    public String label;

    /**
     * Data series type. Mandatory.
     * This can be csv, xml, or properties file.
     * This should be an enum, but I am not sure how to support that with stapler at the moment 
     */
    public String fileType;
    
    public Series() {
        this.file = null; 
		this.label = "missing";
        this.fileType = "properties";
    }

    /**
     * @stapler-constructor
     */
    public Series(String file, String label, String fileType) {
        this.file = file; 

        // TODO: look into this, what do we do if there is no label?
        if (label == null)
			label = "missing";
        
        this.label = label;
        this.fileType = fileType;
    }
    
    public String getFile() {
        return file;
    }
    public String getLabel() {
        return label;
    }
    public String getFileType() {
        return fileType;
    }
    
    /**
     * This is used by the radio buttons to test if they should be checked.
     * returns true if properties is the fileType, and the fileType string is unset.
     * @param fileType String filetype from the radio button to check.
     * @return true if the radio button should be selected
     */
    public boolean isFileType(String fileType)
    {
    	if (this.fileType==null) {
    		if ("properties".equalsIgnoreCase(fileType)) {
    			return true;
    		}
    		return false;
    	}
    	return this.fileType.equalsIgnoreCase(fileType);
    }
    
    /**
     * This is used for saving state of radio buttons in subclasses.
     * Series always returns false.
     * There has to be a cleaner way of doing this, such as casting Series to CSVSeries in the jelly.
     * @param test
     * @return false
     */
    public boolean test(String test)
    {
    	return false;
    }
    
    /**
     * Still not happy, but rather than getExcludedValues(), pretend to be a map.
     * There has to be a cleaner way of doing this, such as casting Series to CSVSeries in the jelly.
     * @return String value of name.
     */
    public String getValue(String name)
    {
    	return "";
    }
    
    public String getDisplayTableFlag()
    {
    	return "false";
    }

    /**
     * Retrieves the plot data for one series after a build from the workspace.
     * 
     * @param workspaceRootDir the root directory of the workspace
     * @param logger the logger to use
     * @return a PlotPoint array of points to plot
     */
    public PlotPoint[] loadSeries(FilePath workspaceRootDir, PrintStream logger)
    {
    	return null;
    }
}

