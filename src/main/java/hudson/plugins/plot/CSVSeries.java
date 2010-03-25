/*
 * Copyright (c) 2008-2009 Yahoo! Inc.  All rights reserved.
 * The copyrights to the contents of this file are licensed under the MIT License (http://www.opensource.org/licenses/mit-license.php)
 */
  
package hudson.plugins.plot;

import hudson.FilePath;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletException;

import au.com.bytecode.opencsv.CSVReader;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Represents a plot data series configuration from an CSV file.
 * 
 * @author Allen Reese
 *
 */
public class CSVSeries extends Series {
    private static transient final Logger LOGGER = Logger.getLogger(Series.class.getName());
    // Debugging hack, so I don't have to change FINE/INFO...
    private static transient final Level defaultLogLevel = Level.FINEST;
    private static transient final Pattern PAT_COMMA = Pattern.compile(","); 
	private static transient final Pattern PAT_NAME = Pattern.compile("%name%");
	private static transient final Pattern PAT_INDEX = Pattern.compile("%index%");
	
	public static enum InclusionFlag
	{
		OFF,
		INCLUDE_BY_STRING,
		EXCLUDE_BY_STRING,
		INCLUDE_BY_COLUMN,
		EXCLUDE_BY_COLUMN,
	};

	/**
	 * Set for excluding values by column name
	 */
	private Set<String> strExclusionSet;

	/**
	 * Set for excluding values by column #
	 */
	private Set<Integer> colExclusionSet;
	
	/**
	 * Flag controlling how values are excluded.
	 */
	private InclusionFlag inclusionFlag = InclusionFlag.OFF;
	
	/**
	 * Comma separated list of columns to exclude.
	 */
	private String exclusionValues;

	/**
	 * Url to use as a base for mapping points. 
	 */
	private String url;
	
	private boolean displayTableFlag;

	/**
	 * 
	 * @param file
	 * @param label
	 * @param req Stapler request
	 * @param radioButtonId ID used to find the parameters specific to this instance.
	 * @throws ServletException
	 */
    @DataBoundConstructor
    public CSVSeries(String file, String url, String inclusionFlag, String exclusionValues, boolean displayTableFlag)
    {
    	super(file, "", "csv");
    	
    	this.url = url;

    	if (exclusionValues == null)
		{
			this.inclusionFlag = InclusionFlag.OFF;
			return;
		}

    	this.inclusionFlag = InclusionFlag.valueOf(inclusionFlag);
    	this.exclusionValues = exclusionValues;
    	this.displayTableFlag = displayTableFlag;

    	loadExclusionSet();
    }

    public String getInclusionFlag() {
        return inclusionFlag.toString();
    }

    public String getExclusionValues() {
        return exclusionValues;
    }

    public String getUrl() {
        return url;
    }
	
    public boolean getDisplayTableFlag() {
    	return displayTableFlag;
    }

