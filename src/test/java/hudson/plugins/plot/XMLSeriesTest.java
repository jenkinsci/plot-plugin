package hudson.plugins.plot;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * Test an XML series.
 *
 * @author Brian Roe
 */
public class XMLSeriesTest extends SeriesTestCase {
    private static final String TEST_XML_FILE = "test.xml";
    private static final String TEST2_XML_FILE = "test2.xml";
    private static final String TEST3_XML_FILE = "test3.xml";
    private static final String TEST4_XML_FILE = "test4.xml";

    @Test
    public void
            testXMLSeries_WhenNodesSharingAParentHaveOneStringAndOneNumericContent_ThenCoalesceNodesToPointLabelledWithStringContent() {
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

    @Test
    public void testXMLSeries_WhenNodesHaveNoContent_ThenCoalesceForAttributes() {
        // Create a new XML series.
        String xpath = "//testcase[@name='testOne'] | //testcase[@name='testTwo'] | //testcase[@name='testThree']";

        XMLSeries series = new XMLSeries(TEST_XML_FILE, xpath, "NODESET", null);

        // test the basic subclass properties.
        testSeries(series, TEST_XML_FILE, "", "xml");

        // load the series.
        List<PlotPoint> points = series.loadSeries(workspaceRootDir, 0, System.out);
        assertNotNull(points);
        assertEquals(3, points.size());
        assertEquals("testOne", points.get(0).getLabel());
        assertEquals("testTwo", points.get(1).getLabel());
        assertEquals("testThree", points.get(2).getLabel());
        testPlotPoints(points, 3);
    }

    @Test
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
        assertEquals("testOne", points.get(0).getLabel());
        assertEquals("testTwo", points.get(1).getLabel());
        assertEquals("testThree", points.get(2).getLabel());
        assertEquals("testFour", points.get(3).getLabel());
        assertEquals("1234.56", points.get(3).getYvalue());
        testPlotPoints(points, 4);
    }

    @Test
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
        assertEquals("one", points.get(0).getLabel());
        assertEquals("0.521", points.get(0).getYvalue());
        testPlotPoints(points, 2);
    }

    @Test
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

    @Test
    public void testXMLSeriesNode() {
        // Create a new XML series.
        String xpath = "//testcase[@name='testThree']";
        XMLSeries series = new XMLSeries(TEST_XML_FILE, xpath, "NODE", null);

        // test the basic subclass properties.
        testSeries(series, TEST_XML_FILE, "", "xml");

        // load the series.
        List<PlotPoint> points = series.loadSeries(workspaceRootDir, 0, System.out);
        assertNotNull(points);
        assertEquals(1, points.size());
        assertEquals(27d, Double.parseDouble(points.get(0).getYvalue()), 0);
        testPlotPoints(points, 1);
    }

    @Test
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

    @Test
    public void testXMLSeriesBoolean() {
        // Create a new XML series.
        String xpath = "//testcase[@name='testOne']";
        XMLSeries series = new XMLSeries(TEST_XML_FILE, xpath, "BOOLEAN", null);

        // test the basic subclass properties.
        testSeries(series, TEST_XML_FILE, "", "xml");

        // load the series.
        List<PlotPoint> points = series.loadSeries(workspaceRootDir, 0, System.out);
        assertNotNull(points);
        testPlotPoints(points, 1);
    }

    @Test
    public void testXMLSeriesNumber() {
        // Create a new XML series.
        String xpath = "concat(//testcase[@name='testOne']/@name, '=', //testcase[@name='testOne']/@time)";
        xpath = "//testcase[@name='testOne']/@time";
        XMLSeries series = new XMLSeries(TEST_XML_FILE, xpath, "NUMBER", "splunge");

        // test the basic subclass properties.
        testSeries(series, TEST_XML_FILE, "", "xml");

        // load the series.
        List<PlotPoint> points = series.loadSeries(workspaceRootDir, 0, System.out);
        assertNotNull(points);
        testPlotPoints(points, 1);
    }

    @Test
    public void testXMLSeriesUrl() {
        // Create a new XML series.
        String xpath = "/results/testcase/*";

        XMLSeries series = new XMLSeries(TEST3_XML_FILE, xpath, "NODESET", "http://localhost/%build%/%name%/%index%");

        // test the basic subclass properties.
        testSeries(series, TEST3_XML_FILE, "", "xml");

        // load the series.
        List<PlotPoint> points = series.loadSeries(workspaceRootDir, 42, System.out);
        assertNotNull(points);
        testPlotPoints(points, 2);
        assertEquals("http://localhost/42/one/0", points.get(0).getUrl());
        assertEquals("http://localhost/42/two/0", points.get(1).getUrl());
    }

    @Test
    public void testXMLSeries_failToReadExternalDTD() throws UnsupportedEncodingException {
        // Create a new XML series with test file
        String xpathString = "/results/testcase/*";
        XMLSeries series = new XMLSeries(TEST4_XML_FILE, xpathString, "NODESET", null);
        testSeries(series, TEST4_XML_FILE, "", "xml");

        // we want to examin the logoutput
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final String utf8 = StandardCharsets.UTF_8.name();
        PrintStream customOutput = new PrintStream(baos, true, utf8);

        // load the series to see if we have the expected behavior.
        List<PlotPoint> points = series.loadSeries(workspaceRootDir, 0, customOutput);

        assertNull(points);

        String expectedOutput =
                "DOCTYPE is disallowed when the feature \"http://apache.org/xml/features/disallow-doctype-decl\" set to true";
        String customOutputAsString = baos.toString();
        assertNotNull(customOutputAsString);
        // depending on the "JRE" (?) the custom output is terminated or not by a \n
        assertThat(customOutputAsString, containsString(expectedOutput));
    }
}
