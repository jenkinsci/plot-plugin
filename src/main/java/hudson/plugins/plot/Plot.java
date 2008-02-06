/*
 * Copyright (c) 2007 Yahoo! Inc.  All rights reserved.  
 * Copyrights licensed under the MIT License.
 */
package hudson.plugins.plot;

import hudson.FilePath;
import hudson.model.AbstractProject;
import hudson.model.Build;
import hudson.model.Project;
import hudson.model.Run;
import hudson.util.ChartUtil;
import hudson.util.ShiftedCategoryAxis;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Polygon;
import java.awt.Shape;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Properties;
import java.util.logging.Logger;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartRenderingInfo;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.labels.StandardCategoryToolTipGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.DefaultDrawingSupplier;
import org.jfree.chart.plot.DrawingSupplier;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;

/**
 * Represents the configuration for a single plot.  A plot can
 * have one or more data series (lines).  Each data series 
 * has one data point per build.  The x-axis is alway the
 * build number.
 * 
 * A plot has the following characteristics:
 * <ul>
 * <li> a title (mandatory)
 * <li> y-axis label (defaults to no label)
 * <li> one or more data series
 * <li> plot group (defaults to no group)
 * <li> number of builds to show on the plot (defaults to all)
 * </ul> 
 * 
 * A plots group effects the way in which plots are displayed.  Group names
 * are listed as links on the top-level plot page.  The user then clicks
 * on a group and sees the plots that belong to that group.
 * 
 * @author Nigel Daley
 */
public class Plot implements Comparable {
    private static transient final Logger LOGGER = Logger.getLogger(Plot.class.getName());
    private static transient final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d");
    
    /**
     * Effectively a 2-dimensional array, where each row is the 
     * data for one data series of an individual build;  the columns
     * are: series y-value, series label, build number, optional URL
     */
    private transient ArrayList<String[]> rawPlotData;
    
    /**
     * The generated plot, which is only regenerated when new data
     * is added (it is re-rendered, however, every time it is requested).
     */
    private transient JFreeChart plot;
    
    /**
     * The project (or job) that this plot belongs to.  A reference
     * to the project is needed to retrieve and save the CSV file
     * that is stored in the project's root directory.
     */
    private transient AbstractProject project;
    
    /** All plots share the same JFreeChart drawing supplier object. */
    private static transient final DrawingSupplier supplier = new DefaultDrawingSupplier(
            DefaultDrawingSupplier.DEFAULT_PAINT_SEQUENCE,
            DefaultDrawingSupplier.DEFAULT_OUTLINE_PAINT_SEQUENCE,
            DefaultDrawingSupplier.DEFAULT_STROKE_SEQUENCE,
            DefaultDrawingSupplier.DEFAULT_OUTLINE_STROKE_SEQUENCE,
            // the plot data points are a small diamond shape 
            new Shape[] { new Polygon(new int[] {3, 0, -3, 0},
                    new int[] {0, 4, 0, -4}, 4) }
    );
    
    /** The default plot width. */
    private static transient final int DEFAULT_WIDTH = 750;
    
    /** The default plot height. */
    private static transient final int DEFAULT_HEIGHT = 450;
    
    /** The default number of builds on plot (all). */
    private static transient final String DEFAULT_NUMBUILDS = "";
    
    /** The width of the plot. */
    private transient int width;
    
    /** The height of the plot. */
    private transient int height;
    
    /** The right-most build number on the plot. */
    private transient int rightBuildNum;
    
    /** Whether or not the plot has a legend. */
    private transient boolean hasLegend = true;
    
    /** Number of builds back to show on this plot from url. */
    public transient String urlNumBuilds = null;
    
    /** Title of plot from url. */
    public transient String urlTitle = null;
    
    /** Title of plot. Mandatory. */
    public String title;
    
    /** Y-axis label. Optional. */
    public String yaxis;

    /** Array of data series. */
    public Series[] series;

    /** Group name that this plot belongs to. */
    public String group;
    
