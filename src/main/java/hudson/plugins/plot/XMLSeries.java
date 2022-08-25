/*
 * Copyright (c) 2008-2009 Yahoo! Inc.  All rights reserved.
 * The copyrights to the contents of this file are licensed under the MIT License
 * (http://www.opensource.org/licenses/mit-license.php)
 */
package hudson.plugins.plot;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Descriptor;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.namespace.QName;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ArrayUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Represents a plot data series configuration from an XML file.
 *
 * @author Allen Reese
 */
public class XMLSeries extends Series {
    private static final transient Logger LOGGER = Logger.getLogger(XMLSeries.class.getName());
    // Debugging hack, so I don't have to change FINE/INFO...
    private static final transient Level DEFAULT_LOG_LEVEL = Level.INFO;

    private static final transient Map<String, QName> Q_NAME_MAP;

    /*
      Fill out the qName map for easy reference.
     */
    static {
        HashMap<String, QName> tempMap = new HashMap<String, QName>();
        tempMap.put("BOOLEAN", XPathConstants.BOOLEAN);
        tempMap.put("NODE", XPathConstants.NODE);
        tempMap.put("NODESET", XPathConstants.NODESET);
        tempMap.put("NUMBER", XPathConstants.NUMBER);
        tempMap.put("STRING", XPathConstants.STRING);
        Q_NAME_MAP = Collections.unmodifiableMap(tempMap);
    }

    /**
     * XPath to select for values
     */
    private String xpathString;

    /**
     * Url to use as a base for mapping points.
     */
    private String url;

    /**
     * String of the qname type to use
     */
    private String nodeTypeString;

    /**
     * Actual nodeType
     */
    private transient QName nodeType;

    @DataBoundConstructor
    public XMLSeries(String file, String xpath, String nodeType, String url) {
        super(file, "", "xml");

        this.xpathString = xpath;
        this.nodeTypeString = nodeType;
        this.nodeType = Q_NAME_MAP.get(nodeType);
        this.url = url;
    }

    private Object readResolve() {
        // Set nodeType when deserialized
        nodeType = Q_NAME_MAP.get(nodeTypeString);
        return this;
    }

    public String getXpath() {
        return xpathString;
    }

    public String getNodeType() {
        return nodeTypeString;
    }

    public String getUrl() {
        return url;
    }

    /**
     * @param buildNumber the build number
     * @return a List of PlotPoints where the label is the element name and the
     * value is the node content.
     */
    private List<PlotPoint> mapNodeNameAsLabelTextContentAsValueStrategy(NodeList nodeList,
                                                                         int buildNumber) {
        List<PlotPoint> retval = new ArrayList<>();
        for (int i = 0; i < nodeList.getLength(); i++) {
            this.addNodeToList(retval, nodeList.item(i), buildNumber);
        }
        return retval;
    }

    /**
     * This is a fallback strategy for nodesets that include non numeric content
     * enabling users to create lists by selecting them such that names and
     * values share a common parent. If a node has attributes and is empty that
     * node will be re-enqueued as a parent to its attributes.
     *
     * @param buildNumber the build number
     * @return a list of PlotPoints where the label is the last non numeric
     * text content and the value is the last numeric text content for
     * each set of nodes under a given parent.
     */
    private List<PlotPoint> coalesceTextnodesAsLabelsStrategy(NodeList nodeList, int buildNumber) {
        Map<Node, List<Node>> parentNodeMap = new HashMap<>();

        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            if (!parentNodeMap.containsKey(node.getParentNode())) {
                parentNodeMap.put(node.getParentNode(), new ArrayList<Node>());
            }
            parentNodeMap.get(node.getParentNode()).add(node);
        }

