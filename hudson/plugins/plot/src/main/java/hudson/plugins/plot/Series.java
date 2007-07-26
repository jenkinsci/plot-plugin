/*
 * Copyright (c) 2007 Yahoo! Inc.  All rights reserved.  
 * Copyrights licensed under the MIT License.
 */
package hudson.plugins.plot;

/**
 * Represents a plot data series configuration.
 *
 * @author Nigel Daley
 */
public class Series {
    /**
     * Relative path to the data series property file. Mandatory.
     */
    public String file;

    /**
     * Data series legend label. Optional.
     */
    public String label;

    /**
     * @stapler-constructor
     */
    public Series(String file, String label) {
        this.file = file;
        this.label = label;
    }

    public Series() {}
    
    public String getFile() {
        return file;
    }
    public String getLabel() {
        return label;
    }
}