    /** 
     * Number of builds back to show on this plot. 
     * Empty string means all builds.  Must not be "0".
     */
    public String numBuilds;
    
    /**
     * The name of the CSV file that persists the plots data.
     * The CSV file is stored in the projects root directory.
     */
    public String csvFileName;
    
    /** The date of the last change to the CSV file. */
    private long csvLastModification;
    
    /**
     * Creates a new plot with the given paramenters.  If numBuilds
     * is the empty string, then all builds will be included. Must
     * not be zero.
     */
    public Plot(String title, String yaxis, Series[] series,
            String group, String numBuilds, String csvFileName) 
    {
        this.title = title;
        this.yaxis = yaxis;
        this.series = series;
        this.group = group;
        this.numBuilds = numBuilds;
        this.csvFileName = csvFileName;
    }

    // needed for serialization
    public Plot() {}

    public int compareTo(Object o) {
        return title.compareTo(((Plot)o).getConfiguredTitle());
    }
    
    public String toString() {
        return "TITLE("+getTitle()+
            "),YAXIS("+yaxis+
            "),NUMSERIES("+series.length+
            "),GROUP("+group+
            "),NUMBUILDS("+getNumBuilds()+
            "),RIGHTBUILDNUM("+getRightBuildNum()+
            "),HASLEGEND("+hasLegend()+
            "),FILENAME("+csvFileName+")";
    }
    
    public String getYaxis() {
        return yaxis;
    }
    public Series[] getSeries() {
        return series;
    }
    public String getGroup() {
        return group;
    }
    public String getCsvFileName() {
        return csvFileName;
    }
    
    /**
     * Sets the title for the plot from the "title" parameter
     * in the given StaplerRequest.
     */
    private void setTitle(StaplerRequest req) {
        urlTitle = req.getParameter("title");
    }
    public String getTitle() {
        if (urlTitle != null) {
            return urlTitle;
        }
        return title;
    }
    public String getConfiguredTitle() {
        return title;
    }
    
    /**
     * Sets the number of builds to plot from the "numbuilds" parameter
     * in the given StaplerRequest.  If the parameter doesn't exist
     * or isn't an integer then a default is used.
     */
    private void setHasLegend(StaplerRequest req) {
        String legend = req.getParameter("legend");
        if (legend == null) {
            hasLegend = true;
        } else {
            try {
                hasLegend = Boolean.parseBoolean(legend);
            } catch (NumberFormatException nfe) {
                hasLegend = true;
            }
        }
    }
    public boolean hasLegend() {
        return hasLegend;
    }
        
    /**
     * Sets the number of builds to plot from the "numbuilds" parameter
     * in the given StaplerRequest.  If the parameter doesn't exist
     * or isn't an integer then a default is used.
     */
    private void setNumBuilds(StaplerRequest req) {
        urlNumBuilds = req.getParameter("numbuilds");
        if (urlNumBuilds != null) {
            try {
                int tmp = Integer.parseInt(urlNumBuilds);
            } catch (NumberFormatException nfe) {
                urlNumBuilds = null;
            }
        }
    }
    public String getNumBuilds() {
        if (urlNumBuilds != null) {
            return urlNumBuilds;
        }
        return numBuilds;
    }

    /**
     * Sets the right-most build number shown on the plot from
     * the "rightbuildnum" parameter in the given StaplerRequest.
     * If the parameter doesn't exist or isn't an integer then 
     * a default is used.
     */
    private void setRightBuildNum(StaplerRequest req) {
        String build = req.getParameter("rightbuildnum");
        if (build == null) {
            rightBuildNum = Integer.MAX_VALUE;
        } else {
            try {
                rightBuildNum = Integer.parseInt(build);
            } catch (NumberFormatException nfe) {
                rightBuildNum = Integer.MAX_VALUE;
            }
        }
    }
    private int getRightBuildNum() {
        return rightBuildNum;
    }
    
