/*
 * Copyright (c) 2007 Yahoo! Inc.  All rights reserved.  
 * Copyrights licensed under the MIT License.
 */
package hudson.plugins.plot;

import org.jfree.chart.urls.CategoryURLGenerator;
import org.jfree.data.category.CategoryDataset;

/**
 * Returns the URL for a given data point.
 *
 * @author Nigel Daley
 */
public class PointURLGenerator implements CategoryURLGenerator {

    /**
     * Retrieves a URL from the given dataset for a particular 
     * item within a series.  If the given dataset isn't a 
     * PlotCategoryDataset, then null is returned.
     *
     * @param dataset the dataset
     * @param series the series index (zero-based)
     * @param category the category index (zero-based)
     *
     * @return the generated URL
     */
    public String generateURL(CategoryDataset dataset, int series, 
                              int category) {
    	if (dataset instanceof PlotCategoryDataset) {
    		return ((PlotCategoryDataset)dataset).getUrl(series,category);
    	} else {
    		return null;
    	}
    }    
}