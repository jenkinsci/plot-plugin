/*
 * Copyright (c) 2007-2009 Yahoo! Inc.  All rights reserved.
 * The copyrights to the contents of this file are licensed under the MIT License
 * (http://www.opensource.org/licenses/mit-license.php)
 */
package hudson.plugins.plot;

import static org.jfree.chart.plot.PlotOrientation.VERTICAL;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvValidationException;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Job;
import hudson.model.Run;
import hudson.util.ChartUtil;
import hudson.util.ShiftedCategoryAxis;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Polygon;
import java.awt.Shape;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartRenderingInfo;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.axis.LogarithmicAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.labels.StandardCategoryToolTipGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.DefaultDrawingSupplier;
import org.jfree.chart.plot.DrawingSupplier;
import org.jfree.chart.renderer.category.AbstractCategoryItemRenderer;
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.StaplerResponse2;

/**
 * Represents the configuration for a single plot. A plot can have one or more
 * data series (lines). Each data series has one data point per build. The
 * x-axis is always the build number.
 * <p>
 * A plot has the following characteristics:
 * <ul>
 * <li>a title (mandatory)
 * <li>y-axis label (defaults to no label)
 * <li>one or more data series
 * <li>plot group (defaults to no group)
 * <li>number of builds to show on the plot (defaults to all)
 * </ul>
 * <p>
 * A plots group effects the way in which plots are displayed. Group names are
 * listed as links on the top-level plot page. The user then clicks on a group
 * and sees the plots that belong to that group.
 *
 * @author Nigel Daley
 */
public class Plot implements Comparable<Plot> {
    private static final Logger LOGGER = Logger.getLogger(Plot.class.getName());
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM d");

    /**
     * Effectively a 2-dimensional array, where each row is the data for one
     * data series of an individual build; the columns are: series y-value,
     * series label, build number, optional URL
     */
    private transient List<String[]> rawPlotData;

    /**
     * The generated plot, which is only regenerated when new data is added (it
     * is re-rendered, however, every time it is requested).
     */
    private transient JFreeChart plot;

    /**
     * The project (or job) that this plot belongs to. A reference to the
     * project is needed to retrieve and save the CSV file that is stored in the
     * project's root directory.
     */
    private transient Job<?, ?> project;

    /**
     * All plots share the same JFreeChart drawing supplier object.
     */
    private static final DrawingSupplier SUPPLIER = new DefaultDrawingSupplier(
            DefaultDrawingSupplier.DEFAULT_PAINT_SEQUENCE,
            DefaultDrawingSupplier.DEFAULT_OUTLINE_PAINT_SEQUENCE,
            DefaultDrawingSupplier.DEFAULT_STROKE_SEQUENCE,
            DefaultDrawingSupplier.DEFAULT_OUTLINE_STROKE_SEQUENCE,
            // the plot data points are a small diamond shape
            new Shape[] {new Polygon(new int[] {3, 0, -3, 0}, new int[] {0, 4, 0, -4}, 4)});

    /**
     * The default plot width.
     */
    private static final int DEFAULT_WIDTH = 750;

    /**
     * The default plot height.
     */
    private static final int DEFAULT_HEIGHT = 450;

    // Transient values

    /**
     * The width of the plot.
     */
    private transient int width;

    /**
     * The height of the plot.
     */
    private transient int height;

    /**
     * The right-most build number on the plot.
     */
    private transient int rightBuildNum;

    /**
     * Whether or not the plot has a legend.
     */
    private transient boolean hasLegend = true;

    /**
     * Number of builds back to show on this plot from url.
     */
    @SuppressWarnings("visibilitymodifier")
    public transient String urlNumBuilds;

    /**
     * Title of plot from url.
     */
    @SuppressWarnings("visibilitymodifier")
    public transient String urlTitle;

    /**
     * Style of plot from url.
     */
    @SuppressWarnings("visibilitymodifier")
    public transient String urlStyle;

    /**
     * Use description flag from url.
     */
    @SuppressWarnings("visibilitymodifier")
    public transient Boolean urlUseDescr;

    // Configuration values

    /**
     * Title of plot. Mandatory.
     */
    @SuppressWarnings("visibilitymodifier")
    public String title;

    /**
     * Description of plot. Optional.
     */
    @SuppressWarnings("visibilitymodifier")
    public String description;