    /**
     * Sets the plot width from the "width" parameter in the
     * given StaplerRequest.  If the parameter doesn't exist
     * or isn't an integer then a default is used.
     */
    private void setWidth(StaplerRequest req) {
        String w = req.getParameter("width");
        if (w == null) {
            width = DEFAULT_WIDTH;
        } else {
            try {
                width = Integer.parseInt(w);
            } catch (NumberFormatException nfe) {
                width = DEFAULT_WIDTH;
            }
        }
    }
    private int getWidth() {
        return width;
    }
    private int getHeight() {
        return height;
    }
    /**
     * Sets the plot height from the "height" parameter in the
     * given StaplerRequest.  If the parameter doesn't exist
     * or isn't an integer then a default is used.
     */
    private void setHeight(StaplerRequest req) {
        String h = req.getParameter("height");
        if (h == null) {
            height = DEFAULT_HEIGHT;
        } else {
            try {
                height = Integer.parseInt(h);
            } catch (NumberFormatException nfe) {
                height = DEFAULT_HEIGHT;
            }
        }
    }
    
    /**
     * A reference to the project is needed to retrieve
     * the project's root directory where the CSV file
     * is located.  Unfortunately, a reference to the project
     * is not available when this object is created.
     * 
     * @param project the project
     */
    public void setProject(Project project) {
        this.project = project;
    }
    
    /**
     * Generates and writes the plot to the response output stream.
     * 
     * @param req the incoming request
     * @param rsp the response stream
     * @throws IOException
     */
    public void plotGraph(StaplerRequest req, StaplerResponse rsp) 
        throws IOException 
    {
        if (ChartUtil.awtProblem) {
            // Not available. Send out error message.
            rsp.sendRedirect2(req.getContextPath()+"/images/headless.png");
            return;
        }
        setWidth(req);
        setHeight(req);
        setNumBuilds(req);
        setRightBuildNum(req);
        setHasLegend(req);
        setTitle(req);
        // need to force regenerate the plot incase build 
        // descriptions (used for tool tips) have changed 
        generatePlot(true); 
        ChartUtil.generateGraph(req, rsp, plot, getWidth(), getHeight());
    }
 
    /**
     * Generates and writes the plot's clickable map to the response 
     * output stream.
     * 
     * @param req the incoming request
     * @param rsp the response stream
     * @throws IOException
     */
    public void plotGraphMap(StaplerRequest req, StaplerResponse rsp) 
        throws IOException 
    {
        if (ChartUtil.awtProblem) {
            // not available. send out error message
            rsp.sendRedirect2(req.getContextPath()+"/images/headless.png");
            return;
        }
        setWidth(req);
        setHeight(req);
        setNumBuilds(req);
        setRightBuildNum(req);
        setHasLegend(req);
        setTitle(req);
        generatePlot(false);
        ChartRenderingInfo info = new ChartRenderingInfo();
        plot.createBufferedImage(getWidth(),getHeight(),info);
        rsp.setContentType("text/plain;charset=UTF-8");
        rsp.getWriter().println(ChartUtilities.getImageMap(getCsvFileName(),info));
    }
    
    /**
     * Called when a build completes.  Adds the finished build to this plot.
     * This method extracts the data for each data series from the build and
     * saves it in the plot's CSV file.
     * 
     * @param build
     * @param logger
     */
    public void addBuild(Build build, PrintStream logger) {
        if (project == null) project = build.getProject();
        // load the existing plot data from disk
        loadPlotData();
        // extract the data for each data series
        for (Series series : getSeries()) {
            Properties seriesData = loadSeriesData(series,project.getWorkspace(),logger);
            if (seriesData != null) {
                rawPlotData.add(new String[] {
                    seriesData.getProperty("YVALUE"),
                    series.getLabel(),
                    build.getNumber() + "", // convert to a string
                    build.getTimestamp().getTimeInMillis() + "",
                    seriesData.getProperty("URL")
                });
            }
        }
        // save the updated plot data to disk
        savePlotData();
    }