        List<PlotPoint> retval = new ArrayList<>();
        Queue<Node> parents = new ArrayDeque<>(parentNodeMap.keySet());
        while (!parents.isEmpty()) {
            Node parent = parents.poll();
            Double value = null;
            String label = null;

            for (Node child : parentNodeMap.get(parent)) {
                if (null == child.getTextContent() || child.getTextContent().trim().isEmpty()) {
                    NamedNodeMap attrmap = child.getAttributes();
                    List<Node> attrs = new ArrayList<>();
                    for (int i = 0; i < attrmap.getLength(); i++) {
                        attrs.add(attrmap.item(i));
                    }
                    parentNodeMap.put(child, attrs);
                    parents.add(child);
                } else if (new Scanner(child.getTextContent().trim()).hasNextDouble()) {
                    value = new Scanner(child.getTextContent().trim()).nextDouble();
                } else {
                    label = child.getTextContent().trim();
                }
            }
            if ((label != null) && (value != null)) {
                addValueToList(retval, label, String.valueOf(value), buildNumber);
            }
        }
        return retval;
    }

    /**
     * Load the series from a properties file.
     */
    @Override
    public List<PlotPoint> loadSeries(FilePath workspaceRootDir, int buildNumber,
                                      PrintStream logger) {
        InputStream in = null;
        InputSource inputSource;

        try {
            List<PlotPoint> ret = new ArrayList<>();
            FilePath[] seriesFiles;

            try {
                seriesFiles = workspaceRootDir.list(getFile());
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Exception trying to retrieve series files", e);
                return null;
            }

            if (ArrayUtils.isEmpty(seriesFiles)) {
                LOGGER.info("No plot data file found: " + getFile());
                return null;
            }

            try {
                if (LOGGER.isLoggable(DEFAULT_LOG_LEVEL)) {
                    LOGGER.log(DEFAULT_LOG_LEVEL, "Loading plot series data from: " + getFile());
                }

                in = seriesFiles[0].read();
                // load existing plot file
                inputSource = new InputSource(in);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE,
                        "Exception reading plot series data from " + seriesFiles[0], e);
                return null;
            }

            if (LOGGER.isLoggable(DEFAULT_LOG_LEVEL)) {
                LOGGER.log(DEFAULT_LOG_LEVEL, "NodeType " + nodeTypeString + " : " + nodeType);
            }

            if (LOGGER.isLoggable(DEFAULT_LOG_LEVEL)) {
                LOGGER.log(DEFAULT_LOG_LEVEL, "Loaded XML Plot file: " + getFile());
            }

            XPath xpath = XPathFactory.newInstance().newXPath();
            Object xmlObject = xpath.evaluate(xpathString, inputSource, nodeType);

            /*
             * If we have a nodeset, we need multiples, otherwise we just need
             * one value, and can do a toString() to set it.
             */
            if (nodeType.equals(XPathConstants.NODESET)) {
                NodeList nl = (NodeList) xmlObject;
                if (LOGGER.isLoggable(DEFAULT_LOG_LEVEL)) {
                    LOGGER.log(DEFAULT_LOG_LEVEL, "Number of nodes: " + nl.getLength());
                }

                for (int i = 0; i < nl.getLength(); i++) {
                    Node node = nl.item(i);
                    if (!new Scanner(node.getTextContent().trim()).hasNextDouble()) {
                        return coalesceTextnodesAsLabelsStrategy(nl, buildNumber);
                    }
                }
                return mapNodeNameAsLabelTextContentAsValueStrategy(nl, buildNumber);
            } else if (nodeType.equals(XPathConstants.NODE)) {
                addNodeToList(ret, (Node) xmlObject, buildNumber);
            } else {
                // otherwise we have a single type and can do a toString on it.
                if (xmlObject instanceof NodeList) {
                    NodeList nl = (NodeList) xmlObject;

                    if (LOGGER.isLoggable(DEFAULT_LOG_LEVEL)) {
                        LOGGER.log(DEFAULT_LOG_LEVEL, "Number of nodes: " + nl.getLength());
                    }

                    for (int i = 0; i < nl.getLength(); i++) {
                        Node n = nl.item(i);

                        if (n != null && n.getLocalName() != null && n.getTextContent() != null) {
                            addValueToList(ret, label, xmlObject, buildNumber);
                        }
                    }
                } else {
                    addValueToList(ret, label, xmlObject, buildNumber);
                }
            }
            return ret;
        } catch (XPathExpressionException e) {
            LOGGER.log(Level.SEVERE, "XPathExpressionException for XPath '" + getXpath() + "'", e);
        } finally {
            IOUtils.closeQuietly(in);
        }

        return null;
    }

    private void addNodeToList(List<PlotPoint> ret, Node n, int buildNumber) {
        NamedNodeMap nodeMap = n.getAttributes();

        if ((null != nodeMap) && (null != nodeMap.getNamedItem("name"))) {
            addValueToList(ret, nodeMap.getNamedItem("name").getTextContent().trim(),
                    n, buildNumber);
        } else {
            addValueToList(ret, n.getLocalName().trim(), n, buildNumber);
        }
    }

    /**
     * Convert a given object into a String.
     *
     * @param obj Xpath Object
     * @return String representation of the node
     */
    private String nodeToString(Object obj) {
        String ret = null;

        if (nodeType == XPathConstants.BOOLEAN) {
            return (((Boolean) obj)) ? "1" : "0";
        }

        if (nodeType == XPathConstants.NUMBER) {
            return ((Double) obj).toString().trim();
        }

        if (nodeType == XPathConstants.NODE
                || nodeType == XPathConstants.NODESET) {
            if (obj instanceof String) {
                ret = ((String) obj).trim();
            } else {
                if (null == obj) {
                    return null;
                }

                Node node = (Node) obj;
                NamedNodeMap nodeMap = node.getAttributes();

                if ((null != nodeMap) && (null != nodeMap.getNamedItem("time"))) {
                    ret = nodeMap.getNamedItem("time").getTextContent();
                }

                if (null == ret) {
                    ret = node.getTextContent().trim();
                }
            }
        }

        if (nodeType == XPathConstants.STRING) {
            ret = ((String) obj).trim();
        }

        // for Node/String/NodeSet, try and parse it as a double.
        // we don't store a double, so just throw away the result.
        Scanner scanner = new Scanner(ret);
        if (scanner.hasNextDouble()) {
            return String.valueOf(scanner.nextDouble());
        }
        return null;
    }

    /**
     * Add a given value to the list of results. This encapsulates some
     * otherwise duplicate logic due to nodeset/!nodeset
     */
    private void addValueToList(List<PlotPoint> list, String label,
                                Object nodeValue, int buildNumber) {
        String value = nodeToString(nodeValue);

        if (value != null) {
            if (LOGGER.isLoggable(DEFAULT_LOG_LEVEL)) {
                LOGGER.log(DEFAULT_LOG_LEVEL, "Adding node: " + label + " value: " + value);
            }
            list.add(new PlotPoint(value, getUrl(url, label, 0, buildNumber),
                    label));
        } else {
            if (LOGGER.isLoggable(DEFAULT_LOG_LEVEL)) {
                LOGGER.log(DEFAULT_LOG_LEVEL, "Unable to add node: " + label
                        + " value: " + nodeValue);
            }
        }
    }

    @Override
    public Descriptor<Series> getDescriptor() {
        return new DescriptorImpl();
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Series> {
        @NonNull
        public String getDisplayName() {
            return Messages.Plot_XmlSeries();
        }

        @Override
        public Series newInstance(StaplerRequest req, @NonNull JSONObject formData)
                throws FormException {
            return SeriesFactory.createSeries(formData, req);
        }
    }
}
