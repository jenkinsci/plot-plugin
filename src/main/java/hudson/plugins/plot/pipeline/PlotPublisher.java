/*
 * Copyright (c) 2007-2009 Yahoo! Inc.  All rights reserved.
 * The copyrights to the contents of this file are licensed under the MIT License (http://www.opensource.org/licenses/mit-license.php)
 */
package hudson.plugins.plot.pipeline;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.matrix.MatrixProject;
import hudson.model.AbstractProject;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.plot.Messages;
import hudson.plugins.plot.Plot;
import hudson.plugins.plot.SeriesFactory;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Records the plot data for builds.
 *
 * @author Nigel Daley
 */
public class PlotPublisher extends Recorder implements SimpleBuildStep {
    /**
     * Array of Plot objects that represent the job's configured plots; must be non-null
     */
    private List<Plot> plots = new ArrayList<>();
    /**
     * Maps plot groups to plot objects; group strings are in a URL friendly format;
     * map must be non-null
     */
    transient private Map<String, List<Plot>> groupMap = new HashMap<>();

    /**
     * Setup the groupMap upon deserialization.
     */
    private Object readResolve() {
        setPlots(plots);
        return this;
    }

    /**
     * Converts a URL friendly plot group name to the original group name.
     * If the given urlGroup doesn't already exist then the empty string will be returned.
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

    protected String originalGroupToUrlGroup(String originalGroup) {
        if (StringUtils.isEmpty(originalGroup)) {
            return "nogroup";
        }
        return originalGroup.replace('/', ' ');
    }

    /**
     * Converts the original plot group name to a URL friendly group name.
     */
    public String originalGroupToUrlEncodedGroup(String originalGroup) {
        return Util.rawEncode(originalGroupToUrlGroup(originalGroup));
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
     * @param plots the new list of plots
     */
    public void setPlots(List<Plot> plots) {
        this.plots = new ArrayList<>();
        groupMap = new HashMap<>();
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
            List<Plot> list = groupMap.get(urlGroup);
            list.add(plot);
        } else {
            List<Plot> list = new ArrayList<>();
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

    /**
     * Called by Jenkins when a build is finishing.
     */
    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace,
            @Nonnull Launcher launcher, @Nonnull TaskListener listener)
            throws InterruptedException, IOException {
        listener.getLogger().println("Recording plot data");
        // add the build to each plot
        for (Plot plot : getPlots()) {
            plot.addBuild(run, listener.getLogger(), workspace);
        }
        run.addAction(new PlotBuildAction(run, getPlots()));
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

    @Extension
    public static final PlotDescriptor DESCRIPTOR = new PlotDescriptor();

    /**
     * The Descriptor for the plot configuration Extension
     *
     * @author Nigel Daley
     * @author Thomas Fox
     */
    public static class PlotDescriptor extends BuildStepDescriptor<Publisher> {

        public PlotDescriptor() {
            super(PlotPublisher.class);
        }

        public String getDisplayName() {
            return Messages.Plot_Pipeline_Publisher_DisplayName();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return AbstractProject.class.isAssignableFrom(jobType)
                    && !MatrixProject.class.isAssignableFrom(jobType);
        }

        /**
         * Called when the user saves the project configuration.
         */
        @Override
        public Publisher newInstance(StaplerRequest req, JSONObject formData)
                throws FormException {
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
         * Called from "config.jelly".
         */
        public FormValidation doCheckSeriesFile(@AncestorInPath Job<?, ?> project,
                @QueryParameter String value) throws IOException {
            FilePath fp = new FilePath(new FilePath(project.getRootDir()), "workspace");
            // Check if workspace folder is missing form root directory
            if (fp.validateFileMask(value) == null) {
                return new FilePath(project.getRootDir()).validateFileMask(value);
            }
            return fp.validateFileMask(value);
        }
    }
}
