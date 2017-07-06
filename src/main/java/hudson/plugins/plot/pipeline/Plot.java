/*
 * Copyright (c) 2007-2009 Yahoo! Inc.  All rights reserved.
 * The copyrights to the contents of this file are licensed under the MIT License (http://www.opensource.org/licenses/mit-license.php)
 */

package hudson.plugins.plot.pipeline;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import hudson.FilePath;
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
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.AbstractCategoryItemRenderer;
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * Represents the configuration for a single plotpipeline. A plotpipeline can have one or more
 * data series (lines). Each data series has one data point per build. The
 * x-axis is always the build number.
 *
 * A plotpipeline has the following characteristics:
 * <ul>
 * <li>a title (mandatory)
 * <li>y-axis label (defaults to no label)
 * <li>one or more data series
 * <li>plotpipeline group (defaults to no group)
 * <li>number of builds to show on the plotpipeline (defaults to all)
 * </ul>
 *
 * A plots group effects the way in which plots are displayed. Group names are
 * listed as links on the top-level plotpipeline page. The user then clicks on a group
 * and sees the plots that belong to that group.
 *
 * @author Nigel Daley
 */
public class Plot implements Comparable<Plot> {
    private static final Logger LOGGER = Logger.getLogger(Plot.class.getName());
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat(
            "MMM d");

    /**
     * Effectively a 2-dimensional array, where each row is the data for one
     * data series of an individual build; the columns are: series y-value,
     * series label, build number, optional URL
     */
    private transient List<String[]> rawPlotData;

    /**
     * The generated plotpipeline, which is only regenerated when new data is added (it
     * is re-rendered, however, every time it is requested).
     */
    private transient JFreeChart plot;

    /**
     * The project (or job) that this plotpipeline belongs to. A reference to the
     * project is needed to retrieve and save the CSV file that is stored in the
     * project's root directory.
     */
    private transient Job<?, ?> job;

    /** All plots share the same JFreeChart drawing supplier object. */
    private static final DrawingSupplier supplier = new DefaultDrawingSupplier(
            DefaultDrawingSupplier.DEFAULT_PAINT_SEQUENCE,
            DefaultDrawingSupplier.DEFAULT_OUTLINE_PAINT_SEQUENCE,
            DefaultDrawingSupplier.DEFAULT_STROKE_SEQUENCE,
            DefaultDrawingSupplier.DEFAULT_OUTLINE_STROKE_SEQUENCE,
            // the plotpipeline data points are a small diamond shape
            new Shape[] { new Polygon(new int[] { 3, 0, -3, 0 }, new int[] { 0,
                    4, 0, -4 }, 4) });

    /** The default plotpipeline width. */
    private static final int DEFAULT_WIDTH = 750;

    /** The default plotpipeline height. */
    private static final int DEFAULT_HEIGHT = 450;

    /** The default number of builds on plotpipeline (all). */
    private static final String DEFAULT_NUMBUILDS = "";

    // Transient values

    /** The width of the plotpipeline. */
    private transient int width;

    /** The height of the plotpipeline. */
    private transient int height;

    /** The right-most build number on the plotpipeline. */
    private transient int rightBuildNum;

    /** Whether or not the plotpipeline has a legend. */
    private transient boolean hasLegend = true;

    /** Number of builds back to show on this plotpipeline from url. */
    public transient String urlNumBuilds;

    /** Title of plotpipeline from url. */
    public transient String urlTitle;

    /** Style of plotpipeline from url. */
    public transient String urlStyle;

    /** Use description flag from url. */
    public transient Boolean urlUseDescr;

    // Configuration values

    /** Title of plotpipeline. Mandatory. */
    public String title;

    /** Y-axis label. Optional. */
    public String yaxis;

    /** List of data series. */
    public List<Series> series;

    /** Group name that this plotpipeline belongs to. */
    public String group;

    /**
     * Number of builds back to show on this plotpipeline. Empty string means all
     * builds. Must not be "0".
     */
    public String numBuilds;

