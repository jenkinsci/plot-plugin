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
import java.util.regex.Pattern;

import javax.servlet.ServletException;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.ObjectUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import au.com.bytecode.opencsv.CSVReader;

/**
 * Represents a plot data series configuration from an CSV file.
 *
 * @author Allen Reese
 *
 */
public class CSVSeries extends Series {
    private static transient final Logger LOGGER = Logger
            .getLogger(CSVSeries.class.getName());
    // Debugging hack, so I don't have to change FINE/INFO...
    private static transient final Level defaultLogLevel = Level.FINEST;
    private static transient final Pattern PAT_COMMA = Pattern.compile(",");

    public enum InclusionFlag {
        OFF, INCLUDE_BY_STRING, EXCLUDE_BY_STRING, INCLUDE_BY_COLUMN, EXCLUDE_BY_COLUMN,
    }

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
     * @param req
     *            Stapler request
     * @param radioButtonId
     *            ID used to find the parameters specific to this instance.
     * @throws ServletException
     */
    @DataBoundConstructor
    public CSVSeries(String file, String url, String inclusionFlag,
            String exclusionValues, boolean displayTableFlag) {
        super(file, "", "csv");

        this.url = url;

        if (exclusionValues == null) {
            this.inclusionFlag = InclusionFlag.OFF;
            return;
        }

        this.inclusionFlag = InclusionFlag.valueOf(inclusionFlag);
        this.exclusionValues = exclusionValues;
        this.displayTableFlag = displayTableFlag;

        loadExclusionSet();
    }

    public String getInclusionFlag() {
        return ObjectUtils.toString(inclusionFlag);
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
    public List<PlotPoint> loadSeries(FilePath workspaceRootDir,
            int buildNumber, PrintStream logger) {
        CSVReader reader = null;
        InputStream in = null;
        InputStreamReader inputReader = null;

        try {
            List<PlotPoint> ret = new ArrayList<PlotPoint>();

            FilePath[] seriesFiles = null;
            try {
                seriesFiles = workspaceRootDir.list(getRealFile());
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE,
                        "Exception trying to retrieve series files", e);
                return null;
            }

            if (ArrayUtils.isEmpty(seriesFiles)) {
                LOGGER.info("No plot data file found: "
                        + workspaceRootDir.getName() + " " + getRealFile());
                return null;
            }

            try {
                if (LOGGER.isLoggable(defaultLogLevel))
                    LOGGER.log(defaultLogLevel,
                            "Loading plot series data from: " + getRealFile());

                in = seriesFiles[0].read();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE,
                        "Exception reading plot series data from "
                                + seriesFiles[0], e);
                return null;
            }

            if (LOGGER.isLoggable(defaultLogLevel))
                LOGGER.log(defaultLogLevel, "Loaded CSV Plot file: "
                        + getRealFile());

            // load existing plot file
            inputReader = new InputStreamReader(in);
            reader = new CSVReader(inputReader);
            String[] nextLine;

            // save the header line to use it for the plot labels.
            String[] headerLine = reader.readNext();

            // read each line of the CSV file and add to rawPlotData
            int lineNum = 0;
            while ((nextLine = reader.readNext()) != null) {
                // skip empty lines
                if (nextLine.length == 1 && nextLine[0].length() == 0)
                    continue;

                for (int index = 0; index < nextLine.length; index++) {
                    String yvalue;
                    String label = null;

                    if (index > nextLine.length)
                        continue;

                    yvalue = nextLine[index];

                    if (index < headerLine.length)
                        label = headerLine[index];

                    if (label == null || label.length() <= 0) {
                        // if there isn't a label, use the index as the label
                        label = "" + index;
                    }

                    // LOGGER.finest("Loaded point: " + point);

                    // create a new point with the yvalue from the csv file and
                    // url from the URL_index in the properties file.
                    if (!excludePoint(label, index)) {
                        PlotPoint point = new PlotPoint(yvalue, getUrl(url,
                                label, index, buildNumber), label);
                        if (LOGGER.isLoggable(defaultLogLevel))
                            LOGGER.log(defaultLogLevel, "CSV Point: [" + index
                                    + ":" + lineNum + "]" + point);
                        ret.add(point);
                    } else {
                        if (LOGGER.isLoggable(defaultLogLevel))
                            LOGGER.log(defaultLogLevel, "excluded CSV Column: "
                                    + index + " : " + label);
                    }
                }
                lineNum++;
            }

            return ret;

        } catch (IOException ioe) {
            LOGGER.log(Level.SEVERE, "Exception loading series", ioe);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignore) {
                    // ignore
                }
            }
            IOUtils.closeQuietly(inputReader);
            IOUtils.closeQuietly(in);
        }

        return null;
    }

    /**
     * This function checks the exclusion/inclusion filters from the properties
     * file and returns true if a point should be excluded.
     * 
     * @return true if the point should be excluded based on label or column
     */
    private boolean excludePoint(String label, int index) {
        if (inclusionFlag == null || inclusionFlag == InclusionFlag.OFF)
            return false;

        boolean retVal = false;

        switch (inclusionFlag) {
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
            LOGGER.finest(((retVal) ? "excluded" : "included")
                    + " CSV Column: " + index + " : " + label);

        return retVal;
    }

    /**
     * This function loads the set of columns that should be included or
     * excluded.
     */
    private void loadExclusionSet() {
        if (inclusionFlag == InclusionFlag.OFF)
            return;

        if (exclusionValues == null) {
            inclusionFlag = InclusionFlag.OFF;
            return;
        }

        switch (inclusionFlag) {
        case INCLUDE_BY_STRING:
        case EXCLUDE_BY_STRING:
            strExclusionSet = new HashSet<String>();
            break;

        case INCLUDE_BY_COLUMN:
        case EXCLUDE_BY_COLUMN:
            colExclusionSet = new HashSet<Integer>();
            break;
        }

        for (String str : PAT_COMMA.split(exclusionValues)) {
            if (str == null || str.length() <= 0)
                continue;

            switch (inclusionFlag) {
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
                    LOGGER.log(Level.SEVERE, "Exception converting to integer",
                            nfe);
                }
                break;
            }
        }
    }
}
