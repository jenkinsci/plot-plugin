/*
 * Copyright (c) 2008-2009 Yahoo! Inc.  All rights reserved.
 * The copyrights to the contents of this file are licensed under the MIT License (http://www.opensource.org/licenses/mit-license.php)
 */
package hudson.plugins.plot;

import hudson.FilePath;
import hudson.plugins.plot.CSVSeries;
import hudson.plugins.plot.PlotPoint;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;

import au.com.bytecode.opencsv.CSVReader;

/**
 * Test a CSV series.
 *
 * @author Allen Reese
 *
 */
public class CSVSeriesTest extends SeriesTestCase {
    private static transient final Logger LOGGER = Logger
            .getLogger(CSVSeriesTest.class.getName());

    private static final String[] files = { "test.csv", };

    public void testCSVSeriesWithNoExclusions() {
        // first create a FilePath to load the test Properties file.
        File workspaceDirFile = new File("target/test-classes/");
        FilePath workspaceRootDir = new FilePath(workspaceDirFile);

        LOGGER.info("workspace File path: "
                + workspaceDirFile.getAbsolutePath());
        LOGGER.info("workspace Dir path: " + workspaceRootDir.getName());

        // Check the number of columns
        int columns = -1;

        try {
            columns = getNumColumns(workspaceRootDir, files[0]);
        } catch (IOException e) {
            assertFalse(true);
        } catch (InterruptedException e) {
            assertFalse(true);
        }

        // Create a new CSV series.
        CSVSeries series = new CSVSeries(files[0],
                "http://localhost:8080/%name%/%index%/", "OFF", "", false);

        LOGGER.info("Created series " + series.toString());
        // test the basic subclass properties.
        testSeries(series, files[0], "", "csv");

        // load the series.
        List<PlotPoint> points = series.loadSeries(workspaceRootDir, 0,
                System.out);
        LOGGER.info("Got " + points.size() + " plot points");
        testPlotPoints(points, columns);

        for (int i = 0; i < points.size(); i++) {
            PlotPoint point = points.get(i);
            assertEquals("http://localhost:8080/" + point.getLabel() + "/" + i
                    + "/", point.getUrl());
        }
    }

    public void testCSVSeriesWithTrailingSemicolonDoesntCreateExtraneousPoint() {
        // first create a FilePath to load the test Properties file.
        File workspaceDirFile = new File("target/test-classes/");
        FilePath workspaceRootDir = new FilePath(workspaceDirFile);
        String file = "test_trailing_semicolon.csv";
        
        LOGGER.info("workspace File path: "
                + workspaceDirFile.getAbsolutePath());
        LOGGER.info("workspace Dir path: " + workspaceRootDir.getName());

        // Create a new CSV series.
        CSVSeries series = new CSVSeries(file,
                "http://localhost:8080/%name%/%index%/", "OFF", "", false);

        LOGGER.info("Created series " + series.toString());
        // test the basic subclass properties.
        testSeries(series, file, "", "csv");

        // load the series.
        List<PlotPoint> points = series.loadSeries(workspaceRootDir, 0,
                System.out);
        LOGGER.info("Got " + points.size() + " plot points");
        testPlotPoints(points, 8);
    }
    
    private int getNumColumns(FilePath workspaceRootDir, String file)
            throws IOException, InterruptedException {
        CSVReader csvreader = null;
        InputStream in = null;
        InputStreamReader inputReader = null;

        FilePath[] seriesFiles = null;
        try {
            seriesFiles = workspaceRootDir.list(file);

            if (seriesFiles != null && seriesFiles.length < 1) {
                LOGGER.info("No plot data file found: "
                        + workspaceRootDir.getName() + " " + file);
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
                if (csvreader != null)
                    csvreader.close();
            } catch (IOException e) {
            }
            IOUtils.closeQuietly(inputReader);
            IOUtils.closeQuietly(in);
        }
    }
}