    /**
     * Y-axis label. Optional.
     */
    @SuppressWarnings("visibilitymodifier")
    public String yaxis;

    /**
     * List of data series.
     */
    @SuppressWarnings("visibilitymodifier")
    public List<Series> series;

    /**
     * Group name that this plot belongs to.
     */
    @SuppressWarnings("visibilitymodifier")
    public String group;

    /**
     * Number of builds back to show on this plot. Empty string means all
     * builds. Must not be "0".
     */
    @SuppressWarnings("visibilitymodifier")
    public String numBuilds;

    /**
     * The name of the CSV file that persists the plots data. The CSV file is
     * stored in the projects root directory. This is different from the source
     * csv file that can be used as a source for the plot.
     */
    @SuppressWarnings("visibilitymodifier")
    public String csvFileName;

    /**
     * The date of the last change to the CSV file.
     */
    private long csvLastModification;

    /**
     * Optional style of plot: line, line3d, stackedArea, stackedBar, etc.
     */
    @SuppressWarnings("visibilitymodifier")
    public String style;

    /**
     * Whether or not to use build descriptions as X-axis labels. Optional.
     */
    @SuppressWarnings("visibilitymodifier")
    public boolean useDescr;

    /**
     * Keep records for builds that were deleted.
     */
    private boolean keepRecords;

    /**
     * Whether or not to exclude zero as default Y-axis value. Optional.
     */
    @SuppressWarnings("visibilitymodifier")
    public boolean exclZero;

    /**
     * Use a logarithmic Y-axis.
     */
    @SuppressWarnings("visibilitymodifier")
    public boolean logarithmic;

    /**
     * Min/max yaxis values, string used so if no value defaults used
     */
    @SuppressWarnings("visibilitymodifier")
    public String yaxisMinimum;

    @SuppressWarnings("visibilitymodifier")
    public String yaxisMaximum;

    static class Label implements Comparable<Label> {
        private final Integer buildNum;
        private final String buildDate;
        private final String text;

        public Label(String buildNum, String buildTime, String text) {
            this.buildNum = Integer.parseInt(buildNum);
            synchronized (DATE_FORMAT) {
                this.buildDate = DATE_FORMAT.format(new Date(Long.parseLong(buildTime)));
            }
            this.text = text;
        }

        public Label(String buildNum, String buildTime) {
            this(buildNum, buildTime, null);
        }

        public int compareTo(Label that) {
            return this.buildNum - that.buildNum;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Label && ((Label) o).buildNum.equals(buildNum);
        }

        @Override
        public int hashCode() {
            return buildNum.hashCode();
        }

        public String numDateString() {
            return "#" + buildNum + " (" + buildDate + ")";
        }

        @Override
        public String toString() {
            return text != null ? text : numDateString();
        }
    }

    private enum ChartStyle {
        AREA("area"),
        BAR("bar"),
        BAR_3D("bar3d"),
        LINE("line"),
        LINE_3D("line3d"),
        LINE_SIMPLE("lineSimple"),
        STACKED_AREA("stackedArea"),
        STACKED_BAR("stackedBar"),
        STACKED_BAR_3D("stackedBar3d"),
        WATERFALL("waterfall");

        private final String name;

        ChartStyle(String name) {
            this.name = name;
        }

        static ChartStyle forName(String name) {
            for (ChartStyle chartStyle : ChartStyle.values()) {
                if (name.equalsIgnoreCase(chartStyle.name)) {
                    return chartStyle;
                }
            }
            return ChartStyle.LINE_SIMPLE;
        }
    }

    /**
     * Creates a new plot with the given parameters. If numBuilds is the empty
     * string, then all builds will be included. Must not be zero.
     */
    @SuppressWarnings("parameternumber")
    @DataBoundConstructor
    public Plot(
            String title,
            String yaxis,
            String group,
            String numBuilds,
            String csvFileName,
            String style,
            boolean useDescr,
            boolean keepRecords,
            boolean exclZero,
            boolean logarithmic,
            String yaxisMinimum,
            String yaxisMaximum,
            String description) {
        this.title = title;
        this.yaxis = yaxis;
        this.group = group;
        this.numBuilds = numBuilds;
        this.csvFileName = csvFileName;
        this.style = style;
        this.useDescr = useDescr;
        this.keepRecords = keepRecords;
        this.exclZero = exclZero;
        this.logarithmic = logarithmic;
        this.yaxisMinimum = yaxisMinimum;
        this.yaxisMaximum = yaxisMaximum;
        this.description = description;
    }