    /**
     * Generates the plot and stores it in the plot instance variable.
     * 
     * @param forceGenerate if true, force the plot to be re-generated
     *        even if the on-disk data hasn't changed
     */
    private void generatePlot(boolean forceGenerate) {
        class Label implements Comparable<Label> {
            private Integer buildNum;
            private String buildDate;
            public Label(String buildNum, String buildTime) {
                this.buildNum = Integer.parseInt(buildNum);
                this.buildDate = dateFormat.format(
                        new Date(Long.parseLong(buildTime)));
            }
            public int compareTo(Label that) {
                return this.buildNum-that.buildNum;
            }
            public boolean equals(Object o) {
                Label that = (Label) o;
                return this.buildNum.equals(that.buildNum);
            }
            public int hashCode() {
                return buildNum.hashCode();
            }
            public String toString() {
                return "#" + buildNum + " (" + buildDate + ")";
            }
        }
        //LOGGER.info("Determining if we should generate plot " + getCsvFileName());
        File csvFile = new File(project.getRootDir(),getCsvFileName());
        if (csvFile.lastModified() == csvLastModification &&
            plot != null &&
            !forceGenerate) 
        {
            // data hasn't changed so don't regenerate the plot
            return;
        }
        if (rawPlotData == null || 
            csvFile.lastModified() > csvLastModification) 
        {
            // data has changed or has not been loaded so load it now
            loadPlotData();
        }
        //LOGGER.info("Generating plot " + getCsvFileName());
        csvLastModification = csvFile.lastModified();
        PlotCategoryDataset dataset = 
            new PlotCategoryDataset();
        for (String[] record : rawPlotData) {
            // record: series y-value, series label, build number, build date, url
            int buildNum;
            try {
                buildNum = Integer.valueOf(record[2]);
                if (buildNum > getRightBuildNum()) {
                    continue; // skip this record
                }
            } catch (NumberFormatException nfe) {
                  continue; // skip this record all together
            }
            Number value = null;
            try {
                value = Integer.valueOf(record[0]);
            } catch (NumberFormatException nfe) {
                try {
                    value = Double.valueOf(record[0]);
                } catch (NumberFormatException nfe2) {
                    continue; // skip this record all together
                }
            }
            String series = record[1];
            Label xlabel = new Label(record[2],record[3]);
            String url = null;
            if (record.length >= 5) url = record[4]; 
            dataset.setValue(value,url,series,xlabel);
        }
        int numBuilds;
        try {
            numBuilds = Integer.parseInt(getNumBuilds());
        } catch (NumberFormatException nfe) {
            numBuilds = Integer.MAX_VALUE;
        }
        dataset.clipDataset(numBuilds);
        plot = ChartFactory.createLineChart(
                getTitle(),null,getYaxis(),dataset,
                PlotOrientation.VERTICAL,hasLegend(),true,false);
        CategoryPlot categoryPlot = (CategoryPlot) plot.getPlot();
        categoryPlot.setDomainGridlinePaint(Color.black);
        categoryPlot.setRangeGridlinePaint(Color.black);
        categoryPlot.setDrawingSupplier(Plot.supplier);
        CategoryAxis domainAxis = new ShiftedCategoryAxis("Build");
        categoryPlot.setDomainAxis(domainAxis);
        domainAxis.setCategoryLabelPositions(CategoryLabelPositions.UP_90);
        domainAxis.setLowerMargin(0.0);
        domainAxis.setUpperMargin(0.03);
        domainAxis.setCategoryMargin(0.0);
        for (Object category : dataset.getColumnKeys()) {
            Run r = project.getBuildByNumber(((Label)category).buildNum);
            String tip = null;
            if (r != null) {
                tip = r.getTruncatedDescription();
                if (tip != null) {
                    tip = tip.replaceAll("<p> *|<br> *", ", ");
                    //LOGGER.info("DESCRIPTION:"+tip);
                }
            }
            domainAxis.addCategoryLabelToolTip((Comparable)category, tip);
        }
        LineAndShapeRenderer renderer = 
            (LineAndShapeRenderer) categoryPlot.getRenderer();
        Color[] LINE_GRAPH = new Color[] {
            new Color(0xCC0000), // red
            new Color(0x3465a4), // blue
            new Color(0x73d216), // green
            new Color(0xedd400), // yellow
            new Color(0x8a2be2), // purple
            new Color(0xd2691e), // brown
            new Color(0xee82ee), // pink
            new Color(0x000000)  // black
        };
        int n=0;
        for (Color c : LINE_GRAPH) renderer.setSeriesPaint(n++,c);
        renderer.setShapesVisible(true);
        renderer.setStroke(new BasicStroke(2.0f));
        renderer.setToolTipGenerator(new StandardCategoryToolTipGenerator(
                "Build {1}: {2}",NumberFormat.getInstance()));
        renderer.setItemURLGenerator(new PointURLGenerator());
    }
    
