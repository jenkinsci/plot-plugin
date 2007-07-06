/*
 * Copyright (c) 2007 Yahoo! Inc.  All rights reserved.  
 * Copyrights licensed under the MIT License.
 */
package hudson.plugins.plot;

import hudson.model.Project;

import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * Represents a plot report for a single group of plots.
 * 
 * @author Nigel Daley
 */
public class PlotReport {
	private static final Logger LOGGER = Logger.getLogger(PlotReport.class.getName());
	
    private final Project project;
    
	/**
	 * The sorted list of plots that belong to the same group.
	 */
	private Plot[] plots;
	
	/**
	 * The group these plots belong to.
	 */
	private String group;
	
	public PlotReport(Project project, String group, Plot[] plots) {
		Arrays.sort(plots);
		this.plots = plots;
		this.group = group;
		this.project = project;
	}

	// called from PlotReport/index.jelly
    public Project getProject() {
        return project;
    }

	// called from PlotReport/index.jelly
    public String getGroup() {
        return group;
    }
    
    // called from PlotReport/index.jelly
	public Plot[] getPlots() {
		return plots;
	}
	
	// called from PlotReport/index.jelly
	public void doGetPlot(StaplerRequest req, StaplerResponse rsp) {
		String i = req.getParameter("index");
		Plot plot = getPlot(i);
		try {
			plot.plotGraph(req,rsp);
		} catch (IOException ioe) {
			LOGGER.log(Level.INFO,"Exception plotting graph",ioe);
		}
	}
	
	// called from PlotReport/index.jelly
	public void doGetPlotMap(StaplerRequest req, StaplerResponse rsp) {
		String i = req.getParameter("index");
		Plot plot = getPlot(i);
		try {
			plot.plotGraphMap(req,rsp);
		} catch (IOException ioe) {
			LOGGER.log(Level.INFO,"Exception plotting graph",ioe);
		}
	}
	
    private Plot getPlot(String i) {
    	try {
    		Plot p = plots[Integer.valueOf(i)];
    		p.setProject(project);
    		return p; 
    	} catch (NumberFormatException ignore) {
    		//ignore
    		return null;
    	}
    }
}