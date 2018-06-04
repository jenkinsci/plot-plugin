package hudson.plugins.plot;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;


public class PlotBuilderTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void testWithMinimalPipelineArgs() throws Exception {
        WorkflowJob p = r.createProject(WorkflowJob.class, "projectUnderTest");
        p.setDefinition(new CpsFlowDefinition(
                "node {  \n"
                        + "    plot csvFileName: 'plot-minimal.csv',\n"
                        + "       group: 'My Data',\n"
                        + "       style: 'line'\n"
                        + "}",
                true));
        r.buildAndAssertSuccess(p);
    }

    @Test
    public void testWithXML() throws Exception {
        WorkflowJob p = r.createProject(WorkflowJob.class, "projectUnderTest");
        p.setDefinition(new CpsFlowDefinition(
                "node {  \n"
                        + "    sh '''\n"
                        + "      echo '<my_data>' > data.xml\n"
                        + "      echo '    <test value1=\"123.456\"/>' >> data.xml\n"
                        + "      echo '    <test value2=\"321.654\"/>' >> data.xml\n"
                        + "      echo '</my_data>' >> data.xml\n"
                        + "    '''\n"
                        + "    plot csvFileName: 'plot-xml.csv',\n"
                        + "       group: 'My Data',\n"
                        + "       title: 'Useful Title',\n"
                        + "       style: 'line',\n"
                        + "       yaxis: 'arbitrary',\n"
                        + "       xmlSeries: [\n"
                        + "         [file: 'data.xml',\n"
                        + "          nodeType: 'NODESET',\n"
                        + "          xpath: 'my_data/test/@*']\n"
                        + "       ]\n"
                        + "}",
                true));
        r.buildAndAssertSuccess(p);
    }

    @Test
    public void testWithCSV() throws Exception {
        WorkflowJob p = r.createProject(WorkflowJob.class, "projectUnderTest");
        p.setDefinition(new CpsFlowDefinition(
                "node {  \n"
                        + "    sh '''\n"
                        + "      echo 'Avg,Median,90,min,max,samples,errors,error %' > data.csv\n"
                        + "      echo '515.33,196,1117,2,16550,97560,360,0.37' >> data.csv\n"
                        + "    '''\n"
                        + "    plot csvFileName: 'plot-csv.csv',\n"
                        + "       group: 'My Data',\n"
                        + "       title: 'Useful Title',\n"
                        + "       style: 'line',\n"
                        + "       yaxis: 'arbitrary',\n"
                        + "       csvSeries: [[file: 'data.csv']]\n"
                        + "}",
                true));
        r.buildAndAssertSuccess(p);
    }

    @Test
    public void testWithProperties() throws Exception {
        WorkflowJob p = r.createProject(WorkflowJob.class, "projectUnderTest");
        p.setDefinition(new CpsFlowDefinition(
                "node {  \n"
                        + "    sh '''\n"
                        + "      echo 'YVALUE=1' > data.properties\n"
                        + "    '''\n"
                        + "    plot csvFileName: 'plot-properties.csv',\n"
                        + "       group: 'My Data',\n"
                        + "       title: 'Useful Title',\n"
                        + "       style: 'line',\n"
                        + "       yaxis: 'arbitrary',\n"
                        + "       propertiesSeries: [\n"
                        + "         [file: 'data.properties',\n"
                        + "          label: 'My Label']\n"
                        + "       ]\n"
                        + "}",
                true));
        r.buildAndAssertSuccess(p);
    }
}