    /**
     * @deprecated Kept for backward compatibility.
     */
    @Deprecated
    public Plot(
            String title,
            String yaxis,
            String group,
            String numBuilds,
            String csvFileName,
            String style,
            boolean useDescr) {
        this(title, yaxis, group, numBuilds, csvFileName, style, useDescr, false, false, false, null, null, null);
    }

    // needed for serialization
    public Plot() {}

    public boolean getKeepRecords() {
        return keepRecords;
    }

    public boolean getExclZero() {
        return exclZero;
    }

    public boolean isLogarithmic() {
        return logarithmic;
    }

    public boolean hasYaxisMinimum() {
        return (getYaxisMinimum() != null);
    }

    public Double getYaxisMinimum() {
        return getDoubleFromString(yaxisMinimum);
    }

    public boolean hasYaxisMaximum() {
        return (getYaxisMaximum() != null);
    }

    public Double getYaxisMaximum() {
        return getDoubleFromString(yaxisMaximum);
    }

    public Double getDoubleFromString(String input) {
        Double result = null;
        if (!StringUtils.isEmpty(input)) {
            try {
                result = Double.parseDouble(input);
            } catch (NumberFormatException nfe) {
                LOGGER.log(
                        Level.INFO,
                        "Failed to parse double from '" + input + "'. Not a problem, result already set",
                        nfe);
            }
        }
        return result;
    }

    public int compareTo(Plot o) {
        if (title == null) {
            return o == null || o.getTitle() == null ? 0 : -1;
        }
        if (o == null || o.getTitle() == null) {
            return 1;
        }
        return title.compareTo(o.getTitle());
    }

    public boolean equals(Object o) {
        return o instanceof Plot && this.compareTo((Plot) o) == 0;
    }

    @Override
    public int hashCode() {
        return this.title.hashCode();
    }

    @Override
    public String toString() {
        return "TITLE(" + getTitle() + "),YAXIS(" + yaxis + "),NUMSERIES("
                + CollectionUtils.size(series) + "),GROUP(" + group
                + "),NUMBUILDS(" + numBuilds + "),RIGHTBUILDNUM("
                + getRightBuildNum() + "),HASLEGEND(" + hasLegend()
                + "),ISLOGARITHMIC(" + isLogarithmic() + "),YAXISMINIMUM("
                + yaxisMinimum + "),YAXISMAXIMUM(" + yaxisMaximum
                + "),FILENAME(" + getCsvFileName() + "),DESCRIPTION("
                + getDescription() + ")";
    }

    public String getYaxis() {
        return yaxis;
    }

    public List<Series> getSeries() {
        return series;
    }

    public String getGroup() {
        return group;
    }

    public String getCsvFileName() {
        if (StringUtils.isBlank(csvFileName) && project != null) {
            try {
                csvFileName = File.createTempFile("plot-", ".csv", project.getRootDir())
                        .getName();
                LOGGER.log(Level.WARNING, "Loading " + csvFileName);
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Unable to create temporary CSV file.", e);
            }
        }
        return csvFileName;
    }

    /**
     * Sets the title for the plot from the "title" parameter in the given
     * StaplerRequest.
     */
    private void setTitle(StaplerRequest2 req) {
        urlTitle = req.getParameter("title");
    }

    private String getURLTitle() {
        return urlTitle != null ? urlTitle : title;
    }

    public String getTitle() {
        return title;
    }

    private void setStyle(StaplerRequest2 req) {
        urlStyle = req.getParameter("style");
    }

    private String getUrlStyle() {
        return urlStyle != null ? urlStyle : (style != null ? style : "");
    }

    private void setUseDescr(StaplerRequest2 req) {
        String u = req.getParameter("usedescr");
        if (u == null) {
            urlUseDescr = null;
        } else {
            urlUseDescr = "on".equalsIgnoreCase(u) || "true".equalsIgnoreCase(u);
        }
    }

    private boolean getUrlUseDescr() {
        return urlUseDescr != null ? urlUseDescr : useDescr;
    }

    /**
     * Sets the "hasLegend" parameter in the given StaplerRequest. If the
     * parameter doesn't exist then a default is used.
     */
    private void setHasLegend(StaplerRequest2 req) {
        String legend = req.getParameter("haslegend");
        hasLegend = legend == null || "on".equalsIgnoreCase(legend) || "true".equalsIgnoreCase(legend);
    }

