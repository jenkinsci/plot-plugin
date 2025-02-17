/*
 * Copyright (c) 2008-2009 Yahoo! Inc.  All rights reserved.
 * The copyrights to the contents of this file are licensed under the MIT License
 * (http://www.opensource.org/licenses/mit-license.php)
 */
package hudson.plugins.plot;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Descriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.ObjectUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * Represents a plot data series configuration from an CSV file.
 *
 * @author Allen Reese
 */
public class CSVSeries extends Series {
    private static final transient Logger LOGGER = Logger.getLogger(CSVSeries.class.getName());
    // Debugging hack, so I don't have to change FINE/INFO...
    private static final transient Level DEFAULT_LOG_LEVEL = Level.FINEST;
    private static final transient Pattern PAT_SEMICOLON_ENCLOSURE = Pattern.compile("\"(.*?)\"");
    private static final transient Pattern PAT_COMMA = Pattern.compile(",");

    public enum InclusionFlag {
        OFF,
        INCLUDE_BY_STRING,
        EXCLUDE_BY_STRING,
        INCLUDE_BY_COLUMN,
        EXCLUDE_BY_COLUMN
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
     * Comma separated list of columns to exclude.
     */
    private List<String> exclusionValuesList;

    /**
     * Url to use as a base for mapping points.
     */
    private String url;

    /**
     * Show table of the single values in charts.
     */
    private boolean displayTableFlag;

    @DataBoundConstructor
    public CSVSeries(String file, String url, String inclusionFlag, String exclusionValues, boolean displayTableFlag) {
        super(file, "", "csv");

        this.url = url;
        this.displayTableFlag = displayTableFlag;
        this.exclusionValues = exclusionValues;

        if (this.exclusionValues == null) {
            this.inclusionFlag = InclusionFlag.OFF;
        } else {
            this.inclusionFlag = InclusionFlag.valueOf(inclusionFlag);
            this.exclusionValuesList = new ArrayList<>();

            /**
             * first: try to handle the regex. The values are enclosed by ""
             * If there are no values found, use plain splitting by comma
             */
            Matcher m = PAT_SEMICOLON_ENCLOSURE.matcher(this.exclusionValues);
            int results = 0;
            while (m.find()) {
                this.exclusionValuesList.add(m.group().replaceAll("\"", ""));
                results++;
            }

            if (results == 0) {
                this.exclusionValuesList = Arrays.asList(PAT_COMMA.split(this.exclusionValues));
            }
        }
        loadExclusionSet();
    }

    public String getInclusionFlag() {
        return ObjectUtils.toString(inclusionFlag);
    }

    public String getExclusionValues() {
        return exclusionValues;
    }

