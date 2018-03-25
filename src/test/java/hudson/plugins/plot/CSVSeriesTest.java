/*
 * Copyright (c) 2008-2009 Yahoo! Inc.  All rights reserved.
 * The copyrights to the contents of this file are licensed under the MIT License (http://www.opensource.org/licenses/mit-license.php)
 */
package hudson.plugins.plot;

import au.com.bytecode.opencsv.CSVReader;
import hudson.FilePath;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.logging.Logger;
import org.apache.commons.io.IOUtils;

/**
 * Test a CSV series.
 *
 * @author Allen Reese
 */
public class CSVSeriesTest extends SeriesTestCase {
    private static final Logger LOGGER = Logger.getLogger(CSVSeriesTest.class.getName());

    private static final String[] FILES = {"test.csv", "spacestest.csv", "test_trailing_semicolon.csv"};
    private static final int[] LINES = {2, 3, 2};  //lines in the file including header
    private static final int[] COLUMNS = {8, 3, 9};  //columns in the file
    private static final int[] CORRECTED_COLS = {8, 3, 8};  //corrected for the trailing comma case
    private static final int[] TOTAL_POINTS = {8, 6, 8};  //total data points in the file
    private static final String[] LAST_COLUMN_NAME = {"error %", "thing", "error %"};  //the label on the last column
    private static final String[] LAST_POINT = {"0.37", "42", "0.37"};  //the value of the last data point

    public void testCSVSeriesWithNullExclusionValuesSetsDisplayTableFlag() {
        CSVSeries series;
        for (int testfilenum = 0; testfilenum < FILES.length; testfilenum++) {
            series = new CSVSeries(FILES[testfilenum], null, null, null, true);
            assertTrue(series.getDisplayTableFlag());
        }
    }

    public void testCSVSeriesWithNoExclusions() {
        for (int testfilenum = 0; testfilenum < FILES.length; testfilenum++) {
            // first create a FilePath to load the test Properties file.
            File workspaceDirFile = new File("target/test-classes/");
            FilePath workspaceRootDir = new FilePath(workspaceDirFile);

            LOGGER.info("workspace File path: " + workspaceDirFile.getAbsolutePath());
            LOGGER.info("workspace Dir path: " + workspaceRootDir.getName());

            // Check the number of columns
            int columns = -1;

            try {
                columns = getNumColumns(workspaceRootDir, FILES[testfilenum]);
            } catch (IOException e) {
                assertFalse(true);
            } catch (InterruptedException e) {
                assertFalse(true);
            }

            assertEquals(COLUMNS[testfilenum], columns);

            // Create a new CSV series.
            CSVSeries series = new CSVSeries(FILES[testfilenum], "http://localhost:8080/%name%/%index%/", "OFF", "", false);

            LOGGER.info("Created series " + series.toString());
            // test the basic subclass properties.
            testSeries(series, FILES[testfilenum], "", "csv");

            // load the series.
            List<PlotPoint> points = series.loadSeries(workspaceRootDir, 0, System.out);
            LOGGER.info("Got " + points.size() + " plot points");
            testPlotPoints(points, TOTAL_POINTS[testfilenum]);

            int pointnum = 0;
            for (int lines = 1; lines < LINES[testfilenum]; lines++) {
                for (int colnum = 0; colnum < CORRECTED_COLS[testfilenum]; colnum++) {
                    PlotPoint point = points.get(pointnum);
                    assertEquals("http://localhost:8080/" + point.getLabel() + "/" + colnum + "/", point.getUrl());
                    pointnum++;
                }
            }
        }
    }

    public void testCSVSeriesIncludeOnlyLastColumn() {
        for (int testfilenum = 0; testfilenum < FILES.length; testfilenum++) {
            // first create a FilePath to load the test Properties file.
            File workspaceDirFile = new File("target/test-classes/");
            FilePath workspaceRootDir = new FilePath(workspaceDirFile);

            LOGGER.info("workspace File path: " + workspaceDirFile.getAbsolutePath());
            LOGGER.info("workspace Dir path: " + workspaceRootDir.getName());

            // Create a new CSV series.
            CSVSeries series = new CSVSeries(FILES[testfilenum], "http://localhost:8080/%name%/%index%/", "INCLUDE_BY_STRING", LAST_COLUMN_NAME[testfilenum], false);

            LOGGER.info("Created series " + series.toString());

            // load the series.
            List<PlotPoint> points = series.loadSeries(workspaceRootDir, 0, System.out);
            LOGGER.info("Got " + points.size() + " plot points");
            testPlotPoints(points, LINES[testfilenum] - 1);  //expect one point per line, minus one header line

            PlotPoint point = points.get(0);
            int colnum = CORRECTED_COLS[testfilenum] - 1; //correct colnum to starting index of 0
            assertEquals("http://localhost:8080/" + point.getLabel() + "/" + colnum + "/", point.getUrl());
        }
    }

    public void testCSVSeriesWithTrailingSemicolonDoesntCreateExtraneousPoint() {
        // first create a FilePath to load the test Properties file.
        File workspaceDirFile = new File("target/test-classes/");
        FilePath workspaceRootDir = new FilePath(workspaceDirFile);
        String file = "test_trailing_semicolon.csv";

        LOGGER.info("workspace File path: " + workspaceDirFile.getAbsolutePath());
        LOGGER.info("workspace Dir path: " + workspaceRootDir.getName());

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
        CSVReader csvreader = null;
        InputStream in = null;
        InputStreamReader inputReader = null;

        FilePath[] seriesFiles;
        try {
            seriesFiles = workspaceRootDir.list(file);

            if (seriesFiles != null && seriesFiles.length < 1) {
                LOGGER.info("No plot data file found: " + workspaceRootDir.getName() + " " + file);
                return -1;
            }

            LOGGER.info("Loading plot series data from: " + file);

            in = seriesFiles[0].read();

            inputReader = new InputStreamReader(in);
            csvreader = new CSVReader(inputReader);

            // save the header line to use it for the plot labels.
            String[] headerLine = csvreader.readNext();

            LOGGER.info("Got " + headerLine.length + " columns");
            return headerLine.length;
        } finally {
            try {
                if (csvreader != null) {
                    csvreader.close();
                }
            } catch (IOException e) {
                assertFalse("Exception " + e, true);
            }
            IOUtils.closeQuietly(inputReader);
            IOUtils.closeQuietly(in);
        }
    }
}
