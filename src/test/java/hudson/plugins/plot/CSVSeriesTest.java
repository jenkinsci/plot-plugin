/*
 * Copyright (c) 2008-2009 Yahoo! Inc.  All rights reserved.
 * The copyrights to the contents of this file are licensed under the MIT License (http://www.opensource.org/licenses/mit-license.php)
 */
package hudson.plugins.plot;

import au.com.bytecode.opencsv.CSVReader;
import hudson.FilePath;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Test a CSV series.
 *
 * @author Allen Reese
 */
public class CSVSeriesTest extends SeriesTestCase {
    private static final Logger LOGGER = Logger.getLogger(CSVSeriesTest.class.getName());

    private static final String[] FILES = {"test.csv", "test_trailing_spaces.csv", "test_trailing_semicolon.csv"};
    private static final int[] LINES = {2, 3, 2};  // lines in the file including header
    private static final int[] COLUMNS = {8, 3, 9};  // columns in the file
    private static final int[] CORRECTED_COLUMNS = {8, 3, 8};  // corrected for the trailing comma case
    private static final int[] TOTAL_POINTS = {8, 6, 8};  // total data points in the file
    private static final String[] LAST_COLUMN_NAME = {"error %", "thing", "error %"};  // the label on the last column

    @Test
    public void testCSVSeriesWithNullExclusionValuesSetsDisplayTableFlag() {
        CSVSeries series;
        for (int index = 0; index < FILES.length; index++) {
            series = new CSVSeries(FILES[index],
                    null,
                    null,
                    null,
                    true);
            assertTrue(series.getDisplayTableFlag());
        }
    }

    @Test
    public void testCSVSeriesWithNoExclusions() {
        for (int index = 0; index < FILES.length; index++) {
            // Check the number of columns
            int columns = -1;

            try {
                columns = getNumColumns(workspaceRootDir, FILES[index]);
            } catch (IOException | InterruptedException e) {
                fail("Exception " + e.toString());
            }

            assertEquals(COLUMNS[index], columns);

            // Create a new CSV series.
            CSVSeries series = new CSVSeries(FILES[index],
                    "http://localhost:8080/%name%/%index%/",
                    "OFF",
                    "",
                    false);

            LOGGER.info("Created series " + series.toString());
            // test the basic subclass properties.
            testSeries(series, FILES[index], "", "csv");

            // load the series.
            List<PlotPoint> points = series.loadSeries(workspaceRootDir, 0, System.out);
            LOGGER.info("Got " + points.size() + " plot points");
            testPlotPoints(points, TOTAL_POINTS[index]);

            int numberOfPoints = 0;
            for (int lines = 1; lines < LINES[index]; lines++) {
                for (int columnIndex = 0; columnIndex < CORRECTED_COLUMNS[index]; columnIndex++) {
                    PlotPoint point = points.get(numberOfPoints);
                    assertEquals("http://localhost:8080/" + point.getLabel() + "/" + columnIndex + "/", point.getUrl());
                    numberOfPoints++;
                }
            }
        }
    }

    @Test
    public void testCSVSeriesIncludeOnlyLastColumn() {
        for (int index = 0; index < FILES.length; index++) {
            // Create a new CSV series.
            CSVSeries series = new CSVSeries(FILES[index],
                    "http://localhost:8080/%name%/%index%/",
                    "INCLUDE_BY_STRING",
                    LAST_COLUMN_NAME[index],
                    false);

            LOGGER.info("Created series " + series.toString());

            // load the series.
            List<PlotPoint> points = series.loadSeries(workspaceRootDir,
                    0,
                    System.out);
            LOGGER.info("Got " + points.size() + " plot points");
            testPlotPoints(points, LINES[index] - 1);  // expect one point per line, minus one header line

            PlotPoint point = points.get(0);
            int columnIndex = CORRECTED_COLUMNS[index] - 1; // correct column to starting index of 0
            assertEquals("http://localhost:8080/" + point.getLabel() + "/" + columnIndex + "/", point.getUrl());
        }
    }

