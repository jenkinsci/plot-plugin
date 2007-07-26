/*
 * Copyright (c) 2007 Yahoo! Inc.  All rights reserved.  
 * Copyrights licensed under the MIT License.
 */
package hudson.plugins.plot;

import hudson.Launcher;
import hudson.model.Action;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Project;
import hudson.tasks.Publisher;
import hudson.util.FormFieldValidator;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

import javax.servlet.ServletException;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * Records the plot data for builds.
 *
 * @author Nigel Daley
 */
public class PlotPublisher extends Publisher {
	private static final Logger LOGGER = Logger.getLogger(PlotPublisher.class.getName());
	
    /**
     * Array of Plot objects that represent the job's configured
     * plots; must be non-null
     */
    private ArrayList<Plot> plots = new ArrayList<Plot>();
    
    /**
	 * Maps plot groups to plot objects; group strings are in a
	 * URL friendly format; map must be non-null
	 */
    transient private HashMap<String,ArrayList<Plot>> groupMap = 
    	new HashMap<String,ArrayList<Plot>>();
    
    /**
     * Setup the groupMap upon deserialization.
     */
    private Object readResolve() {
        Plot[] p = plots.toArray(new Plot[] {});
        setPlots(p);
        return this;
    }
    
    /**
     * Converts a URL friendly plot group name to the original group name.
     * If the given urlGroup doesn't already exist then the empty string will
     * be returned. 
     */
    public String urlGroupToOriginalGroup(String urlGroup) {
    	if (urlGroup == null || "nogroup".equals(urlGroup)) {
    		return "Plots";
    	}
    	if (groupMap.containsKey(urlGroup)) {
    		ArrayList<Plot> plots = groupMap.get(urlGroup);
    		if (plots.size() > 0) {
    			return plots.get(0).group;
    		}
    	}
    	return "";
    }
    
    /**
     * Converts the original plot group name to a URL friendly group name.
     */
    public String originalGroupToUrlGroup(String originalGroup) {
    	if (originalGroup == null || "".equals(originalGroup)) {
    		return "nogroup";
    	}
    	try {
    	    return URLEncoder.encode(originalGroup.replace('/',' '),"UTF-8");
    	} catch (UnsupportedEncodingException uee) {
    		// should never happen
    		return originalGroup;
    	}
    }
    
    /**
     * Returns all group names as the original user specified strings.
     */
    public String[] getOriginalGroups() {
    	ArrayList<String> originalGroups = new ArrayList<String>();
    	for (String urlGroup : groupMap.keySet()) {
    		originalGroups.add(urlGroupToOriginalGroup(urlGroup));
    	}
    	String[] retVal = originalGroups.toArray(new String[] {});
    	Arrays.sort(retVal);
    	return retVal;
    }
    
    /**
     * Replaces the plots managed by this object with the given list.
     * 
     * @param plots the new list of plots
     */
    public void setPlots(Plot[] plots) {
    	this.plots = new ArrayList<Plot>();
    	groupMap = new HashMap<String,ArrayList<Plot>>();
    	for (Plot plot : plots) {
    		addPlot(plot);
    	}
    }
    
    /**
     * Adds the new plot to the plot data structures managed by this object. 
     * 
     * @param plot the new plot
     */
    public void addPlot(Plot plot) {
    	// update the plot list
    	plots.add(plot);
    	// update the group-to-plot map
		String urlGroup = originalGroupToUrlGroup(plot.getGroup());
		if (groupMap.containsKey(urlGroup)) {
			ArrayList<Plot> list = groupMap.get(urlGroup);
			list.add(plot);
		} else {
			ArrayList<Plot> list = new ArrayList<Plot>();
			list.add(plot);
			groupMap.put(urlGroup, list);
		}
    }

    /**
     * Returns the entire list of plots managed by this object.
     */
    public Plot[] getPlots() {
        return plots.toArray(new Plot[] {});
    }

    /**
     * Returns the list of plots with the given group name.  The given
     * group must be the URL friendly form of the group name.
     */
    public Plot[] getPlots(String urlGroup) {
        ArrayList<Plot> p = groupMap.get(urlGroup);
        if (p != null) {
        	return p.toArray(new Plot[] {});
        }
        return new Plot[] {};
    }
    
    /**
     * Called by Hudson.
     */
    public Action getProjectAction(Project project) {
        return new PlotAction(project, this);
    }
    