    public List<String> getExclusionValuesList() {
        return exclusionValuesList;
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
    public List<PlotPoint> loadSeries(FilePath workspaceRootDir, int buildNumber, PrintStream logger) {
        List<PlotPoint> plotPoints = null;
        FilePath[] seriesFiles;
        try {
            seriesFiles = workspaceRootDir.list(getFile());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Exception trying to retrieve series files", e);
            return null;
        }

        if (ArrayUtils.isEmpty(seriesFiles)) {
            LOGGER.info("No plot data file found: " + workspaceRootDir.getName() + " " + getFile());
            return null;
        }

        for (FilePath seriesFile : seriesFiles) {
            List<PlotPoint> seriesList = loadSeriesFile(seriesFile, buildNumber);
            if (seriesList != null) {
                if (plotPoints != null) {
                    plotPoints.addAll(seriesList);
                } else {
                    plotPoints = seriesList;
                }
            }
        }
        return plotPoints;
    }

    private List<PlotPoint> loadSeriesFile(FilePath seriesFile, int buildNumber) {
        CSVReader reader = null;
        InputStream in = null;
        InputStreamReader inputReader = null;

        try {
            List<PlotPoint> ret = new ArrayList<>();

            try {
                if (LOGGER.isLoggable(DEFAULT_LOG_LEVEL)) {
                    LOGGER.log(DEFAULT_LOG_LEVEL, "Loading plot series data from: " + getFile());
                }

                in = seriesFile.read();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Exception reading plot series data from " + seriesFile, e);
                return null;
            }

            if (LOGGER.isLoggable(DEFAULT_LOG_LEVEL)) {
                LOGGER.log(DEFAULT_LOG_LEVEL, "Loaded CSV Plot file: " + getFile());
            }

            // load existing plot file
            inputReader = new InputStreamReader(in, Charset.defaultCharset());
            reader = new CSVReader(inputReader);
            String[] nextLine;

            // save the header line to use it for the plot labels.
            String[] headerLine = reader.readNext();

            // read each line of the CSV file and add to rawPlotData
            int lineNum = 0;
            while ((nextLine = reader.readNext()) != null) {
                // skip empty lines
                if (nextLine.length == 1 && nextLine[0].isEmpty()) {
                    continue;
                }

                for (int index = 0; index < nextLine.length; index++) {
                    String yvalue;
                    String label = null;

                    yvalue = nextLine[index].trim();

                    // empty value, caused by e.g. trailing comma in CSV
                    if (yvalue.trim().isEmpty()) {
                        continue;
                    }

                    if (index < headerLine.length) {
                        label = headerLine[index].trim();
                    }

                    if (label == null || label.length() <= 0) {
                        // if there isn't a label, use the index as the label
                        label = "" + index;
                    }

                    // LOGGER.finest("Loaded point: " + point);

                    // create a new point with the yvalue from the csv file and
                    // url from the URL_index in the properties file.
                    if (!excludePoint(label, index)) {
                        PlotPoint point = new PlotPoint(yvalue, getUrl(url, label, index, buildNumber), label);
                        if (LOGGER.isLoggable(DEFAULT_LOG_LEVEL)) {
                            LOGGER.log(DEFAULT_LOG_LEVEL, "CSV Point: [" + index + ":" + lineNum + "]" + point);
                        }
                        ret.add(point);
                    } else {
                        if (LOGGER.isLoggable(DEFAULT_LOG_LEVEL)) {
                            LOGGER.log(DEFAULT_LOG_LEVEL, "excluded CSV Column: " + index + " : " + label);
                        }
                    }
                }
                lineNum++;
            }

            return ret;
        } catch (CsvValidationException | IOException ioe) {
            LOGGER.log(Level.SEVERE, "Exception loading series", ioe);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Failed to close series reader", e);
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
    private boolean excludePoint(final String label, int index) {
        if (inclusionFlag == null || inclusionFlag == InclusionFlag.OFF) {
            return false;
        }

        boolean retVal =
                switch (inclusionFlag) {
                    case INCLUDE_BY_STRING ->
                    // if the set contains it, don't exclude it.
                    !checkExclusionSet(label);
                    case EXCLUDE_BY_STRING ->
                    // if the set doesn't contain it, exclude it.
                    checkExclusionSet(label);
                    case INCLUDE_BY_COLUMN ->
                    // if the set contains it, don't exclude it.
                    !(colExclusionSet.contains(index));
                    case EXCLUDE_BY_COLUMN ->
                    // if the set doesn't contain it, don't exclude it.
                    colExclusionSet.contains(index);
                    default -> false;
                };

        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.finest(((retVal) ? "excluded" : "included") + " CSV Column: " + index + " : " + label);
        }

        return retVal;
    }

    /**
     * Checks if the current label / header is known in the strExclusionSet (plain text or regex).
     *
     * @param label
     * @return if the label is in the set
     */
    private boolean checkExclusionSet(String label) {
        if (strExclusionSet.contains(label)) {
            return true;
        } else {
            for (String s : strExclusionSet) {
                if (checkPatternIsValid(s) && label.matches(s)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean checkPatternIsValid(String pattern) {
        try {
            Pattern.compile(pattern);
        } catch (java.util.regex.PatternSyntaxException e) {
            return false;
        }
        return true;
    }

    /**
     * This function loads the set of columns that should be included or
     * excluded.
     */
    private void loadExclusionSet() {
        if (inclusionFlag == InclusionFlag.OFF) {
            return;
        }

        if (exclusionValues == null) {
            inclusionFlag = InclusionFlag.OFF;
            return;
        }

        switch (inclusionFlag) {
            case INCLUDE_BY_STRING:
            case EXCLUDE_BY_STRING:
                strExclusionSet = new HashSet<>();
                break;
            case INCLUDE_BY_COLUMN:
            case EXCLUDE_BY_COLUMN:
                colExclusionSet = new HashSet<>();
                break;
            default:
                LOGGER.log(Level.SEVERE, "Failed to initialize columns exclusions set.");
        }

        for (String str : exclusionValuesList) {
            if (str == null || str.length() <= 0) {
                continue;
            }

            switch (inclusionFlag) {
                case INCLUDE_BY_STRING:
                case EXCLUDE_BY_STRING:
                    if (LOGGER.isLoggable(Level.FINEST)) {
                        LOGGER.finest(inclusionFlag + " CSV Column: " + str);
                    }
                    strExclusionSet.add(str);
                    break;
                case INCLUDE_BY_COLUMN:
                case EXCLUDE_BY_COLUMN:
                    try {
                        if (LOGGER.isLoggable(Level.FINEST)) {
                            LOGGER.finest(inclusionFlag + " CSV Column: " + str);
                        }
                        colExclusionSet.add(Integer.valueOf(str));
                    } catch (NumberFormatException nfe) {
                        LOGGER.log(Level.SEVERE, "Exception converting to integer", nfe);
                    }
                    break;
                default:
                    LOGGER.log(Level.SEVERE, "Failed to identify columns exclusions.");
            }
        }
    }

    @Override
    public Descriptor<Series> getDescriptor() {
        return new DescriptorImpl();
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Series> {
        @NonNull
        public String getDisplayName() {
            return Messages.Plot_CsvSeries();
        }

        @Override
        public Series newInstance(StaplerRequest2 req, @NonNull JSONObject formData) throws FormException {
            return SeriesFactory.createSeries(formData, req);
        }
    }
}
