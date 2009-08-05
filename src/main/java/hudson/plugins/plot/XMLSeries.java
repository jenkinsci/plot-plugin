/*
 * Copyright (c) 2008-2009 Yahoo! Inc.  All rights reserved.
 * The copyrights to the contents of this file are licensed under the MIT License (http://www.opensource.org/licenses/mit-license.php)
 */


package hudson.plugins.plot;

import hudson.FilePath;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

import org.kohsuke.stapler.StaplerRequest;
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
    private static transient final Logger LOGGER = Logger.getLogger(Series.class.getName());
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
		tempMap.put("BOOLEAN", XPathConstants.NODE);
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
	private QName nodeType;

	/**
	 * 
	 * @param file
	 * @param label
	 * @param req Stapler request
	 * @param radioButtonId ID used to find the parameters specific to this instance.
	 * @throws ServletException
	 */
	public XMLSeries(String file, String label, StaplerRequest req, String radioButtonId) throws ServletException {
    	super(file, label, "xml");

		String[] temp;

    	if (LOGGER.isLoggable(defaultLogLevel))
    		LOGGER.log(defaultLogLevel,"RadioButtonID " + radioButtonId);

		temp = req.getParameterValues(radioButtonId+".url");
		if (temp != null && temp.length > 0)
			url = temp[0];

    	if (LOGGER.isLoggable(defaultLogLevel))
    		LOGGER.log(defaultLogLevel,"url " + url);

    	temp = req.getParameterValues(radioButtonId+".xpath");
		if (temp != null && temp.length > 0)
			xpathString = temp[0];

    	if (LOGGER.isLoggable(defaultLogLevel))
    		LOGGER.log(defaultLogLevel,"XPath " + xpathString);

    	temp = req.getParameterValues(radioButtonId+".nodeType");
		if (temp != null && temp.length > 0) {
			nodeTypeString = temp[0].toUpperCase();
	    	nodeType = qnameMap.get(nodeTypeString);
		}

    	if (LOGGER.isLoggable(defaultLogLevel))
    		LOGGER.log(defaultLogLevel,"NodeType " + nodeTypeString);
	}

    /**
     * This is used for saving state of radio buttons in subclasses.
     * BUG: This is sucky find a better way to do this.
     * @param test
     * @return
     */
	@Override
    public boolean test(String test)
    {
		if (test == null || nodeTypeString == null || "".equals(nodeTypeString))
			return false;
		
		test = test.toUpperCase();
		
    	if (LOGGER.isLoggable(defaultLogLevel))
    		LOGGER.log(defaultLogLevel,"testing NodeType " + nodeTypeString + " vs " + test);

    	if (qnameMap.containsKey(test) && nodeTypeString.equals(test))
    	{
        	if (LOGGER.isLoggable(defaultLogLevel))
        		LOGGER.log(defaultLogLevel,"true NodeType " + nodeTypeString + " vs " + test);
			return true;
    	}
		
		return false;
    }

    /**
     * There has to be a cleaner way of doing this, such as casting Series to CSVSeries in the jelly.
     * @return ""
     */
	@Override
    public String getValue(String name)
    {
		if ("xpath".equals(name))
		{
    		LOGGER.log(defaultLogLevel,"xpath: " + xpathString);
			return xpathString;
		}
		
		if ("nodetype".equals(name))
		{
    		LOGGER.log(defaultLogLevel,"nodetype: " + nodeTypeString);
			return nodeTypeString;
		}
		
		if ("url".equals(name))
		{
    		LOGGER.log(defaultLogLevel,"url: " + url);
			return url;
		}

		LOGGER.log(defaultLogLevel,"empty: " + name);
		return "";
    }

    /**
     * Load the series from a properties file.
     */
	@Override
	public PlotPoint[] loadSeries(FilePath workspaceRootDir, PrintStream logger) {
		InputStream in = null;
		InputSource inputSource = null;
		
        try {
			List<PlotPoint> ret = new ArrayList<PlotPoint>();
	
			FilePath[] seriesFiles = null;
	        try {
	            seriesFiles = workspaceRootDir.list(getFile());
	        } catch (Exception e) {
	        	LOGGER.warning("Exception trying to retrieve series files: " + e);
	            return null;
	        }
	
	        if (seriesFiles != null && seriesFiles.length < 1) {
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
				LOGGER.warning("Exception reading plot series data from: " + seriesFiles[0] + " " + e);
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
    			NodeList nl=(NodeList)xmlObject;
            	if (LOGGER.isLoggable(defaultLogLevel))
            		LOGGER.log(defaultLogLevel,"Number of nodes: " + nl.getLength());
    			
    			for (int i = 0; i < nl.getLength(); i++) {
					Node n = nl.item(i);
					if (n != null && n.getLocalName() != null && n.getTextContent() != null) {
						addValueToList(ret, n.getLocalName().trim(), n.getTextContent().trim());
					}
				}
    		} else {
    			// otherwise we have a single type and can do a toString on it.
				addValueToList(ret,label, xmlObject);
    		}
        
            return ret.toArray(new PlotPoint[ret.size()]);

        } catch (XPathExpressionException e) {
            //ignore
        	if (LOGGER.isLoggable(defaultLogLevel))
        		LOGGER.log(defaultLogLevel,"Exception: " + e);
		} finally {
        	if (in != null) {
                try {
                    in.close();
                } catch (IOException ignore) {
                    //ignore
                }
        	}
        }

        return null;
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
		
		if (nodeType==XPathConstants.NODE)
			ret = ((Node)obj).toString().trim();

		if (nodeType==XPathConstants.STRING || nodeType==XPathConstants.NODESET)
			ret = ((String)obj).trim();
		
		// for Node/String/NodeSet, try and parse it as a double.
		// we don't store a double, so just throw away the result.
		if (ret != null)
		{
			try {
				Double.parseDouble(ret);
				return ret;
			} catch (NumberFormatException ignore) {
			}
		}
		
		return null;
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
		String value = null;

		value = nodeToString(nodeValue);
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
