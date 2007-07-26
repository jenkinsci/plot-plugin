/*
 * Copyright (c) 2007 Yahoo! Inc.  All rights reserved.  
 * Copyrights licensed under the MIT License.
 */
package hudson.plugins.plot;

import hudson.Plugin;
import hudson.tasks.BuildStep;

/**
 * @author Nigel Daley
 * @plugin
 */
public class PluginImpl extends Plugin {
    public void start() throws Exception {
        BuildStep.PUBLISHERS.addRecorder(PlotPublisher.DESCRIPTOR);
    }
}