    /**
     * The name of the CSV file that persists the plots data. The CSV file is
     * stored in the projects root directory. This is different from the source
     * csv file that can be used as a source for the plotpipeline.
     */
    public String csvFileName;

    /** The date of the last change to the CSV file. */
    private long csvLastModification;

    /** Optional style of plotpipeline: line, line3d, stackedArea, stackedBar, etc. */
    public String style;

    /** Whether or not to use build descriptions as X-axis labels. Optional. */
    public boolean useDescr;

    /** keep records for builds that was deleted */
    private boolean keepRecords;

    /** Whether or not to exclude zero as default Y-axis value. Optional. */
    public boolean exclZero;

    /** Use a logarithmic Y-axis. */
    public boolean logarithmic;

    /** Min/max yaxis values, string used so if no value defaults used */
    public String yaxisMinimum;
    public String yaxisMaximum;

    /**
     * Creates a new plotpipeline with the given paramenters. If numBuilds is the empty
     * string, then all builds will be included. Must not be zero.
     */
    @DataBoundConstructor
    public Plot(String title, String yaxis, String group, String numBuilds,
                String csvFileName, String style, boolean useDescr,
                boolean keepRecords, boolean exclZero, boolean logarithmic,
                String yaxisMinimum, String yaxisMaximum) {
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
    }

    /**
     * @deprecated Kept for backward compatibility.
     */
    @Deprecated
    public Plot(String title, String yaxis, String group, String numBuilds,
                String csvFileName, String style, boolean useDescr) {
        this(title, yaxis, group, numBuilds, csvFileName, style, useDescr,
                false, false, false, null, null);
    }

