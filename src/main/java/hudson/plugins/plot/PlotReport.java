/*
 * Copyright (c) 2007 Yahoo! Inc.  All rights reserved.
 * Copyrights licensed under the MIT License.
 */
package hudson.plugins.plot;

import au.com.bytecode.opencsv.CSVReader;
import hudson.model.AbstractProject;
import hudson.model.Job;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * Represents a plot report for a single group of plots.
 *
 * @author Nigel Daley
 */
public class PlotReport {
    private static final Logger LOGGER = Logger.getLogger(PlotReport.class.getName());
    private final Job<?, ?> project;

    /**
     * The sorted list of plots that belong to the same group.
     */
    private List<Plot> plots;

    /**
     * The group these plots belong to.
     */
    private String group;

    public PlotReport(AbstractProject<?, ?> project, String group,
                      List<Plot> plots) {
        this((Job) project, group, plots);
    }

    public PlotReport(Job<?, ?> job, String group,
                      List<Plot> plots) {
        Collections.sort(plots);
        this.plots = plots;
        this.group = group;
        this.project = job;
    }

    @Deprecated
    public AbstractProject<?, ?> getProject() {
        return project instanceof AbstractProject ? (AbstractProject<?, ?>) project : null;
    }

    // called from PlotReport/index.jelly
    public Job<?, ?> getJob() {
        return project;
    }

    // called from PlotReport/index.jelly
    public String getGroup() {
        return group;
    }

    // called from PlotReport/index.jelly
    public List<Plot> getPlots() {
        return plots;
    }

    // called from PlotReport/index.jelly
    public void doGetPlot(StaplerRequest req, StaplerResponse rsp) {
        String i = req.getParameter("index");
        Plot plot = getPlot(i);
        try {
            plot.plotGraph(req, rsp);
        } catch (IOException ioe) {
            LOGGER.log(Level.SEVERE, "Exception plotting graph", ioe);
        }
    }

    // called from PlotReport/index.jelly
    public void doGetPlotMap(StaplerRequest req, StaplerResponse rsp) {
        String i = req.getParameter("index");
        Plot plot = getPlot(i);
        try {
            plot.plotGraphMap(req, rsp);
        } catch (IOException ioe) {
            LOGGER.log(Level.SEVERE, "Exception plotting graph", ioe);
        }
    }

    // called from PlotReport/index.jelly
    public boolean getDisplayTableFlag(int i) {
        Plot plot = getPlot(i);

        if (CollectionUtils.isNotEmpty(plot.getSeries())) {
            Series series = plot.getSeries().get(0);
            return (series instanceof CSVSeries) && ((CSVSeries) series).getDisplayTableFlag();
        }
        return false;
    }

    // called from PlotReport/index.jelly
    public List<List<String>> getTable(int i) {
        List<List<String>> tableData = new ArrayList<>();

        Plot plot = getPlot(i);

        // load existing csv file
        File plotFile = new File(project.getRootDir(), plot.getCsvFileName());
        if (!plotFile.exists()) {
            return tableData;
        }
        CSVReader reader = null;
        try {
            reader = new CSVReader(new InputStreamReader(new FileInputStream(plotFile),
                    Charset.defaultCharset().name()));
            // throw away 2 header lines
            reader.readNext();
            reader.readNext();
            // array containing header titles
            List<String> header = new ArrayList<>();
            header.add(Messages.Plot_Build() + " #");
            tableData.add(header);
            String[] nextLine;
            while ((nextLine = reader.readNext()) != null) {
                String buildNumber = nextLine[2];
                if (!plot.reportBuild(Integer.parseInt(buildNumber))) {
                    continue;
                }
                String seriesLabel = nextLine[1];
                // index of the column where the value should be located
                int index = header.lastIndexOf(seriesLabel);
                if (index <= 0) {
                    // add header label
                    index = header.size();
                    header.add(seriesLabel);
                }
                List<String> tableRow = null;
                for (int j = 1; j < tableData.size(); j++) {
                    List<String> r = tableData.get(j);
                    if (StringUtils.equals(r.get(0), buildNumber)) {
                        // found table row corresponding to the build number
                        tableRow = r;
                        break;
                    }
                }
                // table row corresponding to the build number not found
                if (tableRow == null) {
                    // create table row with build number at first column
                    tableRow = new ArrayList<>();
                    tableRow.add(buildNumber);
                    tableData.add(tableRow);
                }
                // set value at index column
                String value = nextLine[0];
                if (index < tableRow.size()) {
                    tableRow.set(index, value);
                } else {
                    for (int j = tableRow.size(); j < index; j++) {
                        tableRow.add(StringUtils.EMPTY);
                    }
                    tableRow.add(value);
                }
            }
            int lastColumn = tableData.get(0).size();
            for (List<String> tableRow : tableData) {
                for (int j = tableRow.size(); j < lastColumn; j++) {
                    tableRow.add(StringUtils.EMPTY);
                }
            }
        } catch (IOException ioe) {
            LOGGER.log(Level.SEVERE, "Exception reading csv file", ioe);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    LOGGER.log(Level.INFO, "Failed to close CSV reader", e);
                }
            }
        }
        return tableData;
    }

    private Plot getPlot(int i) {
        Plot p = plots.get(i);
        p.setJob(project);
        return p;
    }

    private Plot getPlot(String i) {
        try {
            return getPlot(Integer.parseInt(i));
        } catch (NumberFormatException ignore) {
            LOGGER.log(Level.SEVERE, "Exception converting to integer", ignore);
            return null;
        }
    }
}
