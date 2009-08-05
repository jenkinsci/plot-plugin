/*
 * Copyright (c) 2008-2009 Yahoo! Inc.  All rights reserved.
 * The copyrights to the contents of this file are licensed under the MIT License (http://www.opensource.org/licenses/mit-license.php)
 */

package hudson.plugins.plot;

public class PlotPoint
{
	/**
	 * YValue for a plot point
	 */
	private String yvalue;
	
	/**
	 * Url for a plot point, can be null
	 */
	private String url;

	/**
	 * Label for a plot point
	 */
	private String label;

	/**
	 */
	public PlotPoint(String yvalue, String url, String label)
	{
		this.yvalue = yvalue;
		
		if (url==null)
			url="";
		
		this.url = url;
		this.label = label;
	}

	/**
	 * @return the yvalue for this point. 
	 */
	public String getYvalue()
	{
		return yvalue;
	}

	/**
	 * @param yvalue set the yvalue for this point.
	 */
	public void setYvalue(String yvalue)
	{
		this.yvalue = yvalue;
	}

	/**
	 * @return url for this point.
	 */
	public String getUrl()
	{
		return url;
	}

	/**
	 * @param url set the url for this point.
	 */
	public void setUrl(String url)
	{
		this.url = url;
	} 

	/**
	 * @return label for this point.
	 */
	public String getLabel()
	{
		return label;
	}

	/**
	 * @param label set the label for this point.
	 */
	public void setLabel(String label)
	{
		this.label = label;
	}

	@Override
	public String toString() {
		return label + " " + url + " " + yvalue;
	} 
}
