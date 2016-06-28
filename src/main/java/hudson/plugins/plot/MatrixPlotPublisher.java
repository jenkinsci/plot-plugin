package hudson.plugins.plot;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.matrix.MatrixConfiguration;
import hudson.matrix.MatrixRun;
import hudson.matrix.MatrixProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.plugins.plot.Messages;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.json.JSONObject;

import org.apache.commons.lang.ObjectUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 *
 * @author lucinka
 */
public class MatrixPlotPublisher extends AbstractPlotPublisher {

    transient private Map<MatrixConfiguration, List<Plot>> plotsOfConfigurations = new HashMap<MatrixConfiguration, List<Plot>>();

    transient private Map<String, List<Plot>> groupMap = new HashMap<String, List<Plot>>();

    /**
     * Configured plots.
     */
    private List<Plot> plots = new ArrayList<Plot>();

    public String urlGroupToOriginalGroup(String urlGroup, MatrixConfiguration c) {
        if (urlGroup == null || "nogroup".equals(urlGroup)) {
            return "Plots";
        }
        if (!plotsOfConfigurations.containsKey(c)) {
            build(c);
        }
        if (groupMap.containsKey(urlGroup)) {
            for (Plot plot : groupMap.get(urlGroup)) {
                if (ObjectUtils.equals(plot.getProject(), c)) {
                    return plot.getGroup();
                }
            }
        }
        return "";
    }

    /**
     * Returns all group names as the original user specified strings.
     */
    public List<String> getOriginalGroups(MatrixConfiguration configuration) {
        List<String> originalGroups = new ArrayList<String>();
        for (String urlGroup : groupMap.keySet()) {
            originalGroups.add(urlGroupToOriginalGroup(urlGroup, configuration));
        }
        Collections.sort(originalGroups);
        return originalGroups;
    }

    /**
     * Reset Configuration and set plots settings for matrixConfiguration
     *
     * @param plots
     *            the new list of plots
     */
    public void setPlots(List<Plot> plots) {
        this.plots = plots;
        groupMap = new HashMap<String, List<Plot>>();
        plotsOfConfigurations = new HashMap<MatrixConfiguration, List<Plot>>();
        for (Plot plot: plots) {
            addPlot(plot);
        }
    }

    /**
     * Adds the new plot to the plot data structures managed by this object.
     *
     * @param plot
     *            the new plot
     */
    public void addPlot(Plot plot) {
        String urlGroup = originalGroupToUrlEncodedGroup(plot.getGroup());
        if (groupMap.containsKey(urlGroup)) {
            List<Plot> list = groupMap.get(urlGroup);
            list.add(plot);
        } else {
            List<Plot> list = new ArrayList<Plot>();
            list.add(plot);
            groupMap.put(urlGroup, list);
        }
        if (plotsOfConfigurations.get((MatrixConfiguration) plot.getProject()) == null) {
            List<Plot> list = new ArrayList<Plot>();
            list.add(plot);
            plotsOfConfigurations.put((MatrixConfiguration) plot.getProject(),
                    list);
        } else {
            plotsOfConfigurations.get((MatrixConfiguration) plot.getProject())
                    .add(plot);
        }
    }

    /**
     * Returns the entire list of plots managed by this object.
     */
    public List<Plot> getPlots(MatrixConfiguration configuration) {
        List<Plot> p = plotsOfConfigurations.get(configuration);
        if (p == null) {
            p = build(configuration);
        }
        return (p != null) ? p : new ArrayList<Plot>();
    }

    public List<Plot> getPlots() {
        return plots;
    }

    /**
     * Returns the list of plots with the given group name. The given group must
     * be the URL friendly form of the group name.
     */
    public List<Plot> getPlots(String urlGroup,
            MatrixConfiguration configuration) {
        List<Plot> plots = getPlots(configuration);
        List<Plot> groupPlots = groupMap.get(urlGroup);
        if (groupPlots == null) {
            groupPlots = new ArrayList<Plot>();
        } else {
            groupPlots = new ArrayList<Plot>(groupPlots);
            groupPlots.retainAll(plots);
        }
        return groupPlots;
    }

    /**
     * Called by Jenkins.
     */
    @Override
    public Action getProjectAction(AbstractProject<?, ?> project) {
        if (project instanceof MatrixConfiguration) {
            return new MatrixPlotAction((MatrixConfiguration) project, this);
        }
        return null;
    }

    @Override
    public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener) {
        if (!plotsOfConfigurations.containsKey((MatrixConfiguration) build
                .getProject())) {
            build((MatrixConfiguration) build.getProject());
        }
        return true;
    }

    private List<Plot> build(MatrixConfiguration configuration) {
        List<Plot> builtPlots = new ArrayList<Plot>(plots.size());
        for (Plot p: plots) {
            Plot plot = new Plot(p.title, p.yaxis, p.group, p.numBuilds,
                    p.csvFileName, p.style, p.useDescr, p.getKeepRecords(),
                    p.getExclZero(), p.isLogarithmic(), p.yaxisMinimum, p.yaxisMaximum);
            plot.series = p.series;
            plot.setProject(configuration);
            addPlot(plot);
            builtPlots.add(plot);
        }
        return builtPlots;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
            BuildListener listener) throws IOException, InterruptedException {
        if (!(build instanceof MatrixRun)) {
            return true;
        }
        listener.getLogger().println("Recording plot data");

        // add the build to each plot
        for (Plot plot : plotsOfConfigurations.get(((MatrixRun) build)
                .getProject())) {
            plot.addBuild(build, listener.getLogger());
        }
        // misconfigured plots will not fail a build so always return true
        return true;
    }

    /**
     * Setup the groupMap upon deserialization.
     */
    private Object readResolve() {
        setPlots(plots);
        return this;
    }

    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    /**
     * Called by Jenkins.
     */
    @Override
    public BuildStepDescriptor<Publisher> getDescriptor() {
        return DESCRIPTOR;
    }

    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        public DescriptorImpl() {
            super(MatrixPlotPublisher.class);
        }

        public String getDisplayName() {
            return Messages.Plot_Publisher_DisplayName();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return MatrixProject.class.isAssignableFrom(jobType);
        }

        /**
         * Called when the user saves the project configuration.
         */
        @Override
        public Publisher newInstance(StaplerRequest req, JSONObject formData)
                throws FormException {
            MatrixPlotPublisher publisher = new MatrixPlotPublisher();
            List<Plot> plots = new ArrayList<Plot>();
            for (Object data : SeriesFactory.getArray(formData.get("plots"))) {
                plots.add(bindPlot((JSONObject) data, req));
            }
            publisher.setPlots(plots);
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
        public FormValidation doCheckSeriesFile(
                @AncestorInPath AbstractProject project,
                @QueryParameter String value) throws IOException {
            return FilePath.validateFileMask(project.getSomeWorkspace(), value);
        }
    }
}
