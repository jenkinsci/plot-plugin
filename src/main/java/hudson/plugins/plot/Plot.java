/*
 * Copyright (c) 2007-2009 Yahoo! Inc.  All rights reserved.
 * The copyrights to the contents of this file are licensed under the MIT License (http://www.opensource.org/licenses/mit-license.php)
 */

package hudson.plugins.plot;

import hudson.model.AbstractBuild;
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
import java.io.PrintStream;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Random;
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
import org.jfree.chart.renderer.category.AbstractCategoryItemRenderer;
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;

/**
 * Represents the configuration for a single plot.  A plot can
 * have one or more data series (lines).  Each data series 
 * has one data point per build.  The x-axis is always the
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
    private static final Logger LOGGER = Logger.getLogger(Plot.class.getName());
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM d");
    
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
    private static final DrawingSupplier supplier = new DefaultDrawingSupplier(
            DefaultDrawingSupplier.DEFAULT_PAINT_SEQUENCE,
            DefaultDrawingSupplier.DEFAULT_OUTLINE_PAINT_SEQUENCE,
            DefaultDrawingSupplier.DEFAULT_STROKE_SEQUENCE,
            DefaultDrawingSupplier.DEFAULT_OUTLINE_STROKE_SEQUENCE,
            // the plot data points are a small diamond shape 
            new Shape[] { new Polygon(new int[] {3, 0, -3, 0},
                    new int[] {0, 4, 0, -4}, 4) }
    );
    
    /** The default plot width. */
    private static final int DEFAULT_WIDTH = 750;
    
    /** The default plot height. */
    private static final int DEFAULT_HEIGHT = 450;
    
    /** The default number of builds on plot (all). */
    private static final String DEFAULT_NUMBUILDS = "";

    // Transient values

    /** The width of the plot. */
    private transient int width;
    
    /** The height of the plot. */
    private transient int height;
    
    /** The right-most build number on the plot. */
    private transient int rightBuildNum;
    
    /** Whether or not the plot has a legend. */
    private transient boolean hasLegend = true;

    /** Number of builds back to show on this plot from url. */
    public transient String urlNumBuilds;
    
    /** Title of plot from url. */
    public transient String urlTitle;

    /** Style of plot from url. */
    public transient String urlStyle;

    /** Use description flag from url. */
    public transient Boolean urlUseDescr;

    // Configuration values
    
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
     * This is different from the source csv file that can be used as a source for the plot.
     */
    public String csvFileName;
    
    /** The date of the last change to the CSV file. */
    private long csvLastModification;

    /** Optional style of plot: line, line3d, stackedArea, stackedBar, etc. */
    public String style;

    /** Whether or not to use build descriptions as X-axis labels. Optional. */
    public boolean useDescr;

    /**
     * Creates a new plot with the given paramenters.  If numBuilds
     * is the empty string, then all builds will be included. Must
     * not be zero.
     */
    @DataBoundConstructor
    public Plot(String title, String yaxis,
            String group, String numBuilds, String csvFileName, String style, boolean useDescr)
    {
        this.title = title;
        this.yaxis = yaxis;
        this.group = group;
        this.numBuilds = numBuilds;
        if (csvFileName == null || csvFileName.trim().length()==0) {
            //TODO: check project dir to ensure uniqueness instead of just random
            csvFileName = Math.abs(new Random().nextInt()) + ".csv";
        }
        this.csvFileName = csvFileName;
        this.style = style;
        this.useDescr = useDescr;
    }

    // needed for serialization
    public Plot() {}

    public int compareTo(Object o) {
        return title.compareTo(((Plot)o).getTitle());
    }
    
    @Override
    public String toString() {
        return "TITLE("+getTitle()+
            "),YAXIS("+yaxis+
            "),NUMSERIES("+series.length+
            "),GROUP("+group+
            "),NUMBUILDS("+numBuilds+
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
            urlUseDescr = u.equalsIgnoreCase("on") || u.equalsIgnoreCase("true");
        }
    }
    private boolean getUrlUseDescr() {
        return urlUseDescr != null ? urlUseDescr : useDescr;
    }

    /**
     * Sets the number of builds to plot from the "numbuilds" parameter
     * in the given StaplerRequest.  If the parameter doesn't exist
     * or isn't an integer then a default is used.
     */
    private void setHasLegend(StaplerRequest req) {
        String legend = req.getParameter("legend");
        hasLegend = legend == null ||
                legend.equalsIgnoreCase("on") || legend.equalsIgnoreCase("true");
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
            	// simply try and parse the string to see if it's a valid number, throw away the result.
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
    private int getHeight() {
        return height;
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
        if (ChartUtil.awtProblemCause != null) {
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
        setStyle(req);
        setUseDescr(req);
        // need to force regenerate the plot in case build
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
        if (ChartUtil.awtProblemCause != null) {
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
        setStyle(req);
        setUseDescr(req);
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
        	if (series == null)
                continue;
        	PlotPoint[] seriesData = series.loadSeries(build.getWorkspace(),logger);
            if (seriesData != null) {
        		for (PlotPoint point : seriesData) {
        			if (point == null)
        				continue;

        			rawPlotData.add(new String[] {
        				point.getYvalue(),
        				point.getLabel(),
        				build.getNumber() + "", // convert to a string
        				build.getTimestamp().getTimeInMillis() + "",
        				point.getUrl()
    				});
        		}
            }
        }
        
        // save the updated plot data to disk
        savePlotData();
        
        // currently only support for csv type
        if (getSeries() != null) {
        	Series series = getSeries()[0];
        	if ("csv".equals(series.getFileType())) {
        		saveTableData(build, series.getFile());
        	}
        }
    }

    /**
     * Generates the plot and stores it in the plot instance variable.
     * 
     * @param forceGenerate if true, force the plot to be re-generated
     *        even if the on-disk data hasn't changed
     */
    private void generatePlot(boolean forceGenerate) {
        class Label implements Comparable<Label> {
            final private Integer buildNum;
            final private String buildDate;
            final private String text;
            public Label(String buildNum, String buildTime, String text) {
                this.buildNum = Integer.parseInt(buildNum);
                this.buildDate = DATE_FORMAT.format(
                        new Date(Long.parseLong(buildTime)));
                this.text = text;
            }
            public Label(String buildNum, String buildTime) {
                this(buildNum, buildTime, null);
            }
            public int compareTo(Label that) {
                return this.buildNum - that.buildNum;
            }
            @Override public boolean equals(Object o) {
                return o instanceof Label && ((Label) o).buildNum.equals(buildNum);
            }
            @Override public int hashCode() {
                return buildNum.hashCode();
            }
            public String numDateString() {
                return "#" + buildNum + " (" + buildDate + ")";
            }
            @Override public String toString() {
                return text != null ? text : numDateString();
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
        PlotCategoryDataset dataset = new PlotCategoryDataset();
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
            Label xlabel = getUrlUseDescr()
                    ? new Label(record[2], record[3], descriptionForBuild(buildNum))
                    : new Label(record[2], record[3]);
            String url = null;
            if (record.length >= 5) url = record[4]; 
            dataset.setValue(value, url, series, xlabel);
        }
        int numBuilds;
        try {
            numBuilds = Integer.parseInt(getURLNumBuilds());
        } catch (NumberFormatException nfe) {
            numBuilds = Integer.MAX_VALUE;
        }
        dataset.clipDataset(numBuilds);
        plot = createChart(dataset);
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
            Label label = (Label) category;
            if (label.text != null) {
                domainAxis.addCategoryLabelToolTip(label, label.numDateString());
            } else {
                domainAxis.addCategoryLabelToolTip(label, descriptionForBuild(label.buildNum));
            }
        }

        AbstractCategoryItemRenderer renderer =
                (AbstractCategoryItemRenderer) categoryPlot.getRenderer();
		int numColors = dataset.getRowCount();
		for (int i = 0; i < numColors; i++) {
			renderer.setSeriesPaint(i, new Color(Color.HSBtoRGB(
					(1f / numColors) * i, 1f, 1f)));
		}        
        renderer.setStroke(new BasicStroke(2.0f));
        renderer.setToolTipGenerator(new StandardCategoryToolTipGenerator(
                "Build {1}: {2}", NumberFormat.getInstance()));
        renderer.setItemURLGenerator(new PointURLGenerator());
        if (renderer instanceof LineAndShapeRenderer) {
            LineAndShapeRenderer lasRenderer = (LineAndShapeRenderer) renderer;
            lasRenderer.setShapesVisible(true); // TODO: deprecated, may be unnecessary
        }
    }

    /**
     * Creates a Chart of the style indicated by getEffStyle() using the given dataset.
     * Defaults to using createLineChart.
     */
    private JFreeChart createChart(PlotCategoryDataset dataset) {
        String s = getUrlStyle();
        if (s.equalsIgnoreCase("area")) {
            return ChartFactory.createAreaChart(
                    getURLTitle(), /*categoryAxisLabel=*/null, getYaxis(), dataset,
                    PlotOrientation.VERTICAL, hasLegend(), /*tooltips=*/true, /*url=*/false);
        }
        if (s.equalsIgnoreCase("bar")) {
            return ChartFactory.createBarChart(
                    getURLTitle(), /*categoryAxisLabel=*/null, getYaxis(), dataset,
                    PlotOrientation.VERTICAL, hasLegend(), /*tooltips=*/true, /*url=*/false);
        }
        if (s.equalsIgnoreCase("bar3d")) {
            return ChartFactory.createBarChart3D(
                    getURLTitle(), /*categoryAxisLabel=*/null, getYaxis(), dataset,
                    PlotOrientation.VERTICAL, hasLegend(), /*tooltips=*/true, /*url=*/false);
        }
        if (s.equalsIgnoreCase("line3d")) {
            return ChartFactory.createLineChart3D(
                    getURLTitle(), /*categoryAxisLabel=*/null, getYaxis(), dataset,
                    PlotOrientation.VERTICAL, hasLegend(), /*tooltips=*/true, /*url=*/false);
        }
        if (s.equalsIgnoreCase("stackedarea")) {
            return ChartFactory.createStackedAreaChart(
                    getURLTitle(), /*categoryAxisLabel=*/null, getYaxis(), dataset,
                    PlotOrientation.VERTICAL, hasLegend(), /*tooltips=*/true, /*url=*/false);
        }
        if (s.equalsIgnoreCase("stackedbar")) {
            return ChartFactory.createStackedBarChart(
                    getURLTitle(), /*categoryAxisLabel=*/null, getYaxis(), dataset,
                    PlotOrientation.VERTICAL, hasLegend(), /*tooltips=*/true, /*url=*/false);
        }
        if (s.equalsIgnoreCase("stackedbar3d")) {
            return ChartFactory.createStackedBarChart3D(
                    getURLTitle(), /*categoryAxisLabel=*/null, getYaxis(), dataset,
                    PlotOrientation.VERTICAL, hasLegend(), /*tooltips=*/true, /*url=*/false);
        }
        if (s.equalsIgnoreCase("waterfall")) {
            return ChartFactory.createWaterfallChart(
                    getURLTitle(), /*categoryAxisLabel=*/null, getYaxis(), dataset,
                    PlotOrientation.VERTICAL, hasLegend(), /*tooltips=*/true, /*url=*/false);
        }
        return ChartFactory.createLineChart(
                getURLTitle(), /*categoryAxisLabel=*/null, getYaxis(), dataset,
                PlotOrientation.VERTICAL, hasLegend(), /*tooltips=*/true, /*url=*/false);
    }

    /**
     * Returns a trimmed description string for the build specified by the given build number.
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
            // throw away 2 header lines
            reader.readNext(); reader.readNext();
            // read each line of the CSV file and add to rawPlotData
            String [] nextLine;
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
            String[] header1 = new String[] {"Title",this.getTitle()};
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

    private void saveTableData(AbstractBuild<?,?> build, String fileName) {
    	ArrayList rawTableData = new ArrayList();

    	File tableFile = new File(project.getRootDir(), "table_"+getCsvFileName());
    	File rawCSVFile = new File(build.getWorkspace() + File.separator + fileName);
    	CSVReader existingTableReader = null;
    	CSVReader newTupleReader = null;
    	CSVWriter newTableWriter = null;
    	try {
    		newTupleReader = new CSVReader(new FileReader(rawCSVFile));

    		// new header including build #
    		String [] header = newTupleReader.readNext();
    		String [] headerIncBuild = new String [header.length+1];
    		headerIncBuild[0] = "build #";
    		System.arraycopy(header, 0, headerIncBuild, 1, header.length);
    		
    		// add a new tuple
    		String [] tuple = newTupleReader.readNext();
    		String [] tupleIncBuild = new String [tuple.length+1];
    		tupleIncBuild[0] = ""+ build.getNumber();
    		System.arraycopy(tuple, 0, tupleIncBuild, 1, tuple.length);
    		rawTableData.add (tupleIncBuild);

    		// load existing data
    		if (tableFile.exists()) {
    			existingTableReader = new CSVReader(new FileReader(tableFile));
        		// skip header
        		existingTableReader.readNext();
        		
                int numBuilds;
                try {
                    numBuilds = Integer.parseInt(getNumBuilds());
                } catch (NumberFormatException nfe) {
                    numBuilds = Integer.MAX_VALUE;
                }
                
        		String [] nextLine;
        		int count = 0;                
        		while ((nextLine = existingTableReader.readNext()) != null && count++ < numBuilds-1) {
                    rawTableData.add(nextLine);
        		}
    		}
    		
    		// write to CSV file 
    		newTableWriter = new CSVWriter(new FileWriter(tableFile));
    		newTableWriter.writeNext(headerIncBuild);
            newTableWriter.writeAll(rawTableData);
    	} catch (IOException ioe) {
            //ignore
        } finally {
            if (existingTableReader != null) {
                try {
                    existingTableReader.close();
                } catch (IOException ignore) {
                    //ignore
                }
            }
            if (newTupleReader != null) {
                try {
                    newTupleReader.close();
                } catch (IOException ignore) {
                    //ignore
                }
            }
            if (newTableWriter != null) {
                try {
                    newTableWriter.close();
                } catch (IOException ignore) {
                    //ignore
                }
            }
        }
    }
}
