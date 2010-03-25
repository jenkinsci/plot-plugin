/*
 * Copyright (c) 2008-2009 Yahoo! Inc.  All rights reserved.
 * The copyrights to the contents of this file are licensed under the MIT License (http://www.opensource.org/licenses/mit-license.php)
 */

package hudson.plugins.plot;

import org.jvnet.hudson.test.HudsonTestCase;

/**
 * Stub to hold common series test functionality.
 * 
 * @author Allen Reese
 *
 */
public class SeriesTestCase extends HudsonTestCase {
	public void testDummy()
	{
		// Allow us to subclass, and not have the tests puke.
	}
	
	public void testSeries(Series series,String file, String label, String type)
	{
		
		// verify the properties was created correctly
		assertNotNull(series);
		
		assertEquals("File name is not configured correctly", file, series.file);
		assertEquals("Label is not configured correctly", label, series.label);
		assertEquals("Type is not configured correctly", type, series.fileType);
	}
	
	public void testPlotPoints(PlotPoint[] points,int expected)
	{
		assertTrue("Must have more than 0 columns",expected>-1);
		
		assertNotNull("loadSeries failed to return any points",points);
		if (points.length!=expected)
		{
			StringBuilder debug=new StringBuilder();
			int i = 0;
			for (PlotPoint p : points)
			{
				debug.append("[").append(i++).append("]").append(p).append("\n");
			}
			
			assertEquals("loadSeries loaded wrong number of points: expected " + expected + ", got " + points.length + "\n"+debug,expected,points.length);
		}
		
		
		// validate each point.
		for (int i=0;i<points.length;i++)
		{
			assertNotNull("loadSeries returned null point at index " + i, points[i]);
			assertNotNull("loadSeries returned null yvalue at index " + i, points[i].getYvalue());
			assertNotNull("loadSeries returned null url at index " + i, points[i].getUrl());
			assertNotNull("loadSeries returned null label at index " + i, points[i].getLabel());
			
			// make sure the yvalue's can be parsed
			try {
				Double.parseDouble(points[i].getYvalue());
			} catch (NumberFormatException nfe) {
				assertTrue ("loadSeries returned invalid yvalue " + points[i].getYvalue() + " at index " + i + " Exception " + nfe.toString(), false);
			}
		}
	}
}