    @Test
    public void testCSVSeriesWithTrailingSemicolonDoesntCreateExtraneousPoint() {
        String file = "test_trailing_semicolon.csv";
        // Create a new CSV series.
        CSVSeries series = new CSVSeries(file,
                "http://localhost:8080/%name%/%index%/",
                "OFF",
                "",
                false);

        LOGGER.info("Created series " + series.toString());
        // test the basic subclass properties.
        testSeries(series, file, "", "csv");

        // load the series.
        List<PlotPoint> points = series.loadSeries(workspaceRootDir,
                0,
                System.out);
        LOGGER.info("Got " + points.size() + " plot points");
        testPlotPoints(points, 8);
    }

    @Test
    public void testCSVExclusionValue_IntArray() {
        CSVSeries series = new CSVSeries("test.csv",
                null,
                "EXCLUDE_BY_STRING",
                "123,345",
                false);
        List<PlotPoint> points = series.loadSeries(workspaceRootDir,
                0,
                System.out);
        LOGGER.info("Got " + points.size() + " plot points");
        testPlotPoints(points, 8);
    }

    @Test
    public void testCSVInclusionValue_IntArray() {
        CSVSeries series = new CSVSeries("test.csv",
                null,
                "INCLUDE_BY_STRING",
                "123,345",
                false);
        List<PlotPoint> points = series.loadSeries(workspaceRootDir,
                0,
                System.out);
        LOGGER.info("Got " + points.size() + " plot points");
        testPlotPoints(points, 0);
    }

    @Test
    public void testExcludeByRegexInAList() {
        CSVSeries series = new CSVSeries("test_regex-webstatistics.csv",
                null,
                "EXCLUDE_BY_STRING",
                "\"HTTP_[4,5]\\d{2}\",\"Hits\",\"Throughput\",\"RunId\",\"Trend Measurement Type\"",
                false);
        List<PlotPoint> points = series.loadSeries(workspaceRootDir,
                0,
                System.out);
        LOGGER.info("Got " + points.size() + " plot points");
        testPlotPoints(points, 3);
    }

    @Test
    public void testIncludeBySingleRegexWithComma() {
        CSVSeries series = new CSVSeries("test_regex-webstatistics.csv",
                null,
                "INCLUDE_BY_STRING",
                "\"HTTP_[2,3]\\d{2}\"",
                false);
        List<PlotPoint> points = series.loadSeries(workspaceRootDir,
                0,
                System.out);
        LOGGER.info("Got " + points.size() + " plot points");
        testPlotPoints(points, 3);
    }

    @Test(expected = java.util.regex.PatternSyntaxException.class)
    public void testIncludeBySingleRegexWithComma_unescaped_shouldFail() {
        CSVSeries series = new CSVSeries("test_regex-webstatistics.csv",
                null,
                "INCLUDE_BY_STRING",
                "HTTP_[2,3]\\d{2}",
                false);
        List<PlotPoint> points = series.loadSeries(workspaceRootDir,
                0,
                System.out);
        LOGGER.info("Got " + points.size() + " plot points");
        testPlotPoints(points, 0);
    }

    @Test
    public void testIncludeTestuserByRegex() {
        CSVSeries series = new CSVSeries("test_regex-by-suffix.csv",
                null,
                "INCLUDE_BY_STRING",
                "\".*testUser_1\",\".*testUser_2\"",
                false);
        List<PlotPoint> points = series.loadSeries(workspaceRootDir,
                0,
                System.out);
        LOGGER.info("Got " + points.size() + " plot points");
        testPlotPoints(points, 6);
    }

    @Test
    public void testExcludeTestuserByRegex() {
        // Testing a little more complex regex with case insensitive and boundaries
        CSVSeries series = new CSVSeries("test_regex-by-suffix.csv",
                null,
                "EXCLUDE_BY_STRING",
                "\"(?i)(RunID)\",\"Login_.*\",\".*testUser_[1-2]{1,2}\"",
                false);
        List<PlotPoint> points = series.loadSeries(workspaceRootDir,
                0,
                System.out);
        LOGGER.info("Got " + points.size() + " plot points");
        testPlotPoints(points, 4);
    }

    @Test
    public void testExcludeHeaderByRegex() {
        CSVSeries series = new CSVSeries("test_exclusions.csv",
                null,
                "EXCLUDE_BY_STRING",
                "\".*min\",\".*max\",\"host\",\"threads\"",
                false);
        List<PlotPoint> points = series.loadSeries(workspaceRootDir,
                0,
                System.out);
        LOGGER.info("Got " + points.size() + " plot points");
        testPlotPoints(points, 8);
    }

