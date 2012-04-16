package hudson.plugins.plot;

import hudson.FilePath;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.junit.Ignore;

/**
 * Test an XML series.
 * 
 * @author Brian Roe
 *
 */
public class XMLSeriesTest extends SeriesTestCase {
    private static final String TEST_XML_FILE = "test.xml";
    private static final String TEST2_XML_FILE = "test2.xml";
	
	public void testXMLSeriesString2()
	{
		// first create a FilePath to load the test Properties file.
		File workspaceDirFile = new File ("target/test-classes/");
		FilePath workspaceRootDir = new FilePath (workspaceDirFile);
		
		// Create a new XML series.
		String xpath = "//UIAction/name|//UIAction/numCalls";
		XMLSeries series = new XMLSeries(TEST2_XML_FILE, xpath, "NODESET", null);

		// test the basic subclass properties.
		testSeries(series, TEST2_XML_FILE, "", "xml");

		// load the series.
		PlotPoint[] points = series.loadSeries(workspaceRootDir, System.out);
		assertNotNull(points);
		assertEquals (4, points.length);
		Map<String, Integer>  map = new HashMap<String, Integer>();
		for (int i = 0; i < points.length; i++) {
			map.put(points[i].getLabel(), Integer.parseInt(points[i].getYvalue()));
		}
		
		assertEquals (7, map.get("AxTermDataService.updateItem").intValue());
		assertEquals (2, map.get("AxTermDataService.createEntity").intValue());
		testPlotPoints(points, 4);
	}
	
	public void testXMLSeriesNodesetSubset()
	{
		// first create a FilePath to load the test Properties file.
		File workspaceDirFile = new File ("target/test-classes/");
		FilePath workspaceRootDir = new FilePath (workspaceDirFile);
		
		// Create a new XML series.
		String xpath = "//testcase[@name='testOne'] | //testcase[@name='testTwo'] | //testcase[@name='testThree']";

		XMLSeries series = new XMLSeries(TEST_XML_FILE, xpath, "NODESET", null);

		// test the basic subclass properties.
		testSeries(series, TEST_XML_FILE, "", "xml");

		// load the series.
		PlotPoint[] points = series.loadSeries(workspaceRootDir, System.out);
		assertNotNull(points);
		assertEquals (points.length, 3);
		assertEquals (points[0].getLabel(), "testOne");
		assertEquals (points[1].getLabel(), "testTwo");
		assertEquals (points[2].getLabel(), "testThree");
		testPlotPoints(points, 3);
	}
	
	public void testXMLSeriesNodeset()
	{
		// first create a FilePath to load the test Properties file.
		File workspaceDirFile = new File ("target/test-classes/");
		FilePath workspaceRootDir = new FilePath (workspaceDirFile);
		
		// Create a new XML series.
		String xpath = "//testcase";

		XMLSeries series = new XMLSeries(TEST_XML_FILE, xpath, "NODESET", null);

		// test the basic subclass properties.
		testSeries(series, TEST_XML_FILE, "", "xml");

		// load the series.
		PlotPoint[] points = series.loadSeries(workspaceRootDir, System.out);
		assertNotNull(points);
		assertEquals (4, points.length);
		assertEquals (points[0].getLabel(), "testOne");
		assertEquals (points[1].getLabel(), "testTwo");
		assertEquals (points[2].getLabel(), "testThree");
		assertEquals (points[3].getLabel(), "testFour");
		assertEquals (points[3].getYvalue(), "1234.56");
		testPlotPoints(points, 4);
	}
	
	public void testXMLSeriesNode()
	{
		// first create a FilePath to load the test Properties file.
		File workspaceDirFile = new File ("target/test-classes/");
		FilePath workspaceRootDir = new FilePath (workspaceDirFile);
		
		// Create a new XML series.
		String xpath = "//testcase[@name='testThree']";
		XMLSeries series = new XMLSeries(TEST_XML_FILE, xpath, "NODE", null);

		// test the basic subclass properties.
		testSeries(series, TEST_XML_FILE, "", "xml");

		// load the series.
		PlotPoint[] points = series.loadSeries(workspaceRootDir, System.out);
		assertNotNull(points);
		assertEquals (points.length, 1);
		assertEquals (points[0].getYvalue(), "27");
		testPlotPoints(points, 1);
	}
	
	public void testXMLSeriesString()
	{
		// first create a FilePath to load the test Properties file.
		File workspaceDirFile = new File ("target/test-classes/");
		FilePath workspaceRootDir = new FilePath (workspaceDirFile);
		
		// Create a new XML series.
		String xpath = "//testcase[@name='testOne']/@time";
		XMLSeries series = new XMLSeries(TEST_XML_FILE, xpath, "STRING", null);

		// test the basic subclass properties.
		testSeries(series, TEST_XML_FILE, "", "xml");

		// load the series.
		PlotPoint[] points = series.loadSeries(workspaceRootDir, System.out);
		assertNotNull(points);
		testPlotPoints(points, 1);
	}
	
	public void testXMLSeriesNumber()
	{
		// first create a FilePath to load the test Properties file.
		File workspaceDirFile = new File ("target/test-classes/");
		FilePath workspaceRootDir = new FilePath (workspaceDirFile);
		
		// Create a new XML series.
		String xpath = "concat(//testcase[@name='testOne']/@name, '=', //testcase[@name='testOne']/@time)";
		xpath = "//testcase[@name='testOne']/@time";
		XMLSeries series = new XMLSeries(TEST_XML_FILE, xpath, "NUMBER", "splunge");

		// test the basic subclass properties.
		testSeries(series, TEST_XML_FILE, "", "xml");

		// load the series.
		PlotPoint[] points = series.loadSeries(workspaceRootDir, System.out);
		assertNotNull(points);
		testPlotPoints(points, 1);
	}
	
	@Ignore
	public void testXMLSeriesBoolean()
	{
		// first create a FilePath to load the test Properties file.
		File workspaceDirFile = new File ("target/test-classes/");
		FilePath workspaceRootDir = new FilePath (workspaceDirFile);
		
		// Create a new XML series.
		String xpath = "//testcase[@name='testOne']/@time";
		XMLSeries series = new XMLSeries(TEST_XML_FILE, xpath, "BOOLEAN", null);

		// test the basic subclass properties.
		testSeries(series, TEST_XML_FILE, "", "xml");

		// load the series.
		PlotPoint[] points = series.loadSeries(workspaceRootDir, System.out);
		assertNotNull(points);
		testPlotPoints(points, 1);
	}
	
}