    /**
     * Called by Hudson.
     */
    public Descriptor<Publisher> getDescriptor() {
        return DESCRIPTOR;
    }
    
    /**
     * Called by Hudson when a build is finishing.
     */
    public boolean perform(Build build, Launcher launcher, 
    		BuildListener listener) throws IOException, InterruptedException 
    {
        listener.getLogger().println("Recording plot data");
        // add the build to each plot
        for (Plot plot : getPlots()) {
            plot.addBuild(build,listener.getLogger());
        }
        // misconfigured plots will not fail a build so always return true
        return true;
    }

    public static final Descriptor<Publisher> DESCRIPTOR = new DescriptorImpl();
    
    public static class DescriptorImpl extends Descriptor<Publisher> {
        public DescriptorImpl() {
            super(PlotPublisher.class);
        }
        public String getDisplayName() {
            return "Plot build data";
        }
        public String getHelpFile() {
            return "/publisher/PlotPublisher/help";
        }
        /**
         * Called when the user saves the project configuration.
         */
        public Publisher newInstance(StaplerRequest req) throws FormException {
        	Random r = new Random();
            PlotPublisher publisher = new PlotPublisher();
            String[] fileNames = req.getParameterValues("plotParam.csvFileName");
            String[] titles = req.getParameterValues("plotParam.title");
            String[] yaxises = req.getParameterValues("plotParam.yaxis");
            String[] groups = req.getParameterValues("plotParam.group");
            String[] numBuilds = req.getParameterValues("plotParam.numBuilds");
        	String[] seriesFiles = req.getParameterValues("seriesParam.file");
        	String[] seriesLabels = req.getParameterValues("seriesParam.label");
            if (seriesFiles != null) {
            	//LOGGER.info(fileNames.length+","+titles.length+","+yaxises.length+","+
            	//	groups.length+","+numBuilds.length+","+seriesFiles.length+","+
            	//	seriesLabels.length);
                int len = fileNames.length;
                int seriesCounter = 0;
                for (int i=0; i<len; i++) {
                	List<Series> seriesList = new ArrayList<Series>();
                	while ( seriesCounter < seriesFiles.length &&
                		    ! seriesFiles[seriesCounter].equals("PLOT_SEPARATOR")) 
                	{
                    	seriesList.add(new Series(seriesFiles[seriesCounter],
                    							  seriesLabels[seriesCounter]));
                    	seriesCounter++;
                    }
                	seriesCounter++; //skip past separator
                	if (fileNames[i] == null || fileNames[i].trim().equals("")) {
                		fileNames[i] = Math.abs(r.nextInt()) + ".csv";
                	}
                	String builds;
                	try {
                		builds = Integer.parseInt(numBuilds[i]) + "";
                	} catch (NumberFormatException nfe) {
                		builds = "";
                	}
                    Plot plot = new Plot(
                    		titles[i],
                    		yaxises[i],
                    		seriesList.toArray(new Series[] {}),
                    		groups[i],
                    		builds,
                    		fileNames[i]);
                    //LOGGER.info("new Plot():" + plot.toString());
                    publisher.addPlot(plot);
                }
            }
            return publisher;
        }

        /**
         * Checks if the series file is valid.
         */
        public void doCheckSeriesFile(StaplerRequest req, StaplerResponse rsp) 
        	throws IOException, ServletException 
        {
            new FormFieldValidator(req,rsp,false) {
                public void check() throws IOException, ServletException {
                    String file = request.getParameter("value");
                    if (file == null || "".equals(file)) {
                    	error("File must be specified");
                    	return;
                    }
                }
            }.process();
            new FormFieldValidator.WorkspaceFileMask(req,rsp).process();
        }
        
        /**
         * Checks if the number of builds is valid.
         */
        public void doCheckNumBuilds(StaplerRequest req, StaplerResponse rsp) 
        	throws IOException, ServletException 
        {
            new FormFieldValidator(req,rsp,false) {
                public void check() throws IOException, ServletException {
                    try {
                    	String numBuilds = request.getParameter("value");
                    	if ("".equals(numBuilds)) {
                    		ok();
                    	} else {
                    		int num = Integer.parseInt(numBuilds);
                    		if (num == 0) {
                    			error("Must be greater than 0");
                    		} else {
                    			ok();
                    		}
                    	}
                    } catch (NumberFormatException nfe) {
                        error("Not a valid integer number");
                    }
                }
            }.process();
        }
    }
}
