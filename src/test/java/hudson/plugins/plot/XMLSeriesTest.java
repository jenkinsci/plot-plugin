package hudson.plugins.plot;

import hudson.FilePath;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;

/**
 * Test an XML series.
 *
 * @author Brian Roe
 */
public class XMLSeriesTest extends SeriesTestCase {
    private static final String TEST_XML_FILE = "test.xml";
    private static final String TEST2_XML_FILE = "test2.xml";
    private static final String TEST3_XML_FILE = "test3.xml";

    private File workspaceDirFile;
    private FilePath workspaceRootDir;

    @Before
    public void setUp() {
        // first create a FilePath to load the test Properties file.
        workspaceDirFile = new File("target/test-classes/");
        workspaceRootDir = new FilePath(workspaceDirFile);
    }

    @After
    public void tearDown() {
        workspaceRootDir = null;
        workspaceDirFile = null;
    }

    public void testXMLSeries_WhenNodesSharingAParentHaveOneStringAndOneNumericContent_ThenCoalesceNodesToPointLabelledWithStringContent() {
        // Create a new XML series.
        String xpath = "//UIAction/name|//UIAction/numCalls";
        XMLSeries series = new XMLSeries(TEST2_XML_FILE, xpath, "NODESET", null);

        // test the basic subclass properties.
        testSeries(series, TEST2_XML_FILE, "", "xml");

        // load the series.
        List<PlotPoint> points = series.loadSeries(workspaceRootDir, 0, System.out);
        assertNotNull(points);
        assertEquals(4, points.size());
        Map<String, Double> map = new HashMap<>();
        for (PlotPoint point : points) {
            map.put(point.getLabel(), Double.parseDouble(point.getYvalue()));
        }

        assertEquals(7, map.get("AxTermDataService.updateItem").intValue());
        assertEquals(2, map.get("AxTermDataService.createEntity").intValue());
        testPlotPoints(points, 4);
    }

    public void testXMLSeries_WhenNodesHaveNoContent_ThenCoalesceForAttributes() {
        // Create a new XML series.
        String xpath =
                "//testcase[@name='testOne'] | //testcase[@name='testTwo'] | //testcase[@name='testThree']";

        XMLSeries series = new XMLSeries(TEST_XML_FILE, xpath, "NODESET", null);

        // test the basic subclass properties.
        testSeries(series, TEST_XML_FILE, "", "xml");

        // load the series.
        List<PlotPoint> points = series.loadSeries(workspaceRootDir, 0, System.out);
        assertNotNull(points);
        assertEquals(points.size(), 3);
        assertEquals(points.get(0).getLabel(), "testOne");
        assertEquals(points.get(1).getLabel(), "testTwo");
        assertEquals(points.get(2).getLabel(), "testThree");
        testPlotPoints(points, 3);
    }

    public void testXMLSeriesNodeset() {
        // Create a new XML series.
        String xpath = "//testcase";

        XMLSeries series = new XMLSeries(TEST_XML_FILE, xpath, "NODESET", null);

        // test the basic subclass properties.
        testSeries(series, TEST_XML_FILE, "", "xml");

        // load the series.
        List<PlotPoint> points = series.loadSeries(workspaceRootDir, 0, System.out);
        assertNotNull(points);
        assertEquals(4, points.size());
        assertEquals(points.get(0).getLabel(), "testOne");
        assertEquals(points.get(1).getLabel(), "testTwo");
        assertEquals(points.get(2).getLabel(), "testThree");
        assertEquals(points.get(3).getLabel(), "testFour");
        assertEquals(points.get(3).getYvalue(), "1234.56");
        testPlotPoints(points, 4);
    }

