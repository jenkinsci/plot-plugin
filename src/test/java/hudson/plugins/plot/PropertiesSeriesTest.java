/*
 * Copyright (c) 2008-2009 Yahoo! Inc.  All rights reserved.
 * The copyrights to the contents of this file are licensed under the MIT License (http://www.opensource.org/licenses/mit-license.php)
 */

package hudson.plugins.plot;

import hudson.FilePath;

import java.io.File;

/**
 * Test a Properties file series.
 * 
 * @author Allen Reese
 *
 */
public class PropertiesSeriesTest extends SeriesTestCase {
	private static final String[] files = {
		"test.properties",
	};
	
	private static final String[] labels = {
		"testLabel",
	};

	public void testPropertiesSeries()
	{
		// first create a FilePath to load the test Properties file.
		File workspaceDirFile = new File ("target/test-classes/");
		FilePath workspaceRootDir = new FilePath (workspaceDirFile);
		
		System.out.println ("workspace path path: " + workspaceDirFile.getAbsolutePath());
		
		// Create a new properties series.
		PropertiesSeries propSeries = new PropertiesSeries(files[0],labels[0]);
	
		// test the basic subclass properties.
		testSeries(propSeries, files[0], labels[0], "properties");
		
		// load the series.
		PlotPoint[] points = propSeries.loadSeries(workspaceRootDir, System.err);
		testPlotPoints(points, 1);
	}
}