    /**
     * Load the series from a properties file.
     */
	@Override
	public PlotPoint[] loadSeries(FilePath workspaceRootDir, PrintStream logger) {
        CSVReader reader = null;
		InputStream in = null;
		InputStreamReader inputReader = null;
		
        try {
			List<PlotPoint> ret = new ArrayList<PlotPoint>();
	
			FilePath[] seriesFiles = null;
	        try {
	            seriesFiles = workspaceRootDir.list(getFile());
	        } catch (Exception e) {
	        	LOGGER.warning("Exception trying to retrieve series files: " + e);
	            return null;
	        }
	
	        if (seriesFiles != null && seriesFiles.length < 1) {
	        	LOGGER.info("No plot data file found: " + workspaceRootDir.getName() + " " + getFile());
	            return null;
	        }
	        
	        try {
            	if (LOGGER.isLoggable(defaultLogLevel))
	        		LOGGER.log(defaultLogLevel,"Loading plot series data from: " + getFile());
	        	
	            in = seriesFiles[0].read();
	        } catch (Exception e) {
	        	LOGGER.warning("Exception reading plot series data from: " + seriesFiles[0] + " " + e);
	            return null;
	        }
	
        	if (LOGGER.isLoggable(defaultLogLevel))
        		LOGGER.log(defaultLogLevel,"Loaded CSV Plot file: " + getFile());
	    	
	        // load existing plot file
        	inputReader = new InputStreamReader(in);
            reader = new CSVReader(inputReader);
            String[] nextLine;

            // save the header line to use it for the plot labels.
            String[] headerLine=reader.readNext();

        	// read each line of the CSV file and add to rawPlotData
            int lineNum=0;
            while ((nextLine = reader.readNext()) != null)
            {
            	// skip empty lines
            	if (nextLine.length==1 && nextLine[0].length()==0)
            		continue;
            	
            	for (int index = 0; index < nextLine.length; index++)
            	{
                	String yvalue;
                	String label = null;
                	
                	if (index > nextLine.length)
                		continue;
                	
                	yvalue = nextLine[index];

                	if (index < headerLine.length)
                		label = headerLine[index];
                	
                	if (label == null || label.length()<=0)
                	{
                		// if there isn't a label, use the index as the label
                		label = "" + index; 
                	}
                	
                	//LOGGER.finest("Loaded point: " + point);
                	
            		// create a new point with the yvalue from the csv file and url from the URL_index in the properties file.
                	if (!excludePoint(label,index)) {
                		PlotPoint point = new PlotPoint(yvalue, getUrl(label,index), label); 
                    	if (LOGGER.isLoggable(defaultLogLevel))
                    		LOGGER.log(defaultLogLevel,"CSV Point: [" +index + ":" + lineNum +"]"+ point);
                		ret.add(point);
                	} else {
                    	if (LOGGER.isLoggable(defaultLogLevel))
                    		LOGGER.log(defaultLogLevel,"excluded CSV Column: " + index + " : " + label);
                	}
            	}
            	++lineNum;
            }
        
            return ret.toArray(new PlotPoint[ret.size()]);

        } catch (IOException ioe) {
            //ignore
        	if (LOGGER.isLoggable(defaultLogLevel))
        		LOGGER.log(defaultLogLevel,"Exception: " + ioe);
        } finally {
        	if (in != null) {
                try {
                    in.close();
                } catch (IOException ignore) {
                    //ignore
                }
        	}
        	
        	if (inputReader != null) {
                try {
                	inputReader.close();
                } catch (IOException ignore) {
                    //ignore
                }
            }

        	if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignore) {
                    //ignore
                }
            }
        }

        return null;
    }

	/**
	 * This function checks the exclusion/inclusion filters from the properties file and
	 * returns true if a point should be excluded.
	 * @return true if the point should be excluded based on label or column
	 */
	private boolean excludePoint(String label,int index)
	{
		if (inclusionFlag == null || inclusionFlag == InclusionFlag.OFF)
			return false;
		
		boolean retVal=false;
		
		switch (inclusionFlag)
		{
			case INCLUDE_BY_STRING:
				// if the set contains it, don't exclude it.
				retVal = !(strExclusionSet.contains(label));
				break;
				
			case EXCLUDE_BY_STRING:
				// if the set doesn't contain it, exclude it.
				retVal = strExclusionSet.contains(label);
				break;
				
			case INCLUDE_BY_COLUMN:
				// if the set contains it, don't exclude it.
				retVal = !(colExclusionSet.contains(Integer.valueOf(index)));
				break;

			case EXCLUDE_BY_COLUMN:
				// if the set doesn't contain it, don't exclude it.
				retVal = colExclusionSet.contains(Integer.valueOf(index));
				break;
		}
		
    	if (LOGGER.isLoggable(Level.FINEST))
    		LOGGER.finest(((retVal)?"excluded":"included") + " CSV Column: " + index + " : " + label);
		
		return retVal;
	}
	
	/**
	 * This function loads the set of columns that should be included or excluded.
	 */
	private void loadExclusionSet()
	{
		if (inclusionFlag == InclusionFlag.OFF)
			return;
		
		if (exclusionValues == null)
		{
			inclusionFlag = InclusionFlag.OFF;
			return;
		}
		
		switch (inclusionFlag) 
		{
			case INCLUDE_BY_STRING:
			case EXCLUDE_BY_STRING:
				strExclusionSet = new HashSet<String>();
				break;
				
			case INCLUDE_BY_COLUMN:
			case EXCLUDE_BY_COLUMN:
				colExclusionSet = new HashSet<Integer>();
				break;
		}

		for (String str : PAT_COMMA.split(exclusionValues))
		{
			if (str==null || str.length()<=0)
				continue;
			
			switch (inclusionFlag) 
			{
				case INCLUDE_BY_STRING:
				case EXCLUDE_BY_STRING:
                	if (LOGGER.isLoggable(Level.FINEST))
                		LOGGER.finest(inclusionFlag + " CSV Column: " + str);
					strExclusionSet.add(str);
					break;
					
				case INCLUDE_BY_COLUMN:
				case EXCLUDE_BY_COLUMN:
					try {
                    	if (LOGGER.isLoggable(Level.FINEST))
                    		LOGGER.finest(inclusionFlag + " CSV Column: " + str);
						colExclusionSet.add(Integer.valueOf(str));
					} catch (NumberFormatException nfe) {
						// ignore badly formatted columns.
					}
					break;
			}
		}
	}
	
	/**
	 * Return the url that should be used for this point.
	 * @param label Name of the column
	 * @param index Index of the column
	 * @return url for the label.
	 */
	private String getUrl(String label,int index)
	{
		/*
		 * Check the name first, and do replacement upon it.
		 */
		Matcher nameMatcher = PAT_NAME.matcher(label);
		if (nameMatcher.find())
		{
			url = nameMatcher.replaceAll(label);
		}

		/*
		 * Check the index, and do replacement on it.
		 */
		Matcher indexMatcher = PAT_INDEX.matcher(label);
		if (indexMatcher.find())
		{
			url = indexMatcher.replaceAll(label);
		}
		
		return url;
	}
}