    public void testXMLSeries_WhenAllNodesAreNumeric_ThenPointsAreLabelledWithNodeName() {
        // Create a new XML series.
        String xpath = "/results/testcase/*";

        XMLSeries series = new XMLSeries(TEST3_XML_FILE, xpath, "NODESET", null);

        // test the basic subclass properties.
        testSeries(series, TEST3_XML_FILE, "", "xml");

        // load the series.
        List<PlotPoint> points = series.loadSeries(workspaceRootDir, 0, System.out);
        assertNotNull(points);
        assertEquals(2, points.size());
        assertEquals(points.get(0).getLabel(), "one");
        assertEquals(points.get(0).getYvalue(), "0.521");
        testPlotPoints(points, 2);
    }

    public void testXMLSeriesEmptyNodeset() {
        // Create a new XML series.
        String xpath = "/there/is/no/such/element";

        XMLSeries series = new XMLSeries(TEST3_XML_FILE, xpath, "NODESET", null);

        // test the basic subclass properties.
        testSeries(series, TEST3_XML_FILE, "", "xml");

        // load the series.
        List<PlotPoint> points = series.loadSeries(workspaceRootDir, 0, System.out);
        assertNotNull(points);
        assertEquals(0, points.size());
        testPlotPoints(points, 0);
    }

    public void testXMLSeriesNode() {
        // Create a new XML series.
        String xpath = "//testcase[@name='testThree']";
        XMLSeries series = new XMLSeries(TEST_XML_FILE, xpath, "NODE", null);

        // test the basic subclass properties.
        testSeries(series, TEST_XML_FILE, "", "xml");

        // load the series.
        List<PlotPoint> points = series.loadSeries(workspaceRootDir, 0, System.out);
        assertNotNull(points);
        assertEquals(points.size(), 1);
        assertEquals(Double.parseDouble(points.get(0).getYvalue()), 27d);
        testPlotPoints(points, 1);
    }

    public void testXMLSeriesString() {
        // Create a new XML series.
        String xpath = "//testcase[@name='testOne']/@time";
        XMLSeries series = new XMLSeries(TEST_XML_FILE, xpath, "STRING", null);

        // test the basic subclass properties.
        testSeries(series, TEST_XML_FILE, "", "xml");

        // load the series.
        List<PlotPoint> points = series.loadSeries(workspaceRootDir, 0, System.out);
        assertNotNull(points);
        testPlotPoints(points, 1);
    }

    public void testXMLSeriesNumber() {
        // Create a new XML series.
        String xpath =
                "concat(//testcase[@name='testOne']/@name, '=', //testcase[@name='testOne']/@time)";
        xpath = "//testcase[@name='testOne']/@time";
        XMLSeries series = new XMLSeries(TEST_XML_FILE, xpath, "NUMBER",
                "splunge");

        // test the basic subclass properties.
        testSeries(series, TEST_XML_FILE, "", "xml");

        // load the series.
        List<PlotPoint> points = series.loadSeries(workspaceRootDir, 0, System.out);
        assertNotNull(points);
        testPlotPoints(points, 1);
    }

    public void testXMLSeriesUrl() {
        // Create a new XML series.
        String xpath = "/results/testcase/*";

        XMLSeries series = new XMLSeries(TEST3_XML_FILE, xpath, "NODESET",
                "http://localhost/%build%/%name%/%index%");

        // test the basic subclass properties.
        testSeries(series, TEST3_XML_FILE, "", "xml");

        // load the series.
        List<PlotPoint> points = series.loadSeries(workspaceRootDir, 42, System.out);
        assertNotNull(points);
        testPlotPoints(points, 2);
        assertEquals("http://localhost/42/one/0", points.get(0).getUrl());
        assertEquals("http://localhost/42/two/0", points.get(1).getUrl());
    }

    @Ignore
    public void testXMLSeriesBoolean() {
        // Create a new XML series.
        String xpath = "//testcase[@name='testOne']/@time";
        XMLSeries series = new XMLSeries(TEST_XML_FILE, xpath, "BOOLEAN", null);

        // test the basic subclass properties.
        testSeries(series, TEST_XML_FILE, "", "xml");

        // load the series.
        List<PlotPoint> points = series.loadSeries(workspaceRootDir, 0, System.out);
        assertNotNull(points);
        testPlotPoints(points, 1);
    }
}
