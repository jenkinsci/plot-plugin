/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.plot;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.matrix.MatrixConfiguration;
import hudson.matrix.MatrixProject;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 *
 * @author lucinka
 */
public class MatrixPlotPublisher extends AbstractPlotPublisher {

    transient private Map<MatrixConfiguration,List<Plot>> plotsOfConfigurations = new HashMap<MatrixConfiguration, List<Plot>>();

    transient private HashMap<String, ArrayList<Plot>> groupMap = new HashMap<String, ArrayList<Plot>>();

    private ArrayList<Plot> plots = new ArrayList<Plot>(); //plots setting for matrix project

    public String urlGroupToOriginalGroup(String urlGroup, MatrixConfiguration c) {
        if (urlGroup == null || "nogroup".equals(urlGroup)) {
            return "Plots";
        }
        if (groupMap.containsKey(urlGroup)) {
            ArrayList<Plot> plots = new ArrayList<Plot>();
            for(Plot plot:groupMap.get(urlGroup)){
                if(plot.getProject().equals(c))
                    plots.add(plot);
            }
            if (plots.size() > 0 ) {
                return plots.get(0).group;
            }
        }
        return "";
    }

    /**
     * Returns all group names as the original user specified strings.
     */
    public String[] getOriginalGroups(MatrixConfiguration configuration) {
        ArrayList<String> originalGroups = new ArrayList<String>();
        for (String urlGroup : groupMap.keySet()) {
            originalGroups.add(urlGroupToOriginalGroup(urlGroup,configuration));
        }
        String[] retVal = originalGroups.toArray(new String[]{});
        Arrays.sort(retVal);
        return retVal;
    }

    /**
     * Reset Configuration and set plots settings for matrixConfiguration
     *
     * @param plots the new list of plots
     */
    public void setPlots(Plot[] p) {
        plots = new ArrayList<Plot>();
        groupMap = new HashMap<String, ArrayList<Plot>>();
        plots.addAll(Arrays.asList(p));
        plotsOfConfigurations = new HashMap<MatrixConfiguration, List<Plot>>();
    }

    /**
     * Adds the new plot to the plot data structures managed by this object.
     *
     * @param plot the new plot
     */
    public void addPlot(Plot plot) {
        String urlGroup = originalGroupToUrlEncodedGroup(plot.getGroup());
        if (groupMap.containsKey(urlGroup)) {
                ArrayList<Plot> list = groupMap.get(urlGroup);
                list.add(plot);
            } else {
                ArrayList<Plot> list = new ArrayList<Plot>();
                list.add(plot);
                groupMap.put(urlGroup, list);
            }
        if(plotsOfConfigurations.get((MatrixConfiguration)plot.getProject())==null){
            ArrayList<Plot> list = new ArrayList<Plot>();
            list.add(plot);
            plotsOfConfigurations.put((MatrixConfiguration)plot.getProject(), list);
        }
        else{
            plotsOfConfigurations.get((MatrixConfiguration)plot.getProject()).add(plot);
        }
    }

    /**
     * Returns the entire list of plots managed by this object.
     */
    public Plot[] getPlots(MatrixConfiguration configuration) {
        List<Plot> configurationPlots = plotsOfConfigurations.get(configuration);
        if(configurationPlots==null){
            return new Plot[]{};
        }
        return plotsOfConfigurations.get(configuration).toArray(new Plot[]{});
    }

    public Plot[] getPlots(){
        Plot[] p = plots.toArray(new Plot[]{});
        return p;
    }

    /**
     * Returns the list of plots with the given group name.  The given
     * group must be the URL friendly form of the group name.
     */
    public Plot[] getPlots(String urlGroup, MatrixConfiguration configuration) {
        List<Plot> groupPlots = new ArrayList<Plot>();
        List<Plot> p = groupMap.get(urlGroup);
        if (p != null) {
            for(Plot plot:p){
                if(plot.getProject().equals(configuration))
                    groupPlots.add(plot);
            }
        }
        return groupPlots.toArray(new Plot[]{});
    }

    /**
     * Called by Jenkins.
     */
    @Override
    public Action getProjectAction(AbstractProject<?, ?> project) {
        if(project instanceof MatrixConfiguration){
            return new MatrixPlotAction((MatrixConfiguration) project, this);
        }
        return null;
    }

    @Override
    public boolean prebuild(AbstractBuild<?, ?> build,BuildListener listener){
            if(!plotsOfConfigurations.containsKey((MatrixConfiguration)build.getProject())){
                for(Plot p:plots){
                    Plot plot = new Plot(p.title,p.yaxis, p.group, p.numBuilds, p.csvFileName, p.style, p.useDescr);
                    plot.series=p.series;
                    plot.setProject((MatrixConfiguration)build.getProject());
                    addPlot(plot);
                }
            }
        return true;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
            BuildListener listener) throws IOException, InterruptedException {
        // Should always be a Build due to isApplicable below
        if (!(build instanceof Build)) {
            return true;
        }
        listener.getLogger().println("Recording plot data");

        // add the build to each plot
        for(Plot plot: plotsOfConfigurations.get((MatrixConfiguration)build.getProject())){
            plot.addBuild((Build) build, listener.getLogger());
        }
        // misconfigured plots will not fail a build so always return true
        return true;
    }

    /**
     * Setup the groupMap upon deserialization.
     */
    private Object readResolve() {
        Plot[] p = plots.toArray(new Plot[]{});
        setPlots(p);
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
        public Publisher newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            MatrixPlotPublisher publisher = new MatrixPlotPublisher();
            ArrayList<Plot> plots = new ArrayList<Plot>();
            for (Object data : SeriesFactory.getArray(formData.get("plots"))) {
                plots.add(bindPlot((JSONObject) data, req));
            }
            Plot pl[] = plots.toArray(new Plot[]{});
            publisher.setPlots(pl);
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
