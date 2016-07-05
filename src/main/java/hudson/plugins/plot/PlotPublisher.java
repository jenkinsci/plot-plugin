/*
 * Copyright (c) 2007-2009 Yahoo! Inc.  All rights reserved.
 * The copyrights to the contents of this file are licensed under the MIT License (http://www.opensource.org/licenses/mit-license.php)
 */
package hudson.plugins.plot;

import hudson.Launcher;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.commons.collections.CollectionUtils;

/**
 * Records the plot data for builds.
 *
 * @author Nigel Daley
 */
public class PlotPublisher extends AbstractPlotPublisher {

    private static final Logger LOGGER = Logger.getLogger(PlotPublisher.class
            .getName());
    /**
     * Array of Plot objects that represent the job's configured plots; must be
     * non-null
     */
    private List<Plot> plots = new ArrayList<Plot>();
    /**
     * Maps plot groups to plot objects; group strings are in a URL friendly
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
     * Converts a URL friendly plot group name to the original group name. If
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
     * @param plots the new list of plots
     */
    public void setPlots(List<Plot> plots) {
        this.plots = new ArrayList<Plot>();
        groupMap = new HashMap<String, List<Plot>>();
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

    /**
     * Called by Jenkins.
     */
    @Override
    public Action getProjectAction(AbstractProject<?, ?> project) {
        return new PlotAction(project, this);
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


        List<Plot> newPlotList = new ArrayList<Plot>();
        for (Plot plot : getPlots()) {
            BufferedReader reader = null;
            List<String> groupList = new ArrayList<String>();
            try {
                String namePath = new File(build.getWorkspace().toString()).getParentFile().getAbsolutePath() + "/" + plot.group;
                File identifyNameFile = new File(namePath);
                if (!identifyNameFile.getParentFile().exists()) {
                    identifyNameFile.getParentFile().mkdirs();
                }
                reader = new BufferedReader(new InputStreamReader(new FileInputStream(identifyNameFile), "utf-8"));
                String line = null;
                while ((line = reader.readLine()) != null) {
                    groupList.add(line);
                }

            } catch (IOException e) {

            } finally {
                try {
                    reader.close();
                } catch (Exception e) {

                }
            }

            for (String group : groupList) {
                Plot newPlot = new Plot(plot.title, plot.yaxis, group, plot.numBuilds,
                        plot.csvFileName.replace("plot-", group + "-plot-"), plot.style, plot.useDescr,
                        plot.getKeepRecords(), plot.exclZero, plot.logarithmic,
                        plot.yaxisMinimum, plot.yaxisMaximum);

                List<Series> seriesList = new ArrayList<Series>();
                for (Series seriesItem : plot.series) {
                    Series series = null;
                    if ("csv".equals(seriesItem.fileType)) {
                        CSVSeries temp = (CSVSeries) seriesItem;
                        series = new CSVSeries(temp.file.replace("${group}", group), temp.getUrl(), temp.getInclusionFlag(), temp.getExclusionValues(), temp.getDisplayTableFlag());
                    } else if ("xml".equals(seriesItem.fileType)) {
                        XMLSeries temp = (XMLSeries) seriesItem;
                        series = new XMLSeries(temp.file.replace("${group}", group), temp.getXpath(), temp.getNodeType(), temp.getUrl());
                    } else if ("properties".equals(seriesItem.fileType)) {
                        PropertiesSeries temp = (PropertiesSeries) seriesItem;
                        series = new PropertiesSeries(temp.file.replace("${group}", group), temp.label);
                    }
                    if (series != null) {
                        seriesList.add(series);
                    }
                }
                newPlot.series = seriesList;
                newPlotList.add(newPlot);
            }
        }

        groupMap = new HashMap<String, List<Plot>>();
        for (Plot plot : newPlotList) {
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

        // add the build to each plot
        for (Plot plot : newPlotList) {
            plot.addBuild(build, listener.getLogger());
        }
        // misconfigured plots will not fail a build so always return true
        return true;
    }

    public static final PlotDescriptor DESCRIPTOR = new PlotDescriptor();
}
