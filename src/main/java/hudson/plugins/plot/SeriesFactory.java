/*
 * Copyright (c) 2008-2009 Yahoo! Inc.  All rights reserved.
 * The copyrights to the contents of this file are licensed under the MIT License (http://www.opensource.org/licenses/mit-license.php)
 */

package hudson.plugins.plot;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

/**
 * This class creates a Series class based on the data source
 * 
 * @author areese, Alan.Harder@sun.com
 */
public class SeriesFactory {
    //private static final Logger LOGGER = Logger.getLogger(SeriesFactory.class.getName());

    /**
     * Using file and label and the Stapler request, create a subclass of series that can process the type selected.
     * @param formData JSON data for series
     */
    public static Series createSeries(JSONObject formData, StaplerRequest req) {
        String file = formData.getString("file");
        formData = formData.getJSONObject("fileType");
        formData.put("file", file);
        String type = formData.getString("value");
        Class<? extends Series> typeClass = null;

        if ("properties".equals(type)) typeClass = PropertiesSeries.class;
        else if ("csv".equals(type))   typeClass = CSVSeries.class;
        else if ("xml".equals(type))   typeClass = XMLSeries.class;

        return typeClass!=null ? req.bindJSON(typeClass, formData) : null;
    }

    public static Series[] createSeriesList(Object data, StaplerRequest req) {
        JSONArray list = getArray(data);
        Series[] result = new Series[list.size()];
        int i = 0;
        for (Object series : list) {
            result[i++] = createSeries((JSONObject)series, req);
        }
        return result;
    }

    /**
     * Get data as JSONArray (wrap single JSONObject in array if needed).
     */
    public static JSONArray getArray(Object data) {
        JSONArray result;
        if (data instanceof JSONArray) result = (JSONArray)data;
        else {
            result = new JSONArray();
            if (data != null) result.add(data);
        }
        return result;
    }
};
