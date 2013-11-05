/*
 * Copyright (c) 2007 Yahoo! Inc.  All rights reserved.  
 * Copyrights licensed under the MIT License.
 */
package hudson.plugins.plot;

import hudson.model.AbstractProject;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.ArrayList;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import au.com.bytecode.opencsv.CSVReader;
import org.apache.commons.lang.StringUtils;

/**
 * Represents a plot report for a single group of plots.
 * 
 * @author Nigel Daley
 */
public class PlotReport {
	private static final Logger LOGGER = Logger.getLogger(PlotReport.class.getName());
	
    private final AbstractProject<?, ?> project;
    
	/**
	 * The sorted list of plots that belong to the same group.
	 */
	private Plot[] plots;
	
	/**
	 * The group these plots belong to.
	 */
	private String group;
	
	public PlotReport(AbstractProject<?, ?> project, String group, Plot[] plots) {
		Arrays.sort(plots);
		this.plots = plots;
		this.group = group;
		this.project = project;
	}

	// called from PlotReport/index.jelly
    public AbstractProject<?, ?> getProject() {
        return project;
    }

	// called from PlotReport/index.jelly
    public String getGroup() {
        return group;
    }
    
    // called from PlotReport/index.jelly
	public Plot[] getPlots() {
		return plots;
	}
	
	// called from PlotReport/index.jelly
	public void doGetPlot(StaplerRequest req, StaplerResponse rsp) {
		String i = req.getParameter("index");
		Plot plot = getPlot(i);
		try {
			plot.plotGraph(req,rsp);
		} catch (IOException ioe) {
			LOGGER.log(Level.INFO,"Exception plotting graph",ioe);
		}
	}
	
	// called from PlotReport/index.jelly
	public void doGetPlotMap(StaplerRequest req, StaplerResponse rsp) {
		String i = req.getParameter("index");
		Plot plot = getPlot(i);
		try {
			plot.plotGraphMap(req,rsp);
		} catch (IOException ioe) {
			LOGGER.log(Level.INFO,"Exception plotting graph",ioe);
		}
	}

	// called from PlotReport/index.jelly
	public boolean getDisplayTableFlag(int i) {
		Plot plot = getPlot(Integer.toString(i));

		if (plot.getSeries()!=null) {
    	    Series series = plot.getSeries()[0];
    	    return (series instanceof CSVSeries) && ((CSVSeries)series).getDisplayTableFlag();
    	}
    	return false;
	}
	
	// called from PlotReport/index.jelly
    public ArrayList getTable(int i) {
    	ArrayList<ArrayList<String>> tableData = new ArrayList<ArrayList<String>>();
    	
    	Plot plot = getPlot(""+i);
    	
        // load existing csv file
        File plotFile = new File(project.getRootDir(), plot.getCsvFileName());
        if (!plotFile.exists()) {
            return tableData;
        }
        CSVReader reader = null;
        try {
            reader = new CSVReader(new FileReader(plotFile));
            // throw away 2 header lines
            reader.readNext(); reader.readNext();
            // array containing header titles
            ArrayList<String> header = new ArrayList<String>();
            header.add(Messages.Plot_Build() + " #");
            tableData.add(header);
            String[] nextLine;
            while ((nextLine = reader.readNext()) != null) {
                String buildNumber = nextLine[2];
                if (project.getBuildByNumber(Integer.parseInt(buildNumber)) == null) {
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
                ArrayList<String> tableRow = null;
                for (int j = 1; j < tableData.size(); j++) {
                    ArrayList<String> r = tableData.get(j);
                    if (StringUtils.equals(r.get(0), buildNumber)) {
                        // found table row corresponding to the build number
                        tableRow = r;
                        break;
                    }
                }
                // table row corresponding to the build number not found
                if (tableRow == null) {
                    // create table row with build number at first column
                    tableRow = new ArrayList<String>();
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
            for (ArrayList<String> tableRow : tableData) {
                for (int j = tableRow.size(); j < lastColumn; j++) {
                    tableRow.add(StringUtils.EMPTY);
                }
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
    	return tableData;
    }
	
    private Plot getPlot(String i) {
    	try {
    		Plot p = plots[Integer.valueOf(i)];
    		p.setProject(project);
    		return p; 
    	} catch (NumberFormatException ignore) {
    		//ignore
    		return null;
    	}
    }
}