    // needed for serialization
    public Plot() {
    }

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
        if (input != null) {
            try {
                result = Double.parseDouble(input);
            } catch (NumberFormatException nfe) {
                // Not a problem, result already set
            }
        }
        return result;
    }

    public int compareTo(Plot o) {
        return title.compareTo(o.getTitle());
    }

    @Override
    public boolean equals( Object o ) {
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
                + "),FILENAME(" + getCsvFileName() + ")";
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
        if (StringUtils.isBlank(csvFileName) && job != null) {
            try {
                csvFileName = File.createTempFile("plot-", ".csv", job.getRootDir()).getName();
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Unable to create temporary CSV file.", e);
            }
        }
        return csvFileName;
    }

    /**
     * Sets the title for the plotpipeline from the "title" parameter in the given
     * StaplerRequest.
     */
    private void setTitle(StaplerRequest req) {
        urlTitle = req.getParameter("title");
    }

    private String getURLTitle() {
        return urlTitle != null ? urlTitle : title;
    }

    public String getTitle() {
        return title;
    }

    private void setStyle(StaplerRequest req) {
        urlStyle = req.getParameter("style");
    }

    private String getUrlStyle() {
        return urlStyle != null ? urlStyle : (style != null ? style : "");
    }

    private void setUseDescr(StaplerRequest req) {
        String u = req.getParameter("usedescr");
        if (u == null) {
            urlUseDescr = null;
        } else {
            urlUseDescr = "on".equalsIgnoreCase(u)
                    || "true".equalsIgnoreCase(u);
        }
    }

    private boolean getUrlUseDescr() {
        return urlUseDescr != null ? urlUseDescr : useDescr;
    }

    /**
     * Sets the number of builds to plotpipeline from the "numbuilds" parameter in the
     * given StaplerRequest. If the parameter doesn't exist or isn't an integer
     * then a default is used.
     */
    private void setHasLegend(StaplerRequest req) {
        String legend = req.getParameter("legend");
        hasLegend = legend == null || "on".equalsIgnoreCase(legend)
                || "true".equalsIgnoreCase(legend);
    }

    public boolean hasLegend() {
        return hasLegend;
    }

    /**
     * Sets the number of builds to plotpipeline from the "numbuilds" parameter in the
     * given StaplerRequest. If the parameter doesn't exist or isn't an integer
     * then a default is used.
     */
    private void setNumBuilds(StaplerRequest req) {
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
     * Sets the right-most build number shown on the plotpipeline from the
     * "rightbuildnum" parameter in the given StaplerRequest. If the parameter
     * doesn't exist or isn't an integer then a default is used.
     */
    private void setRightBuildNum(StaplerRequest req) {
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
     * Sets the plotpipeline width from the "width" parameter in the given
     * StaplerRequest. If the parameter doesn't exist or isn't an integer then a
     * default is used.
     */
    private void setWidth(StaplerRequest req) {
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
     * Sets the plotpipeline height from the "height" parameter in the given
     * StaplerRequest. If the parameter doesn't exist or isn't an integer then a
     * default is used.
     */
    private void setHeight(StaplerRequest req) {
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

    public Job<?, ?> getProject() {
        return job;
    }

    /**
     * A reference to the project is needed to retrieve the project's root
     * directory where the CSV file is located. Unfortunately, a reference to
     * the project is not available when this object is created.
     *
     * @param job
     *            the project
     */
    public void setProject(Job<?, ?> job) {
        this.job = job;
    }

    /**
     * Generates and writes the plotpipeline to the response output stream.
     *
     * @param req
     *            the incoming request
     * @param rsp
     *            the response stream
     * @throws IOException
     */
    public void plotGraph(StaplerRequest req, StaplerResponse rsp)
            throws IOException {
        if ( ChartUtil.awtProblemCause != null) {
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
        // need to force regenerate the plotpipeline in case build
        // descriptions (used for tool tips) have changed
        generatePlot(true);
        ChartUtil.generateGraph(req, rsp, plot, getWidth(), getHeight());
    }

    /**
     * Generates and writes the plotpipeline's clickable map to the response output
     * stream.
     *
     * @param req
     *            the incoming request
     * @param rsp
     *            the response stream
     * @throws IOException
     */
    public void plotGraphMap(StaplerRequest req, StaplerResponse rsp)
            throws IOException {
        if ( ChartUtil.awtProblemCause != null) {
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
        rsp.getWriter().println(
                ChartUtilities.getImageMap(getCsvFileName(), info));
    }

    /**
     * Called when a build completes. Adds the finished build to this plotpipeline. This
     * method extracts the data for each data series from the build and saves it
     * in the plotpipeline's CSV file.
     *
     * @param run
     * @param logger
     */
    public void addBuild(Run<?, ?> run, PrintStream logger, FilePath workspace) {
        if (job == null)
            job = run.getParent();

        // load the existing plotpipeline data from disk
        loadPlotData();
        // extract the data for each data series
        for (Series series : getSeries()) {
            if (series == null)
                continue;
            List<PlotPoint> seriesData = series.loadSeries(
                    workspace, run.getNumber(), logger);
            if (seriesData != null) {
                for (PlotPoint point : seriesData) {
                    if (point == null)
                        continue;

                    rawPlotData.add(new String[] { point.getYvalue(),
                            point.getLabel(),
                            run.getNumber() + "", // convert to a string
                            run.getTimestamp().getTimeInMillis() + "",
                            point.getUrl() });
                }
            }
        }

        // save the updated plotpipeline data to disk
        savePlotData();
    }

    /**
     * Generates the plotpipeline and stores it in the plotpipeline instance variable.
     *
     * @param forceGenerate
     *            if true, force the plotpipeline to be re-generated even if the on-disk
     *            data hasn't changed
     */
    private void generatePlot(boolean forceGenerate) {
        class Label implements Comparable<Label> {
            final private Integer buildNum;
            final private String buildDate;
            final private String text;

            public Label(String buildNum, String buildTime, String text) {
                this.buildNum = Integer.parseInt(buildNum);
                synchronized (DATE_FORMAT) {
                    this.buildDate = DATE_FORMAT.format(new Date(Long
                            .parseLong(buildTime)));
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
                return o instanceof Label
                        && ((Label) o).buildNum.equals(buildNum);
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
        // LOGGER.info("Determining if we should generate plotpipeline " +
        // getCsvFileName());
        File csvFile = new File(job.getRootDir(), getCsvFileName());
        if (csvFile.lastModified() == csvLastModification && plot != null
                && !forceGenerate) {
            // data hasn't changed so don't regenerate the plotpipeline
            return;
        }
        if (rawPlotData == null || csvFile.lastModified() > csvLastModification) {
            // data has changed or has not been loaded so load it now
            loadPlotData();
        }
        // LOGGER.info("Generating plotpipeline " + getCsvFileName());
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
            Number value = null;
            try {
                value = Integer.valueOf(record[0]);
            } catch (NumberFormatException nfe) {
                try {
                    value = Double.valueOf(record[0]);
                } catch (NumberFormatException nfe2) {
                    LOGGER.log(Level.SEVERE, "Exception converting to number",
                            nfe2);
                    continue; // skip this record all together
                }
            }
            String series = record[1];
            Label xlabel = getUrlUseDescr() ? new Label(record[2], record[3],
                    descriptionForBuild(buildNum)) : new Label(record[2],
                    record[3]);
            String url = null;
            if (record.length >= 5)
                url = record[4];
            dataset.setValue(value, url, series, xlabel);
        }

        String urlNumBuilds = getURLNumBuilds();
        int numBuilds;
        if (StringUtils.isBlank(urlNumBuilds)) {
            numBuilds = Integer.MAX_VALUE;
        } else {
            try {
                numBuilds = Integer.parseInt(urlNumBuilds);
            } catch (NumberFormatException nfe) {
                LOGGER.log(Level.SEVERE, "Exception converting to integer", nfe);
                numBuilds = Integer.MAX_VALUE;
            }
        }

        dataset.clipDataset(numBuilds);
        plot = createChart(dataset);
        CategoryPlot categoryPlot = (CategoryPlot) plot.getPlot();
        categoryPlot.setDomainGridlinePaint(Color.black);
        categoryPlot.setRangeGridlinePaint(Color.black);
        categoryPlot.setDrawingSupplier(Plot.supplier);
        CategoryAxis domainAxis = new ShiftedCategoryAxis(Messages.Plot_Build());
        categoryPlot.setDomainAxis(domainAxis);
        domainAxis.setCategoryLabelPositions(CategoryLabelPositions.UP_90);
        domainAxis.setLowerMargin(0.0);
        domainAxis.setUpperMargin(0.03);
        domainAxis.setCategoryMargin(0.0);
        for (Object category : dataset.getColumnKeys()) {
            Label label = (Label) category;
            if (label.text != null) {
                domainAxis
                        .addCategoryLabelToolTip(label, label.numDateString());
            } else {
                domainAxis.addCategoryLabelToolTip(label,
                        descriptionForBuild(label.buildNum));
            }
        }
        // Replace the range axis by a logarithmic axis if the option is
        // selected
        if (isLogarithmic()) {
            LogarithmicAxis logAxis = new LogarithmicAxis(getYaxis());
            logAxis.setExpTickLabelsFlag(true);
            categoryPlot.setRangeAxis(logAxis);
        }

        // optionally exclude zero as default y-axis value
        ValueAxis rangeAxis = categoryPlot.getRangeAxis();
        if ((rangeAxis != null) && (rangeAxis instanceof NumberAxis)) {
            if (hasYaxisMinimum()) {
                ((NumberAxis) rangeAxis).setLowerBound(getYaxisMinimum());
            }
            if (hasYaxisMaximum()) {
                ((NumberAxis) rangeAxis).setUpperBound(getYaxisMaximum());
            }
            ((NumberAxis) rangeAxis).setAutoRangeIncludesZero(!getExclZero());
        }

        AbstractCategoryItemRenderer renderer = (AbstractCategoryItemRenderer) categoryPlot
                .getRenderer();
        int numColors = dataset.getRowCount();
        for (int i = 0; i < numColors; i++) {
            renderer.setSeriesPaint(i,
                    new Color(Color.HSBtoRGB((1f / numColors) * i, 1f, 1f)));
        }
        renderer.setBaseStroke(new BasicStroke(2.0f));
        renderer.setBaseToolTipGenerator(new StandardCategoryToolTipGenerator(
                Messages.Plot_Build() + " {1}: {2}", NumberFormat.getInstance()));
        renderer.setBaseItemURLGenerator(new PointURLGenerator());
        if (renderer instanceof LineAndShapeRenderer) {
            String s = getUrlStyle();
            LineAndShapeRenderer lasRenderer = (LineAndShapeRenderer) renderer;
            if ("lineSimple".equalsIgnoreCase(s)) {
               lasRenderer.setShapesVisible(false); // TODO: deprecated, may be unnecessary
            } else {
               lasRenderer.setShapesVisible(true); // TODO: deprecated, may be unnecessary
            }
        }
    }

    /**
     * Creates a Chart of the style indicated by getEffStyle() using the given
     * dataset. Defaults to using createLineChart.
     */
    private JFreeChart createChart(PlotCategoryDataset dataset) {
        String s = getUrlStyle();
        if ("area".equalsIgnoreCase(s)) {
            return ChartFactory.createAreaChart(getURLTitle(), /*
                                                                * categoryAxisLabel=
                                                                */null,
                    getYaxis(), dataset, PlotOrientation.VERTICAL, hasLegend(), /*
                                                                                 * tooltips
                                                                                 * =
                                                                                 */
                    true, /* url= */false);
        }
        if ("bar".equalsIgnoreCase(s)) {
            return ChartFactory.createBarChart(getURLTitle(), /*
                                                               * categoryAxisLabel=
                                                               */null,
                    getYaxis(), dataset, PlotOrientation.VERTICAL, hasLegend(), /*
                                                                                 * tooltips
                                                                                 * =
                                                                                 */
                    true, /* url= */false);
        }
        if ("bar3d".equalsIgnoreCase(s)) {
            return ChartFactory.createBarChart3D(getURLTitle(), /*
                                                                 * categoryAxisLabel
                                                                 * =
                                                                 */null,
                    getYaxis(), dataset, PlotOrientation.VERTICAL, hasLegend(), /*
                                                                                 * tooltips
                                                                                 * =
                                                                                 */
                    true, /* url= */false);
        }
        if ("line3d".equalsIgnoreCase(s)) {
            return ChartFactory.createLineChart3D(getURLTitle(), /*
                                                                  * categoryAxisLabel
                                                                  * =
                                                                  */null,
                    getYaxis(), dataset, PlotOrientation.VERTICAL, hasLegend(), /*
                                                                                 * tooltips
                                                                                 * =
                                                                                 */
                    true, /* url= */false);
        }
        if ("lineSimple".equalsIgnoreCase(s)) {
            return ChartFactory.createLineChart(
                    getURLTitle(), /*categoryAxisLabel=*/null, getYaxis(), dataset,
                    PlotOrientation.VERTICAL, hasLegend(), /*tooltips=*/true, /*url=*/false);
        }

        if ("stackedarea".equalsIgnoreCase(s)) {
            return ChartFactory.createStackedAreaChart(getURLTitle(), /*
                                                                       * categoryAxisLabel
                                                                       * =
                                                                       */null,
                    getYaxis(), dataset, PlotOrientation.VERTICAL, hasLegend(), /*
                                                                                 * tooltips
                                                                                 * =
                                                                                 */
                    true, /* url= */false);
        }
        if ("stackedbar".equalsIgnoreCase(s)) {
            return ChartFactory.createStackedBarChart(getURLTitle(), /*
                                                                      * categoryAxisLabel
                                                                      * =
                                                                      */null,
                    getYaxis(), dataset, PlotOrientation.VERTICAL, hasLegend(), /*
                                                                                 * tooltips
                                                                                 * =
                                                                                 */
                    true, /* url= */false);
        }
        if ("stackedbar3d".equalsIgnoreCase(s)) {
            return ChartFactory.createStackedBarChart3D(getURLTitle(), /*
                                                                        * categoryAxisLabel
                                                                        * =
                                                                        */null,
                    getYaxis(), dataset, PlotOrientation.VERTICAL, hasLegend(), /*
                                                                                 * tooltips
                                                                                 * =
                                                                                 */
                    true, /* url= */false);
        }
        if ("waterfall".equalsIgnoreCase(s)) {
            return ChartFactory.createWaterfallChart(getURLTitle(), /*
                                                                     * categoryAxisLabel
                                                                     * =
                                                                     */null,
                    getYaxis(), dataset, PlotOrientation.VERTICAL, hasLegend(), /*
                                                                                 * tooltips
                                                                                 * =
                                                                                 */
                    true, /* url= */false);
        }
        return ChartFactory.createLineChart(getURLTitle(), /* categoryAxisLabel= */
                null, getYaxis(), dataset, PlotOrientation.VERTICAL,
                hasLegend(), /* tooltips= */true, /* url= */false);
    }

    /**
     * Returns a trimmed description string for the build specified by the given
     * build number.
     */
    private String descriptionForBuild(int buildNum) {
        Run r = job.getBuildByNumber(buildNum);
        if (r != null) {
            String tip = r.getTruncatedDescription();
            if (tip != null) {
                return tip.replaceAll("<p> *|<br> *", ", ");
            }
        }
        return null;
    }

    /**
     * Loads the plotpipeline data from the CSV file on disk. The CSV file is stored in
     * the projects root directory. The data is stored in the rawPlotData
     * instance variable.
     */
    private void loadPlotData() {
        rawPlotData = new ArrayList<String[]>();
        // load existing plotpipeline file
        File plotFile = new File(job.getRootDir(), getCsvFileName());
        if (!plotFile.exists()) {
            return;
        }
        CSVReader reader = null;
        rawPlotData = new ArrayList<String[]>();
        try {
            reader = new CSVReader(new InputStreamReader( new FileInputStream(plotFile), "UTF-8" ) );
            // throw away 2 header lines
            reader.readNext();
            reader.readNext();
            // read each line of the CSV file and add to rawPlotData
            String[] nextLine;
            while ((nextLine = reader.readNext()) != null) {
                rawPlotData.add(nextLine);
            }
        } catch (IOException ioe) {
            LOGGER.log(Level.SEVERE, "Exception reading plotpipeline file", ioe);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignore) {
                    // ignore
                }
            }
        }
    }

    /**
     * Saves the plotpipeline data to the CSV file on disk. The CSV file is stored in
     * the projects root directory. The data is read from the rawPlotData
     * instance variable.
     */
    private void savePlotData() {
        File plotFile = new File(job.getRootDir(), getCsvFileName());
        CSVWriter writer = null;
        try {
            writer = new CSVWriter(new OutputStreamWriter( new FileOutputStream(plotFile), "UTF-8"));
            // write 2 header lines
            String[] header1 = new String[] { Messages.Plot_Title(),
                    this.getTitle() };
            String[] header2 = new String[] { Messages.Plot_Value(),
                    Messages.Plot_SeriesLabel(), Messages.Plot_BuildNumber(),
                    Messages.Plot_BuildDate(), Messages.Plot_URL() };
            writer.writeNext(header1);
            writer.writeNext(header2);
            // write each entry of rawPlotData to a new line in the CSV file
            for (String[] entry : rawPlotData) {
                if (reportBuild(Integer.parseInt(entry[2]))) {
                    writer.writeNext(entry);
                }
            }
        } catch (IOException ioe) {
            LOGGER.log(Level.SEVERE, "Exception saving plotpipeline file", ioe);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignore) {
                    // ignore
                }
            }
        }
    }

    /**
     * @return true if the build should be part of the graph.
     */
    /* package */boolean reportBuild(int buildNumber) {
        int numBuilds;
        try {
            numBuilds = Integer.parseInt(this.numBuilds);
        } catch (NumberFormatException ex) {
            // Report all builds
            numBuilds = Integer.MAX_VALUE;
        }

        if (buildNumber < job.getNextBuildNumber() - numBuilds)
            return false;

        return keepRecords || job.getBuildByNumber(buildNumber) != null;
    }
}