    @Test
    public void testIncludeHeaderByRegex_testcsv() {
        CSVSeries series = new CSVSeries("test.csv",
                null,
                "INCLUDE_BY_STRING",
                "\"m.*\",\"error.*\"",
                false);
        List<PlotPoint> points = series.loadSeries(workspaceRootDir,
                0,
                System.out);
        LOGGER.info("Got " + points.size() + " plot points");
        testPlotPoints(points, 4);
    }

    @Test
    public void testIncludeHeaderByRegex() {
        CSVSeries series = new CSVSeries("test_exclusions.csv",
                null,
                "INCLUDE_BY_STRING",
                "\".*avg\"",
                false);
        List<PlotPoint> points = series.loadSeries(workspaceRootDir,
                0,
                System.out);
        LOGGER.info("Got " + points.size() + " plot points");
        testPlotPoints(points, 4);
    }

    /**
     * Making sure, the 3 exclusionValues are put into the List by surrounding even the String with ""
     */
    @Test
    public void testIncludeHeaderByRegexAndEscapedString() {
        CSVSeries series = new CSVSeries("test_exclusions.csv",
                null,
                "INCLUDE_BY_STRING",
                "\"errors\",\".*avg\",\"autoplay count\"",
                false);
        List<PlotPoint> points = series.loadSeries(workspaceRootDir,
                0,
                System.out);
        LOGGER.info("Got " + points.size() + " plot points");
        testPlotPoints(points, 6);
    }


    /**
     * By not surrounding single Strings with "", only the Regex will be put into the List
     */
    @Test
    public void testIncludeHeaderByRegexAndUnescapedString() {
        CSVSeries series = new CSVSeries("test_exclusions.csv",
                null,
                "INCLUDE_BY_STRING",
                "errors,\".*avg\",autoplay count",
                false);
        List<PlotPoint> points = series.loadSeries(workspaceRootDir,
                0,
                System.out);
        LOGGER.info("Got " + points.size() + " plot points");
        testPlotPoints(points, 4);
    }

    @Test
    public void testIncludeByRegexInAString() {
        CSVSeries series = new CSVSeries("test_regex-by-suffix.csv",
                null,
                "INCLUDE_BY_STRING",
                "\".*_(OpenStartPage|Login)_.*\"",
                false);
        List<PlotPoint> points = series.loadSeries(workspaceRootDir,
                0,
                System.out);
        LOGGER.info("Got " + points.size() + " plot points");
        testPlotPoints(points, 8);
    }

    @Test
    public void testIncludeByString() {
        CSVSeries series = new CSVSeries("test.csv",
                null,
                "INCLUDE_BY_STRING",
                "Avg,Median",
                false);
        List<PlotPoint> points = series.loadSeries(workspaceRootDir,
                0,
                System.out);
        LOGGER.info("Got " + points.size() + " plot points");
        testPlotPoints(points, 2);
    }

    private int getNumColumns(FilePath workspaceRootDir, String file) throws IOException, InterruptedException {
        CSVReader csvReader = null;
        InputStream inputStream = null;
        InputStreamReader inputReader = null;

        FilePath[] seriesFiles;
        try {
            seriesFiles = workspaceRootDir.list(file);

            if (seriesFiles != null && seriesFiles.length < 1) {
                LOGGER.info("No plot data file found: " + workspaceRootDir.getName() + " " + file);
                return -1;
            }

            LOGGER.info("Loading plot series data from: " + file);

            inputStream = seriesFiles[0].read();

            inputReader = new InputStreamReader(inputStream);
            csvReader = new CSVReader(inputReader);

            // save the header line to use it for the plot labels.
            String[] headerLine = csvReader.readNext();

            LOGGER.info("Got " + headerLine.length + " columns");
            return headerLine.length;
        } finally {
            try {
                if (csvReader != null) {
                    csvReader.close();
                }
            } catch (IOException e) {
                fail("Exception " + e);
            }
            IOUtils.closeQuietly(inputReader);
            IOUtils.closeQuietly(inputStream);
        }
    }

}