    public boolean hasLegend() {
        return hasLegend;
    }

    /**
     * Sets the number of builds to plot from the "numbuilds" parameter in the
     * given StaplerRequest. If the parameter doesn't exist or isn't an integer
     * then a default is used.
     */
    private void setNumBuilds(StaplerRequest2 req) {
        urlNumBuilds = req.getParameter("numbuilds");
        if (urlNumBuilds != null) {
            try {
                // simply try and parse the string to see if it's a valid
                // number, throw away the result.
                Integer.parseInt(urlNumBuilds);
            } catch (NumberFormatException nfe) {
                urlNumBuilds = null;
            }
        }
    }

    public String getURLNumBuilds() {
        return urlNumBuilds != null ? urlNumBuilds : numBuilds;
    }

    public String getNumBuilds() {
        return numBuilds;
    }

    /**
     * Sets the description of the plot from the "description" parameter in the
     * given StaplerRequest. If the parameter doesn't exist or isn't an string
     * then a default is used.
     */
    private void setDescription(StaplerRequest2 req) {
        description = req.getParameter("description");
    }

    public String getDescription() {
        return description;
    }

    /**
     * Sets the right-most build number shown on the plot from the
     * "rightbuildnum" parameter in the given StaplerRequest. If the parameter
     * doesn't exist or isn't an integer then a default is used.
     */
    private void setRightBuildNum(StaplerRequest2 req) {
        String build = req.getParameter("rightbuildnum");
        if (StringUtils.isBlank(build)) {
            rightBuildNum = Integer.MAX_VALUE;
        } else {
            try {
                rightBuildNum = Integer.parseInt(build);
            } catch (NumberFormatException nfe) {
                LOGGER.log(Level.SEVERE, "Exception converting to integer", nfe);
                rightBuildNum = Integer.MAX_VALUE;
            }
        }
    }

    private int getRightBuildNum() {
        return rightBuildNum;
    }

    /**
     * Sets the plot width from the "width" parameter in the given
     * StaplerRequest. If the parameter doesn't exist or isn't an integer then a
     * default is used.
     */
    private void setWidth(StaplerRequest2 req) {
        String w = req.getParameter("width");
        if (w == null) {
            width = DEFAULT_WIDTH;
        } else {
            try {
                width = Integer.parseInt(w);
            } catch (NumberFormatException nfe) {
                LOGGER.log(Level.SEVERE, "Exception converting to integer", nfe);
                width = DEFAULT_WIDTH;
            }
        }
    }

    private int getWidth() {
        return width;
    }

    /**
     * Sets the plot height from the "height" parameter in the given
     * StaplerRequest. If the parameter doesn't exist or isn't an integer then a
     * default is used.
     */
    private void setHeight(StaplerRequest2 req) {
        String h = req.getParameter("height");
        if (h == null) {
            height = DEFAULT_HEIGHT;
        } else {
            try {
                height = Integer.parseInt(h);
            } catch (NumberFormatException nfe) {
                LOGGER.log(Level.SEVERE, "Exception converting to integer", nfe);
                height = DEFAULT_HEIGHT;
            }
        }
    }

    private int getHeight() {
        return height;
    }

    public Job<?, ?> getJob() {
        return project;
    }

    public void setJob(Job<?, ?> job) {
        this.project = job;
    }

    @Deprecated
    public AbstractProject<?, ?> getProject() {
        return (AbstractProject<?, ?>) project;
    }

    /**
     * A reference to the project is needed to retrieve the project's root
     * directory where the CSV file is located. Unfortunately, a reference to
     * the project is not available when this object is created.
     */
    public void setProject(AbstractProject<?, ?> project) {
        this.project = project;
    }

    /**
     * Generates and writes the plot to the response output stream.
     *
     * @param req the incoming request
     * @param rsp the response stream
     * @throws IOException
     */
    public void plotGraph(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
        if (ChartUtil.awtProblemCause != null) {
            // Not available. Send out error message.
            rsp.sendRedirect2(req.getContextPath() + "/images/headless.png");
            return;
        }
        setWidth(req);
        setHeight(req);
        setNumBuilds(req);
        setRightBuildNum(req);
        setHasLegend(req);
        setTitle(req);
        setStyle(req);
        setUseDescr(req);
        // need to force regenerate the plot in case build
        // descriptions (used for tool tips) have changed
        generatePlot(true);
        ChartUtil.generateGraph(
                StaplerRequest.fromStaplerRequest2(req),
                StaplerResponse.fromStaplerResponse2(rsp),
                plot,
                getWidth(),
                getHeight());
    }