    /**
     * Loads the plot data from the CSV file on disk.  The
     * CSV file is stored in the projects root directory.
     * The data is stored in the rawPlotData instance variable.
     */
    private void loadPlotData() {
        rawPlotData = new ArrayList<String[]>();
        // load existing plot file
        File plotFile = new File(project.getRootDir(),getCsvFileName());
        if (!plotFile.exists()) {
            return;
        }
        CSVReader reader = null;
        rawPlotData = new ArrayList<String[]>();
        try {
            reader = new CSVReader(new FileReader(plotFile));
            String [] nextLine;
            // throw away 2 header lines
            reader.readNext(); reader.readNext();
            // read each line of the CSV file and add to rawPlotData
            while ((nextLine = reader.readNext()) != null) {
                rawPlotData.add(nextLine);
            }
        } catch (IOException ioe) {
            //ignore
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignore) {
                    //ignore
                }
            }
        }
    }
    
    /**
     * Saves the plot data to the CSV file on disk.  The
     * CSV file is stored in the projects root directory.
     * The data is read from the rawPlotData instance variable.
     */
    private void savePlotData() {
        File plotFile = new File(project.getRootDir(),getCsvFileName());
        CSVWriter writer = null;
        try {
            writer = new CSVWriter(new FileWriter(plotFile));
            // write 2 header lines
            String[] header1 = new String[] {"Title",this.getConfiguredTitle()};
            String[] header2 = new String[] {"Value","Series Label","Build Number","Build Date","URL"};
            writer.writeNext(header1);
            writer.writeNext(header2);
            // write each entry of rawPlotData to a new line in the CSV file
            for (String[] entry : rawPlotData) {
                writer.writeNext(entry);
            }
        } catch (IOException ioe) {
            //ignore
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignore) {
                    //ignore
                }
            }
        }
    }

    /**
     * Retrieves the plot data for one series after a build from the workspace.
     * 
     * @param series the series to retrieve from the workspace
     * @param workspaceRootDir the root directory of the workspace
     * @param logger the logger to use
     * @return a properties object that contains the retrieved series data
     *         from the workspace
     */
    private Properties loadSeriesData(Series series, FilePath workspaceRootDir, PrintStream logger) {
        InputStream in = null;
        FilePath[] seriesFiles = null;
        try {
            seriesFiles = workspaceRootDir.list(series.getFile());
        } catch (Exception e) {
            logger.println("Exception trying to retrieve series files: " + e);
            return null;
        }
        if (seriesFiles != null && seriesFiles.length < 1) {
            logger.println("No plot data file found: " + series.getFile());
            return null;
        }
        try {
            in = seriesFiles[0].read();
            logger.println("Saving plot series data from: " + seriesFiles[0]);
            Properties data = new Properties();
            data.load(in);
            return data;
        } catch (Exception e) {
            logger.println("Exception reading plot series data from: " + seriesFiles[0]);
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignore) {
                    //ignore
                }
            }
        }
    }

}

