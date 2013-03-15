/*
 * Copyright (c) 2007-2009 Yahoo! Inc.  All rights reserved.
 * The copyrights to the contents of this file are licensed under the MIT License (http://www.opensource.org/licenses/mit-license.php)
 */
package hudson.plugins.plot;

import hudson.Launcher;
import hudson.Util;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.logging.Logger;



/**
 * Records the plot data for builds.
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
        Plot[] p = plots.toArray(new Plot[]{});
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
        this.plots = new ArrayList<Plot>();
        groupMap = new HashMap<String, ArrayList<Plot>>();
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
    public Action getProjectAction(AbstractProject<?, ?> project) {
        return new PlotAction(project, this);
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
        listener.getLogger().println("Recording plot data");
        // add the build to each plot
        for (Plot plot : getPlots()) {
            plot.addBuild(build, listener.getLogger());
        }
        // misconfigured plots will not fail a build so always return true
        return true;
    }
    public static final PlotDescriptor DESCRIPTOR = new PlotDescriptor();
}