    /**
     * Generates and writes the plot's clickable map to the response output
     * stream.
     *
     * @param req the incoming request
     * @param rsp the response stream
     * @throws IOException
     */
    public void plotGraphMap(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
        if (ChartUtil.awtProblemCause != null) {
            // not available. send out error message
            rsp.sendRedirect2(req.getContextPath() + "/images/headless.png");
            return;
        }
        setWidth(req);
        setHeight(req);
        setNumBuilds(req);
        setRightBuildNum(req);
        setHasLegend(req);
        setTitle(req);
        setStyle(req);
        setUseDescr(req);
        generatePlot(false);
        ChartRenderingInfo info = new ChartRenderingInfo();
        plot.createBufferedImage(getWidth(), getHeight(), info);
        rsp.setContentType("text/plain;charset=UTF-8");
        rsp.getWriter().println(ChartUtilities.getImageMap(getCsvFileName(), info));
    }

    /**
     * @see #addBuild(Run, PrintStream, FilePath)
     */
    public void addBuild(AbstractBuild<?, ?> build, PrintStream logger) {
        addBuild(build, logger, build.getWorkspace());
    }

    /**
     * Called when a build completes. Adds the finished build to this plot. This
     * method extracts the data for each data series from the build and saves it
     * in the plot's CSV file.
     */
    public void addBuild(Run<?, ?> run, PrintStream logger, FilePath workspace) {
        if (project == null) {
            project = run.getParent();
        }

        // load the existing plot data from disk
        loadPlotData();
        // extract the data for each data series
        for (Series s : getSeries()) {
            if (s == null) {
                continue;
            }
            List<PlotPoint> seriesData = s.loadSeries(workspace, run.getNumber(), logger);
            if (seriesData != null) {
                for (PlotPoint point : seriesData) {
                    if (point == null) {
                        continue;
                    }

                    rawPlotData.add(new String[] {
                        point.getYvalue(),
                        point.getLabel(),
                        run.getNumber() + "", // convert to a string
                        run.getTimestamp().getTimeInMillis() + "",
                        point.getUrl()
                    });
                }
            }
        }

        // save the updated plot data to disk
        savePlotData();
    }

