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
            series = new CSVSeries(FILES[index], null, null, null, true);
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
            CSVSeries series = new CSVSeries(FILES[index], "http://localhost:8080/%name%/%index%/", "OFF", "", false);

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
            CSVSeries series = new CSVSeries(FILES[index], "http://localhost:8080/%name%/%index%/", "INCLUDE_BY_STRING", LAST_COLUMN_NAME[index], false);

            LOGGER.info("Created series " + series.toString());

            // load the series.
            List<PlotPoint> points = series.loadSeries(workspaceRootDir, 0, System.out);
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
                "http://localhost:8080/%name%/%index%/", "OFF", "", false);

        LOGGER.info("Created series " + series.toString());
        // test the basic subclass properties.
        testSeries(series, file, "", "csv");

        // load the series.
        List<PlotPoint> points = series.loadSeries(workspaceRootDir, 0, System.out);
        LOGGER.info("Got " + points.size() + " plot points");
        testPlotPoints(points, 8);
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

    @Test
    public void testCSVSeriesMultipleFiles() {
        // Create a new CSV series.
        CSVSeries series = new CSVSeries("*.csv", "http://localhost:8080/%name%/%index%/", "INCLUDE_BY_STRING", "Avg", false);

        LOGGER.info("Created series " + series.toString());

        // load the series.
        List<PlotPoint> points = series.loadSeries(workspaceRootDir, 0, System.out);
        LOGGER.info("Got " + points.size() + " plot points");
        testPlotPoints(points, 2);  // Avg is found in 2 files, each containing 1 line
    }

}
