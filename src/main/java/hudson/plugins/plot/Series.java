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
    protected String file;

    /**
     * Data series legend label. Optional.
     */
    protected String label;

    /**
     * Data series type. Mandatory.
     * This can be csv, xml, or properties file.
     * This should be an enum, but I am not sure how to support that with stapler at the moment 
     */
    protected String fileType;
    
    protected Series(String file, String label, String fileType) {
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

    // Convert data from before version 1.3
    private Object readResolve() {
        return (fileType == null) ? new PropertiesSeries(file, label) : this;
    }
}