    /**
     * Generates the plot and stores it in the plot instance variable.
     *
     * @param forceGenerate if true, force the plot to be re-generated even if the on-disk
     *                      data hasn't changed
     */
    private void generatePlot(boolean forceGenerate) {
        // LOGGER.info("Determining if we should generate plot " +
        // getCsvFileName());
        File csvFile = new File(project.getRootDir(), getCsvFileName());
        if (csvFile.lastModified() == csvLastModification && plot != null && !forceGenerate) {
            // data hasn't changed so don't regenerate the plot
            return;
        }
        if (rawPlotData == null || csvFile.lastModified() > csvLastModification) {
            // data has changed or has not been loaded so load it now
            loadPlotData();
        }
        // LOGGER.info("Generating plot " + getCsvFileName());
        csvLastModification = csvFile.lastModified();
        PlotCategoryDataset dataset = new PlotCategoryDataset();
        for (String[] record : rawPlotData) {
            // record: series y-value, series label, build number, build date,
            // url
            int buildNum;
            try {
                buildNum = Integer.parseInt(record[2]);
                if (!reportBuild(buildNum) || buildNum > getRightBuildNum()) {
                    continue; // skip this record
                }
            } catch (NumberFormatException nfe) {
                LOGGER.log(Level.SEVERE, "Exception converting to integer", nfe);
                continue; // skip this record all together
            }
            Number value;
            try {
                value = Integer.parseInt(record[0]);
            } catch (NumberFormatException nfe) {
                try {
                    value = Double.parseDouble(record[0]);
                } catch (NumberFormatException nfe2) {
                    LOGGER.log(Level.SEVERE, "Exception converting to number", nfe2);
                    continue; // skip this record all together
                }
            }
            Label columnXLabel = getUrlUseDescr()
                    ? new Label(record[2], record[3], descriptionForBuild(buildNum))
                    : new Label(record[2], record[3]);
            String url = null;
            if (record.length >= 5) {
                url = record[4];
            }
            String rowSeries = record[1];
            dataset.setValue(value, url, rowSeries, columnXLabel);
        }

        String builds = getURLNumBuilds();
        int buildsNumber;
        if (StringUtils.isBlank(builds)) {
            buildsNumber = Integer.MAX_VALUE;
        } else {
            try {
                buildsNumber = Integer.parseInt(builds);
            } catch (NumberFormatException nfe) {
                LOGGER.log(Level.SEVERE, "Exception converting to integer", nfe);
                buildsNumber = Integer.MAX_VALUE;
            }
        }

        dataset.clipDataset(buildsNumber);
        plot = createChart(dataset);
        CategoryPlot categoryPlot = (CategoryPlot) plot.getPlot();
        categoryPlot.setDomainGridlinePaint(Color.black);
        categoryPlot.setRangeGridlinePaint(Color.black);
        categoryPlot.setDrawingSupplier(Plot.SUPPLIER);
        CategoryAxis domainAxis = new ShiftedCategoryAxis(Messages.Plot_Build());
        categoryPlot.setDomainAxis(domainAxis);
        domainAxis.setCategoryLabelPositions(CategoryLabelPositions.UP_90);
        domainAxis.setLowerMargin(0.0);
        domainAxis.setUpperMargin(0.03);
        domainAxis.setCategoryMargin(0.0);
        for (Object category : dataset.getColumnKeys()) {
            Label label = (Label) category;
            if (label.text != null) {
                domainAxis.addCategoryLabelToolTip(label, label.numDateString());
            } else {
                domainAxis.addCategoryLabelToolTip(label, descriptionForBuild(label.buildNum));
            }
        }
        // Replace the range axis by a logarithmic axis if the option is
        // selected
        if (isLogarithmic()) {
            LogarithmicAxis logAxis = new LogarithmicAxis(getYaxis());
            categoryPlot.setRangeAxis(logAxis);
        }

        // optionally exclude zero as default y-axis value
        ValueAxis rangeAxis = categoryPlot.getRangeAxis();
        if ((rangeAxis != null) && (rangeAxis instanceof NumberAxis)) {
            if (hasYaxisMinimum()) {
                rangeAxis.setLowerBound(getYaxisMinimum());
            }
            if (hasYaxisMaximum()) {
                rangeAxis.setUpperBound(getYaxisMaximum());
            }
            ((NumberAxis) rangeAxis).setAutoRangeIncludesZero(!getExclZero());
        }

        AbstractCategoryItemRenderer renderer = (AbstractCategoryItemRenderer) categoryPlot.getRenderer();
        int numColors = dataset.getRowCount();
        for (int i = 0; i < numColors; i++) {
            renderer.setSeriesPaint(i, new Color(Color.HSBtoRGB((1f / numColors) * i, 1f, 1f)));
        }
        renderer.setBaseStroke(new BasicStroke(2.0f));
        renderer.setBaseToolTipGenerator(
                new StandardCategoryToolTipGenerator(Messages.Plot_Build() + " {1}: {2}", NumberFormat.getInstance()));
        renderer.setBaseItemURLGenerator(new PointURLGenerator());
        if (renderer instanceof LineAndShapeRenderer lasRenderer) {
            String s = getUrlStyle();
            lasRenderer.setShapesVisible(!"lineSimple".equalsIgnoreCase(s));
        }
    }

