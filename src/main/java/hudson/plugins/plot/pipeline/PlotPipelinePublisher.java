/*
 * Copyright (c) 2007-2009 Yahoo! Inc.  All rights reserved.
 * The copyrights to the contents of this file are licensed under the MIT License (http://www.opensource.org/licenses/mit-license.php)
 */
package hudson.plugins.plot.pipeline;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import jenkins.tasks.SimpleBuildStep;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Records the plotpipeline data for builds.
 *
 * @author Nigel Daley
 */
public class PlotPipelinePublisher extends Recorder implements SimpleBuildStep {

    @DataBoundConstructor
    public PlotPipelinePublisher(Boolean enabled) {}

    private static final Logger LOGGER = Logger.getLogger(PlotPipelinePublisher.class
            .getName());
    /**
     * Array of Plot objects that represent the job's configured plots; must be
     * non-null
     */
    private List<Plot> plots = new ArrayList<Plot>();
    /**
     * Maps plotpipeline groups to plotpipeline objects; group strings are in a URL friendly
     * format; map must be non-null
     */
    transient private Map<String, List<Plot>> groupMap = new HashMap<String, List<Plot>>();

    /**
     * Setup the groupMap upon deserialization.
     */
    private Object readResolve() {
        setPlots(plots);
        return this;
    }

    /**
     * Converts a URL friendly plotpipeline group name to the original group name. If
     * the given urlGroup doesn't already exist then the empty string will be
     * returned.
     */
    public String urlGroupToOriginalGroup(String urlGroup) {
        if (urlGroup == null || "nogroup".equals(urlGroup)) {
            return "Plots";
        }
        if (groupMap.containsKey(urlGroup)) {
            List<Plot> plots = groupMap.get(urlGroup);
            if (CollectionUtils.isNotEmpty(plots)) {
                return plots.get(0).group;
            }
        }
        return "";
    }

    /**
     * Returns all group names as the original user specified strings.
     */
    public List<String> getOriginalGroups() {
        List<String> originalGroups = new ArrayList<String>();
        for (String urlGroup : groupMap.keySet()) {
            originalGroups.add(urlGroupToOriginalGroup(urlGroup));
        }
        Collections.sort(originalGroups);
        return originalGroups;
    }

    /**
     * Replaces the plots managed by this object with the given list.
     *
     * @param plots
     *            the new list of plots
     */
    public void setPlots(List<Plot> plots) {
        this.plots = new ArrayList<Plot>();
        groupMap = new HashMap<String, List<Plot>>();
        for (Plot plot : plots) {
            addPlot(plot);
        }
    }

    /**
     * Adds the new plotpipeline to the plotpipeline data structures managed by this object.
     *
     * @param plot
     *            the new plotpipeline
     */
    public void addPlot(Plot plot) {
        // update the plotpipeline list
        plots.add(plot);
        // update the group-to-plotpipeline map
        String urlGroup = originalGroupToUrlEncodedGroup(plot.getGroup());
        if (groupMap.containsKey(urlGroup)) {
            List<Plot> list = groupMap.get(urlGroup);
            list.add(plot);
        } else {
            List<Plot> list = new ArrayList<Plot>();
            list.add(plot);
            groupMap.put(urlGroup, list);
        }
    }

    /**
     * Returns the entire list of plots managed by this object.
     */
    public List<Plot> getPlots() {
        return plots;
    }

    /**
     * Returns the list of plots with the given group name. The given group must
     * be the URL friendly form of the group name.
     */
    public List<Plot> getPlots(String urlGroup) {
        List<Plot> p = groupMap.get(urlGroup);
        return (p != null) ? p : new ArrayList<Plot>();
    }

    @Override
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

/*    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
            BuildListener listener) throws IOException, InterruptedException {
        listener.getLogger().println("Recording plotpipeline data");
        // add the build to each plotpipeline
        for (Plot plotpipeline : getPlots()) {
            plotpipeline.addBuild(build, listener.getLogger());
        }
        // misconfigured plots will not fail a build so always return true
        return true;
    }*/

    /**
     * Called by Jenkins when a build is finishing.
     */
    @Override
    public void perform(@Nonnull Run<?, ?> run,
                        @Nonnull FilePath workspace,
                        @Nonnull Launcher launcher,
                        @Nonnull TaskListener listener)
            throws InterruptedException, IOException {
        listener.getLogger().println("Recording plotpipeline data");
        // add the build to each plotpipeline
        for (Plot plot : getPlots()) {
            plot.addBuild(run, listener.getLogger(), workspace);
        }
        run.addAction(new PlotBuildAction(run, getPlots()));
    }

    protected String originalGroupToUrlGroup(String originalGroup) {
        if ( StringUtils.isEmpty(originalGroup)) {
            return "nogroup";
        }
        return originalGroup.replace('/', ' ');
    }

    /**
     * Converts the original plotpipeline group name to a URL friendly group name.
     */
    public String originalGroupToUrlEncodedGroup(String originalGroup) {
        return Util.rawEncode(originalGroupToUrlGroup(originalGroup));
    }

    public static final PlotPipelineDescriptor DESCRIPTOR = new PlotPipelineDescriptor();
}
