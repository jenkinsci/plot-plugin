/*
 * The MIT License
 *
 * Copyright (c) 2014 Red Hat, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.plugins.plot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.Launcher;
import hudson.matrix.AxisList;
import hudson.matrix.MatrixConfiguration;
import hudson.matrix.MatrixProject;
import hudson.matrix.TextAxis;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.FreeStyleProject;
import hudson.tasks.Builder;
import hudson.tasks.LogRotator;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class PlotTest {

    @Test
    void discardPlotSamplesForOldBuilds(JenkinsRule j) throws Exception {
        FreeStyleProject p = jobArchivingBuilds(j, 1);

        plotBuilds(p, "2", false);

        j.buildAndAssertSuccess(p);
        assertSampleCount(p, 1);

        j.buildAndAssertSuccess(p);
        assertSampleCount(p, 1); // Truncated to 1

        j.buildAndAssertSuccess(p);
        assertSampleCount(p, 1); // Still 1
    }

    @Test
    void discardPlotSamplesForDeletedBuilds(JenkinsRule j) throws Exception {
        FreeStyleProject p = jobArchivingBuilds(j, 10);

        plotBuilds(p, "", false);

        j.buildAndAssertSuccess(p);
        assertSampleCount(p, 1);

        j.buildAndAssertSuccess(p);
        assertSampleCount(p, 2);

        j.buildAndAssertSuccess(p);
        assertSampleCount(p, 3);

        p.getLastBuild().delete();
        assertSampleCount(p, 2); // Data should be removed with the build
    }

    @Test
    void keepPlotSamplesForOldBuilds(JenkinsRule j) throws Exception {
        FreeStyleProject p = jobArchivingBuilds(j, 1);

        plotBuilds(p, "2", true);

        j.buildAndAssertSuccess(p);
        assertSampleCount(p, 1);

        j.buildAndAssertSuccess(p);
        assertSampleCount(p, 2);

        j.buildAndAssertSuccess(p);
        assertSampleCount(p, 2); // Plot 2 builds

        j.buildAndAssertSuccess(p);
        assertSampleCount(p, 2); // Still 2
    }

    @Test
    void keepPlotSamplesForDeletedBuilds(JenkinsRule j) throws Exception {
        FreeStyleProject p = jobArchivingBuilds(j, 10);

        plotBuilds(p, "", true);

        j.buildAndAssertSuccess(p);
        assertSampleCount(p, 1);

        j.buildAndAssertSuccess(p);
        assertSampleCount(p, 2);

        j.buildAndAssertSuccess(p);
        assertSampleCount(p, 3);

        p.getLastBuild().delete();
        assertSampleCount(p, 3); // Data should be kept
    }

    @Test
    void discardPlotSamplesForDeletedMatrixBuilds(JenkinsRule j) throws Exception {
        MatrixProject p = matrixJobArchivingBuilds(j, 10);
        p.setAxes(new AxisList(new TextAxis("a", "a")));

        MatrixConfiguration c = p.getItem("a=a");

        plotMatrixBuilds(p, "10", false);

        j.buildAndAssertSuccess(p);
        assertSampleCount(c, 1);

        j.buildAndAssertSuccess(p);
        assertSampleCount(c, 2);

        j.buildAndAssertSuccess(p);
        assertSampleCount(c, 3);

        c.getLastBuild().delete();
        assertSampleCount(c, 2); // Data should be removed
    }

    @Test
    void keepPlotSamplesForDeletedMatrixBuilds(JenkinsRule j) throws Exception {
        MatrixProject p = matrixJobArchivingBuilds(j, 10);
        p.setAxes(new AxisList(new TextAxis("a", "a")));

        MatrixConfiguration c = p.getItem("a=a");

        plotMatrixBuilds(p, "10", true);

        j.buildAndAssertSuccess(p);
        assertSampleCount(c, 1);

        j.buildAndAssertSuccess(p);
        assertSampleCount(c, 2);

        j.buildAndAssertSuccess(p);
        assertSampleCount(c, 3);

        c.getLastBuild().delete();
        assertSampleCount(c, 3); // Data should be kept
        p.getLastBuild().delete();
        assertSampleCount(c, 3); // Data should be kept
    }

    private static FreeStyleProject jobArchivingBuilds(JenkinsRule j, int count) throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(new PlotBuildNumber());
        p.setBuildDiscarder(new LogRotator(-1, count, -1, -1));

        return p;
    }

    private static MatrixProject matrixJobArchivingBuilds(JenkinsRule j, int count) throws Exception {
        MatrixProject p = j.createProject(MatrixProject.class);

        p.getBuildersList().add(new PlotBuildNumber());
        p.setBuildDiscarder(new LogRotator(-1, count, -1, -1));

        return p;
    }

    private static void plotBuilds(AbstractProject<?, ?> p, String count, boolean keepRecords) {
        final PlotPublisher publisher = new PlotPublisher();
        final Plot plot = new Plot(
                "Title", "Number", "default", count, null, "line", false, keepRecords, false, false, null, null, null);
        p.getPublishersList().add(publisher);
        publisher.addPlot(plot);
        plot.series = List.of(new PropertiesSeries("src.properties", null));
    }

    private static void plotMatrixBuilds(AbstractProject<?, ?> p, String count, boolean keepRecords) {
        final MatrixPlotPublisher publisher = new MatrixPlotPublisher();
        final Plot plot = new Plot(
                "Title", "Number", "default", count, null, "line", false, keepRecords, false, false, null, null, null);
        p.getPublishersList().add(publisher);
        publisher.setPlots(List.of(plot));
        plot.series = List.of(new PropertiesSeries("src.properties", null));
    }

    private static void assertSampleCount(AbstractProject<?, ?> p, int count) throws Exception {
        PlotReport pr = p instanceof MatrixConfiguration
                ? p.getAction(MatrixPlotAction.class).getDynamic("default")
                : p.getAction(PlotAction.class).getDynamic("default", null, null);
        List<List<String>> table = pr.getTable(0);
        assertEquals(count, table.size() - 1, "Plot sample count");
    }

    private static final class PlotBuildNumber extends Builder {

        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
                throws InterruptedException, IOException {
            build.getWorkspace().child("src.properties").write(String.format("YVALUE=%d", build.getNumber()), "UTF-8");
            return true;
        }
    }
}
