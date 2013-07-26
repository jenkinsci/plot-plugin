/*
 * Copyright (c) 2007 Yahoo! Inc.  All rights reserved.  
 * Copyrights licensed under the MIT License.
 */
package hudson.plugins.plot;

import hudson.FilePath;
import hudson.matrix.MatrixConfiguration;
import hudson.matrix.MatrixProject;
import hudson.model.AbstractProject;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import au.com.bytecode.opencsv.CSVReader;

/**
 * Represents a plot report for a single group of plots.
 * 
 * @author Nigel Daley
 */
public class PlotReport {
	private static final Logger LOGGER = Logger.getLogger(PlotReport.class.getName());
	
    private final AbstractProject<?, ?> project;
    
	/**
	 * The sorted list of plots that belong to the same group.
	 */
	private PlotData[] plots;
	
	/**
	 * The group these plots belong to.
	 */
	private String group;
	
	public PlotReport(AbstractProject<?, ?> project, String group, PlotData[] plots) {
		Arrays.sort(plots);
		this.plots = plots;
		this.group = group;
		this.project = project;
	}

	// called from PlotReport/index.jelly
    public AbstractProject<?, ?> getProject() {
        return project;
    }

	// called from PlotReport/index.jelly
    public String getGroup() {
        return group;
    }
    
    // called from PlotReport/index.jelly
	public PlotData[] getPlotData() {
		return plots;
	}
	
	// called from PlotReport/index.jelly
	public void doGetPlot(StaplerRequest req, StaplerResponse rsp) {
		String i = req.getParameter("index");
		PlotData plotData = getPlot(i);
		try {
			if (plotData != null) {
				plotData.plotGraph(req,rsp);
			}
		} catch (IOException ioe) {
			LOGGER.log(Level.INFO,"Exception plotting graph",ioe);
		}
	}
	
	// called from PlotReport/index.jelly
	public void doGetPlotMap(StaplerRequest req, StaplerResponse rsp) {
		String i = req.getParameter("index");
		PlotData plotData = getPlot(i);
		try {
			if (plotData != null) {
				plotData.plotGraphMap(req,rsp);
			}
		} catch (IOException ioe) {
			LOGGER.log(Level.INFO,"Exception plotting graph",ioe);
		}
	}

	// called from PlotReport/index.jelly
	public boolean getDisplayTableFlag(int i) {
/*		PlotData plotData = getPlot(Integer.toString(i));

		if (plotData.getSeries()!=null) {
    	    Series series = plotData.getSeries()[0];
    	    return (series instanceof CSVSeries) && ((CSVSeries)series).getDisplayTableFlag();
    	}
*/    	return false;
	}
	
	// called from PlotReport/index.jelly
    public ArrayList getTable(int i) {
    	ArrayList tableData = new ArrayList<String[]>();
    	
    	PlotData plotData = getPlot(""+i);
    	
        // load existing csv file
        File tableFile = new File(project.getRootDir(), "table_"+plotData.getCsvFileName());
        if (!tableFile.exists()) {
            return tableData;
        }
        CSVReader reader = null;
        try {
            reader = new CSVReader(new FileReader(tableFile));
            tableData = (ArrayList)reader.readAll();
        } catch (IOException ioe) {
            //ignore
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignore) {
                    //ignore
                }
            }
        }
    	return tableData;
    }
	
    private PlotData getPlot(String i) {
    	try {
    		PlotData p = plots[Integer.valueOf(i)];
    		return p; 
    	} catch (NumberFormatException ignore) {
    		//ignore
    		return null;
    	}
    }
}