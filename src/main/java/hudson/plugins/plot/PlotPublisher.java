/*
 * Copyright (c) 2007-2009 Yahoo! Inc.  All rights reserved.
 * The copyrights to the contents of this file are licensed under the MIT License (http://www.opensource.org/licenses/mit-license.php)
 */
package hudson.plugins.plot;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.matrix.MatrixRun;
import hudson.matrix.MatrixProject;
import hudson.model.Action;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Project;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Vector;
import java.util.logging.Logger;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Records the plot data for builds.
 * 
 * This is the entry point of the plugin. Jenkins will instanciate this class when loading
 * a job that contains a plot post build step
 *
 * @author Nigel Daley
 */
public class PlotPublisher extends Recorder {

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
    transient private HashMap<String, ArrayList<Plot>> groupMap =
            new HashMap<String, ArrayList<Plot>>();

    /**
     * Setup the groupMap upon deserialization.
     */
    private Object readResolve() {
    	LOGGER.entering(this.getClass().getName(), "readResolve");
        Plot[] p = plots.toArray(new Plot[]{});
        setPlots(p);
        LOGGER.exiting(this.getClass().getName(), "readResolve");
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
    public String originalGroupToUrlEncodedGroup(String originalGroup) {
        return Util.rawEncode(originalGroupToUrlGroup(originalGroup));
    }

    private String originalGroupToUrlGroup(String originalGroup) {
        if (originalGroup == null || "".equals(originalGroup)) {
            return "nogroup";
        }
        return originalGroup.replace('/', ' ');
    }

    /**
     * Returns all group names as the original user specified strings.
     */
    public String[] getOriginalGroups() {
        ArrayList<String> originalGroups = new ArrayList<String>();
        for (String urlGroup : groupMap.keySet()) {
            originalGroups.add(urlGroupToOriginalGroup(urlGroup));
        }
        String[] retVal = originalGroups.toArray(new String[]{});
        Arrays.sort(retVal);
        return retVal;
    }

    /**
     * Replaces the plots managed by this object with the given list.
     * 
     * @param plots the new list of plots
     */
    public void setPlots(Plot[] plots) {
    	LOGGER.entering(this.getClass().getName(), "setPlots");
        this.plots = new ArrayList<Plot>();
        groupMap = new HashMap<String, ArrayList<Plot>>();
        for (Plot plot : plots) {
        	LOGGER.finest("Setting the plot: " + plot);
            addPlot(plot);
        }
        LOGGER.exiting(this.getClass().getName(), "setPlots");
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
        String urlGroup = originalGroupToUrlEncodedGroup(plot.getGroup());
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
        return plots.toArray(new Plot[]{});
    }

    /**
     * Returns the list of plots with the given group name.  The given
     * group must be the URL friendly form of the group name.
     */
    public Plot[] getPlots(String urlGroup) {
        ArrayList<Plot> p = groupMap.get(urlGroup);
        if (p != null) {
            return p.toArray(new Plot[]{});
        }
        return new Plot[]{};
    }

    /**
     * Called by Jenkins.
     */
    @Override
    public Collection<Action> getProjectActions(AbstractProject<?, ?> project) {
    	LOGGER.entering(this.getClass().getName(), "getProjectActions");
    	Vector<Action> vec = new Vector<Action>();
    	if(project instanceof Project || project instanceof MatrixProject) {
    		vec.add( new PlotAction(project, this) );
    	}
        return vec;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }

    /**
     * Called by Jenkins.
     */
    @Override
    public BuildStepDescriptor<Publisher> getDescriptor() {
        return DESCRIPTOR;
    }

    /**
     * Called by Jenkins when a build is finishing.
     */
    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
            BuildListener listener) throws IOException, InterruptedException {
    	LOGGER.entering(this.getClass().getName(), "perform");
    	
    	// Should always be a supported Build due to isApplicable below. But does not hurt to double check
        if (!(build instanceof Build) && !(build instanceof MatrixRun) ) {
        	LOGGER.warning("The type of project is not supported");
        	return true;
        }
        listener.getLogger().println("Recording plot data");
        
        // add the build to each plot configured for this jobs.
        for (Plot plot : getPlots()) {
        	LOGGER.fine("Adding the build to the plot " + plot);
    		plot.addBuild((Build<?, ?>) build, listener);
        }
        // misconfigured plots will not fail a build so always return true
        return true;
    }
    
    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    // Actual implementation of the plugin so that it is recognized by Jenkins and instanciated
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        public DescriptorImpl() {
            super(PlotPublisher.class);
        }

        public String getDisplayName() {
            return Messages.Plot_Publisher_DisplayName();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
        	// This is where we specify this plugin is applicable to FreeStyle and Matrix projects
            return Project.class.isAssignableFrom(jobType) || MatrixProject.class.isAssignableFrom(jobType);
        }

        /**
         * Called when the user saves the project configuration.
         */
        @Override
        public Publisher newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            PlotPublisher publisher = new PlotPublisher();
            for (Object data : SeriesFactory.getArray(formData.get("plots"))) {
                publisher.addPlot(bindPlot((JSONObject) data, req));
            }
            return publisher;
        }
        private static Plot bindPlot(JSONObject data, StaplerRequest req) {
            Plot p = req.bindJSON(Plot.class, data);
            p.series = SeriesFactory.createSeriesList(data.get("series"), req);
            return p;
        }

        /**
         * Checks if the series file is valid.
         */
        public FormValidation doCheckSeriesFile(@AncestorInPath AbstractProject project,
                @QueryParameter String value) throws IOException {
            return FilePath.validateFileMask(project.getSomeWorkspace(), value);
        }
    }
}
