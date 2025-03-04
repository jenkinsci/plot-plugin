/*
 * Copyright (c) 2008-2009 Yahoo! Inc.  All rights reserved.
 * The copyrights to the contents of this file are licensed under the MIT License (http://www.opensource.org/licenses/mit-license.php)
 */

package hudson.plugins.plot;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.FilePath;
import java.io.File;
import java.util.List;
import java.util.logging.Logger;

/**
 * Stub to hold common series test functionality.
 *
 * @author Allen Reese
 */
class SeriesTestUtils {
    private static final Logger LOGGER = Logger.getLogger(SeriesTestUtils.class.getName());

    protected static final FilePath WORKSPACE_ROOT_DIR = createTestDirectory();

    protected static void testSeries(Series series, String file, String label, String type) {
        // verify the properties was created correctly
        assertNotNull(series);

        assertEquals(file, series.file, "File name is not configured correctly");
        assertEquals(label, series.label, "Label is not configured correctly");
        assertEquals(type, series.fileType, "Type is not configured correctly");
    }

    protected static void testPlotPoints(List<PlotPoint> points, int expected) {
        assertTrue(expected > -1, "Must have more than 0 columns");

        assertNotNull(points, "loadSeries failed to return any points");
        if (points.size() != expected) {
            StringBuilder debug = new StringBuilder();
            int i = 0;
            for (PlotPoint p : points) {
                debug.append("[").append(i++).append("]").append(p).append("\n");
            }

            assertEquals(
                    expected,
                    points.size(),
                    "loadSeries loaded wrong number of points: expected " + expected + ", got " + points.size() + "\n"
                            + debug);
        }

        // validate each point.
        for (int i = 0; i < points.size(); i++) {
            assertNotNull(points.get(i), "loadSeries returned null point at index " + i);
            assertNotNull(points.get(i).getYvalue(), "loadSeries returned null yvalue at index " + i);
            assertNotNull(points.get(i).getUrl(), "loadSeries returned null url at index " + i);
            assertNotNull(points.get(i).getLabel(), "loadSeries returned null label at index " + i);

            // make sure the yvalue's can be parsed
            String yValue = points.get(i).getYvalue();
            assertDoesNotThrow(
                    () -> Double.parseDouble(yValue),
                    "loadSeries returned invalid yvalue " + yValue + " at index " + i);
        }
    }

    private static FilePath createTestDirectory() {
        File file = new File("target/test-classes/");
        FilePath dir = new FilePath(file);
        LOGGER.info("Workspace File path: " + file.getAbsolutePath());
        LOGGER.info("Workspace Dir path: " + dir.getName());
        return dir;
    }
}
