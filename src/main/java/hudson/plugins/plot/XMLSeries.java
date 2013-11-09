/*
 * Copyright (c) 2008-2009 Yahoo! Inc.  All rights reserved.
 * The copyrights to the contents of this file are licensed under the MIT License (http://www.opensource.org/licenses/mit-license.php)
 */

package hudson.plugins.plot;

import hudson.FilePath;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.xml.namespace.QName;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ArrayUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Represents a plot data series configuration from an XML file.
 *
 * @author Allen Reese
 *
 */
public class XMLSeries extends Series {
    private static transient final Logger LOGGER = Logger.getLogger(XMLSeries.class.getName());
    // Debugging hack, so I don't have to change FINE/INFO...
    private static transient final Level defaultLogLevel = Level.INFO;
	private static transient final Pattern PAT_NAME = Pattern.compile("%name%");
	private static transient final Pattern PAT_INDEX = Pattern.compile("%index%");

	private static transient final Map<String,QName> qnameMap;

	/**
	 * Fill out the qname map for easy reference.
	 */
	static {
		HashMap<String, QName> tempMap = new HashMap<String, QName>();
		tempMap.put("BOOLEAN", XPathConstants.BOOLEAN);
		tempMap.put("NODE", XPathConstants.NODE);
		tempMap.put("NODESET", XPathConstants.NODESET);
		tempMap.put("NUMBER", XPathConstants.NUMBER);
		tempMap.put("STRING", XPathConstants.STRING);
		qnameMap = Collections.unmodifiableMap(tempMap);
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

	/**
	 *
	 * @param file
	 * @param label
	 * @param req Stapler request
	 * @param radioButtonId ID used to find the parameters specific to this instance.
	 * @throws ServletException
	 */
    @DataBoundConstructor
    public XMLSeries(String file, String xpath, String nodeType, String url) {
    	super(file, "", "xml");

    	this.xpathString = xpath;
    	this.nodeTypeString = nodeType;
    	this.nodeType = qnameMap.get(nodeType);
    	this.url = url;
    }

    private Object readResolve() {
        // Set nodeType when deserialized
        nodeType = qnameMap.get(nodeTypeString);
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
     * Load the series from a properties file.
     */
	@Override
    public List<PlotPoint> loadSeries(FilePath workspaceRootDir, PrintStream logger) {
		InputStream in = null;
		InputSource inputSource = null;

        try {
			List<PlotPoint> ret = new ArrayList<PlotPoint>();
			FilePath[] seriesFiles = null;

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
            	if (LOGGER.isLoggable(defaultLogLevel))
	        		LOGGER.log(defaultLogLevel,"Loading plot series data from: " + getFile());

	            in = seriesFiles[0].read();
		        // load existing plot file
	        	inputSource = new InputSource(in);
	        } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Exception reading plot series data from " + seriesFiles[0], e);
				return null;
			}

	    	if (LOGGER.isLoggable(defaultLogLevel))
	    		LOGGER.log(defaultLogLevel,"NodeType " + nodeTypeString + " : " + nodeType);

	    	if (LOGGER.isLoggable(defaultLogLevel))
        		LOGGER.log(defaultLogLevel,"Loaded XML Plot file: " + getFile());

    		XPath xpath = XPathFactory.newInstance().newXPath();
    		Object xmlObject = xpath.evaluate(xpathString, inputSource, nodeType);

    		/*
    		 * If we have a nodeset, we need multiples, otherwise we just need one value, and can do a toString()
    		 * to set it.
    		 */
    		if (nodeType.equals(XPathConstants.NODESET))
    		{
    			NodeList nl = (NodeList) xmlObject;

            	if (LOGGER.isLoggable(defaultLogLevel))
            		LOGGER.log(defaultLogLevel,"Number of nodes: " + nl.getLength());

            	Map<Node, List<Node>> parentNodeMap = new HashMap<Node, List<Node>>();

            	for (int i = 0; i < nl.getLength(); i++) {
    				Node node = nl.item(i);
    				Node parent = node.getParentNode();
    				List<Node>  nodeList = parentNodeMap.get(parent);

    				// TODO:  Temp debug code:
    				Object obj = nl.item(i);
    				Object obj2 = node.getFirstChild();

    				// Make sure these nodes aren't all from the parent node:
    				if (node.getFirstChild() != null) {
    					if (nodeList == null) {
    						nodeList = new LinkedList<Node>();
    						parentNodeMap.put(parent, nodeList);
    					}

    					nodeList.add(node);
    				}
            	}

            	int numParents = parentNodeMap.size();
            	int numNodes = nl.getLength();

            	// If we found nodes with common parents, combine them:
            	if ((parentNodeMap.size() != 0) && (parentNodeMap.size() < nl.getLength())) {
            		Set<Map.Entry<Node, List<Node>>> entries = parentNodeMap.entrySet();

            		// TODO:  Put in logging messages about what is being processed.

            		// If there are multiple names and multiple values, we'll always use the
            		// latest one.
            		for (Map.Entry<Node, List<Node>> entry : entries) {
            			String name = null;
            			String value = null;
            		    List<Node> nodeList = entry.getValue();

            		    for (Node node : nodeList) {
            		    	try {
            		    		Double.parseDouble(node.getTextContent());
            		    		value = node.getTextContent().trim();
            		    	} catch (NumberFormatException nfe) {
                                LOGGER.log(Level.SEVERE, "Exception converting to number", nfe);
            		    		name = node.getTextContent().trim();
            		    	}
            		    }
            		    addValueToList(ret, name, value);
            		}

            	} else {
            		for (int i = 0; i < nl.getLength(); i++) {
            			Node n = nl.item(i);

            			if (n != null && n.getLocalName() != null && n.getTextContent() != null) {
            				addNodeToList(ret, n);
            			}
            		}
            	}
    		} else if (nodeType.equals(XPathConstants.NODE)) {
    			addNodeToList(ret, (Node) xmlObject);
    		} else {
    			// otherwise we have a single type and can do a toString on it.
    			if (xmlObject instanceof NodeList) {
    				NodeList nl = (NodeList) xmlObject;

                	if (LOGGER.isLoggable(defaultLogLevel))
                		LOGGER.log(defaultLogLevel,"Number of nodes: " + nl.getLength());

        			for (int i = 0; i < nl.getLength(); i++) {
    					Node n = nl.item(i);

    					if (n != null && n.getLocalName() != null && n.getTextContent() != null) {
    						addValueToList(ret, label, xmlObject);
    					}
    				}

    			} else {
				    addValueToList(ret, label, xmlObject);
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

	private void addNodeToList(List<PlotPoint> ret, Node n) {
		NamedNodeMap nodeMap = n.getAttributes();

		if ((null != nodeMap) && (null != nodeMap.getNamedItem("name"))) {
			addValueToList(ret, nodeMap.getNamedItem("name").getTextContent().trim(), n);
		} else {
			addValueToList(ret, n.getLocalName().trim(), n);
		}
	}


	/**
	 * Return the url that should be used for this point.
	 * @param label Name of the column
	 * @param index Index of the column
	 * @return url for the label.
	 */
	private String getUrl(String label,int index)
	{
		/*
		 * Check the name first, and do replacement upon it.
		 */
		Matcher nameMatcher = PAT_NAME.matcher(label);
		if (nameMatcher.find())
		{
			url = nameMatcher.replaceAll(label);
		}

		/*
		 * Check the index, and do replacement on it.
		 */
		Matcher indexMatcher = PAT_INDEX.matcher(label);
		if (indexMatcher.find())
		{
			url = indexMatcher.replaceAll(label);
		}

		return url;
	}

	/**
	 * Convert a given object into a String.
	 * @param obj Xpath Object
	 * @return String representation of the node
	 */
	private String nodeToString(Object obj)
	{
		String ret = null;

		if (nodeType==XPathConstants.BOOLEAN)
		{
			return (((Boolean)obj))?"1":"0";
		}

		if (nodeType==XPathConstants.NUMBER)
			return ((Double)obj).toString().trim();

		if (nodeType==XPathConstants.NODE || nodeType==XPathConstants.NODESET) {
			if (obj instanceof String) {
				ret = ((String)obj).trim();
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

		if (nodeType==XPathConstants.STRING)
			ret = ((String)obj).trim();

		// for Node/String/NodeSet, try and parse it as a double.
		// we don't store a double, so just throw away the result.
		if (ret != null)
		{
			// First remove any commas that may exist in the String.  JUnit formats them that way, but
			// Double won't parse them.
			ret = removeCommas(ret);

			try {
				Double.parseDouble(ret);
			} catch (NumberFormatException ignore) {
                LOGGER.log(Level.SEVERE, "Exception converting to number", ignore);
			}
			return ret;
		}

		return null;
	}

	/**
	 * Parsing a double doesn't handle commas, but JUnit formats test execution times with commas for
	 * easy viewing by humans.  We need to take them out, and make sure the string is shorter so we
	 * don't get weird non-printable characters at the end.
	 * @param ret
	 * @return
	 */
	private String removeCommas(String ret) {
		int length = ret.length();
        StringBuilder r = new StringBuilder(length);
		r.setLength(length);
		int current = 0;

		for (int i = 0; i < ret.length(); i ++) {
			char cur = ret.charAt(i);
			if (cur != ',') {
				r.setCharAt( current++, cur );
			} else {
				length--;
				r.setLength(length);
			}
		}
		ret = r.toString().trim();
		return ret;
	}

	/**
	 * Add a given value to the list of results.
	 * This encapsulates some otherwise duplicate logic due to nodeset/!nodeset
	 * @param list
	 * @param label
	 * @param nodeValue
	 */
	private void addValueToList(List<PlotPoint> list, String label, Object nodeValue)
	{
		String value = nodeToString(nodeValue);

		if (value != null) {
			if (LOGGER.isLoggable(defaultLogLevel))
				LOGGER.log(defaultLogLevel, "Adding node: " + label + " value: " + value);
			list.add(new PlotPoint(value, getUrl(label,0), label));
		} else {
			if (LOGGER.isLoggable(defaultLogLevel))
				LOGGER.log(defaultLogLevel, "Unable to add node: " + label + " value: " + nodeValue);
		}
	}
}
