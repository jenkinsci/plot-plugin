/*
 * Copyright (c) 2008-2009 Yahoo! Inc.  All rights reserved.
 * The copyrights to the contents of this file are licensed under the MIT License (http://www.opensource.org/licenses/mit-license.php)
 */

package hudson.plugins.plot;

import org.junit.Test;

import java.util.List;

/**
 * Test a Properties file series.
 *
 * @author Allen Reese
 */
public class PropertiesSeriesTest extends SeriesTestCase {
    private static final String[] FILES = {"test.properties"};
    private static final String[] LABELS = {"testLabel"};

    @Test
    public void testPropertiesSeries() {
        // Create a new properties series.
        PropertiesSeries propSeries = new PropertiesSeries(FILES[0], LABELS[0]);

        // test the basic subclass properties.
        testSeries(propSeries, FILES[0], LABELS[0], "properties");

        // load the series.
        List<PlotPoint> points = propSeries.loadSeries(workspaceRootDir, 0, System.err);
        testPlotPoints(points, 1);
    }
}