    /**
     * Creates a Chart of the style indicated by getUrlStyle() using the given
     * dataset. Defaults to using createLineChart.
     */
    // spotless:off
    private JFreeChart createChart(PlotCategoryDataset dataset) {
        return switch (ChartStyle.forName(getUrlStyle())) {
            case AREA ->
                ChartFactory.createAreaChart(
                        getURLTitle(), null, getYaxis(), dataset, VERTICAL, hasLegend(), true, false);
            case BAR ->
                ChartFactory.createBarChart(
                        getURLTitle(), null, getYaxis(), dataset, VERTICAL, hasLegend(), true, false);
            case BAR_3D ->
                ChartFactory.createBarChart3D(
                        getURLTitle(), null, getYaxis(), dataset, VERTICAL, hasLegend(), true, false);
            case LINE_3D ->
                ChartFactory.createLineChart3D(
                        getURLTitle(), null, getYaxis(), dataset, VERTICAL, hasLegend(), true, false);
            case LINE_SIMPLE ->
                ChartFactory.createLineChart(
                        getURLTitle(), null, getYaxis(), dataset, VERTICAL, hasLegend(), true, false);
            case STACKED_AREA ->
                ChartFactory.createStackedAreaChart(
                        getURLTitle(), null, getYaxis(), dataset, VERTICAL, hasLegend(), true, false);
            case STACKED_BAR ->
                ChartFactory.createStackedBarChart(
                        getURLTitle(), null, getYaxis(), dataset, VERTICAL, hasLegend(), true, false);
            case STACKED_BAR_3D ->
                ChartFactory.createStackedBarChart3D(
                        getURLTitle(), null, getYaxis(), dataset, VERTICAL, hasLegend(), true, false);
            case WATERFALL ->
                ChartFactory.createWaterfallChart(
                        getURLTitle(), null, getYaxis(), dataset, VERTICAL, hasLegend(), true, false);
            default ->
                ChartFactory.createLineChart(
                        getURLTitle(), null, getYaxis(), dataset, VERTICAL, hasLegend(), true, false);
        };
    }
    // spotless:on

    /**
     * Returns a trimmed description string for the build specified by the given
     * build number.
     */
    private String descriptionForBuild(int buildNum) {
        Run r = project.getBuildByNumber(buildNum);
        if (r != null) {
            String tip = r.getTruncatedDescription();
            if (tip != null) {
                return tip.replaceAll("<p> *|<br> *", ", ");
            }
        }
        return null;
    }

    /**
     * Loads the plot data from the CSV file on disk. The CSV file is stored in
     * the projects root directory. The data is stored in the rawPlotData
     * instance variable.
     */
    private void loadPlotData() {
        rawPlotData = new ArrayList<>();
        // load existing plot file
        File plotFile = new File(project.getRootDir(), getCsvFileName());
        if (!plotFile.exists()) {
            return;
        }
        CSVReader reader = null;
        rawPlotData = new ArrayList<>();
        try {
            reader = new CSVReader(new InputStreamReader(new FileInputStream(plotFile), Charset.defaultCharset()));
            // throw away 2 header lines
            reader.readNext();
            reader.readNext();
            // read each line of the CSV file and add to rawPlotData
            String[] nextLine;
            while ((nextLine = reader.readNext()) != null) {
                rawPlotData.add(nextLine);
            }
        } catch (CsvValidationException | IOException ioe) {
            LOGGER.log(Level.SEVERE, "Exception reading plot file", ioe);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Failed to close plot reader", e);
                }
            }
        }
    }

    /**
     * Saves the plot data to the CSV file on disk. The CSV file is stored in
     * the projects root directory. The data is read from the rawPlotData
     * instance variable.
     */
    private void savePlotData() {
        File plotFile = new File(project.getRootDir(), getCsvFileName());
        CSVWriter writer = null;
        try {
            writer = new CSVWriter(new OutputStreamWriter(new FileOutputStream(plotFile), Charset.defaultCharset()));
            // write 2 header lines
            String[] header1 = new String[] {Messages.Plot_Title(), this.getTitle()};
            String[] header2 = new String[] {
                Messages.Plot_Value(),
                Messages.Plot_SeriesLabel(),
                Messages.Plot_BuildNumber(),
                Messages.Plot_BuildDate(),
                Messages.Plot_URL()
            };
            writer.writeNext(header1);
            writer.writeNext(header2);
            // write each entry of rawPlotData to a new line in the CSV file
            for (String[] entry : rawPlotData) {
                if (reportBuild(Integer.parseInt(entry[2]))) {
                    writer.writeNext(entry);
                }
            }
        } catch (IOException ioe) {
            LOGGER.log(Level.SEVERE, "Exception saving plot file", ioe);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Failed to close plot reader", e);
                }
            }
        }
    }

    /**
     * @return true if the build should be part of the graph.
     */
    /* package */ boolean reportBuild(int buildNumber) {
        int buildsNumber;
        try {
            buildsNumber = Integer.parseInt(this.numBuilds);
        } catch (NumberFormatException ex) {
            // Report all builds
            buildsNumber = Integer.MAX_VALUE;
        }

        return buildNumber >= project.getNextBuildNumber() - buildsNumber
                && (keepRecords || project.getBuildByNumber(buildNumber) != null);
    }
}
