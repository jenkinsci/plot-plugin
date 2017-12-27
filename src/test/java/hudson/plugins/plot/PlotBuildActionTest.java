package hudson.plugins.plot;

import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;
import com.thoughtworks.xstream.converters.reflection.ReflectionProvider;
import hudson.model.Run;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.fail;

public class PlotBuildActionTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();
    private PlotBuildAction plotBuildAction;

    @Before
    public void setUp() throws Exception {
        final Run<?, ?> run = r.buildAndAssertSuccess(r.createFreeStyleProject());
        final List<Plot> plots = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            Plot p = new Plot();
            p.title = String.valueOf(i);
            plots.add(p);
        }
        plotBuildAction = new PlotBuildAction(run, plots);
    }

    @Issue("JENKINS-48465")
    @Test
    public void checksNoConcurrentModificationExceptionIsThrownForPlotsListAccess()
            throws Exception {
        int tasksCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        List<FutureTask<Object>> tasks = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(tasksCount);

        simulateConcurrentModificationException(executorService, tasksCount, tasks, latch);

        waitForAllThreadsToFinish(executorService, latch);
        assertNoConcurrentModificationExceptionThrown(tasks);
    }

    private void simulateConcurrentModificationException(ExecutorService executorService,
            int tasksCount, List<FutureTask<Object>> tasks, final CountDownLatch latch) {
        for (int i = 0; i < tasksCount; i++) {
            FutureTask<Object> task = new FutureTask<>(new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    try {
                        Thread.sleep(new Random().nextInt(100));
                        // using PureJavaReflectionProvider just because it's used in Jenkins
                        // close to "real world"
                        PureJavaReflectionProvider provider = new PureJavaReflectionProvider();
                        provider.visitSerializableFields(plotBuildAction,
                                new ReflectionProvider.Visitor() {
                                    @Override
                                    public void visit(String fieldName, Class fieldType,
                                            Class definedIn, Object value) {
                                        if (value != null && value instanceof List) {
                                            List<Plot> plots = (List<Plot>) value;
                                            // simulate ConcurrentModificationException
                                            for (Plot p : plots) {
                                                if (plots.size() > 0) {
                                                    plots.remove(p);
                                                }
                                            }
                                        }
                                    }
                                });
                    } finally {
                        latch.countDown();
                    }
                    return null;
                }
            });
            tasks.add(task);
            executorService.submit(task);
        }
    }

    private void waitForAllThreadsToFinish(ExecutorService executorService, CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        executorService.shutdown();
    }

    private void assertNoConcurrentModificationExceptionThrown(List<FutureTask<Object>> tasks)
            throws InterruptedException {
        try {
            // we expect here no ConcurrentModificationException
            // otherwise access to plots list is not synchronized
            for (FutureTask task : tasks) {
                task.get();
            }
        } catch (ExecutionException | ConcurrentModificationException e) {
            fail("Access to PlotBuildAction#plots list is not synchronized");
        }
    }
}
