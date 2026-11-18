package nl.enadeag.carchargeplanner.buildlogic;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * Fails when the instrumented tests' results hold a failure or no test at all: AGP 9.5 lets the connected test task
 * pass when the app's process crashes before the first test, which a missing keep rule can make it do. With a runner
 * that names its test classes itself, as the minified suite's does, it also fails when a class with a test is not
 * named there, as that class would never run (#485). A tripwire against a mistake: a text match, not a parse.
 */
public abstract class InstrumentedResultsCheck extends DefaultTask {

    /** Makes the task; Gradle fills in its properties. */
    public InstrumentedResultsCheck() {
        setGroup("verification");
        setDescription("Checks that the instrumented tests ran, all of them, and passed.");
    }

    /**
     * The connected test task's folder of XML results.
     *
     * @return the folder
     */
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getResults();

    /**
     * The sources of the tests, when {@link #getRunner()} is given; a file with {@code @Test} or {@code @org.junit.Test}
     * is a test class.
     *
     * @return the source files, empty for AndroidX Test's runner, which finds the classes itself
     */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getTestSources();

    /**
     * The runner's source, which names every test class it runs.
     *
     * @return the file, absent for AndroidX Test's runner
     */
    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getRunner();

    /**
     * Reads the results and sources and fails on a problem.
     *
     * @throws GradleException naming each problem
     */
    @TaskAction
    public void check() {
        var results = texts(getResults().get().getAsFileTree().matching(it -> it.include("**/*.xml")).getFiles());
        var problems = new ArrayList<>(resultProblems(results));
        if (getRunner().isPresent()) {
            var runner = texts(List.of(getRunner().get().getAsFile())).values().iterator().next();
            problems.addAll(unnamedTestClasses(texts(getTestSources().getFiles()), runner));
        }
        if (!problems.isEmpty()) {
            throw new GradleException("The instrumented tests failed or did not all run (the app's process may have "
                    + "crashed, logcat says why; results in " + getResults().get().getAsFile() + "): "
                    + String.join("; ", problems));
        }
    }

    /** What the results, per file name, show wrong: a failure in a file, or no test case in any. */
    static List<String> resultProblems(Map<String, String> results) {
        var problems = new ArrayList<String>();
        results.forEach((name, text) -> {
            if (text.contains("<failure")) {
                problems.add("a failure in " + name);
            }
        });
        if (results.values().stream().noneMatch(text -> text.contains("<testcase "))) {
            problems.add("no test ran");
        }
        return problems;
    }

    /** The test classes, per file name, with a test that the runner's source does not name as {@code Name.class}. */
    static List<String> unnamedTestClasses(Map<String, String> sources, String runner) {
        var test = Pattern.compile("@(org\\.junit\\.)?Test\\b");
        var problems = new ArrayList<String>();
        sources.forEach((name, text) -> {
            var dot = name.lastIndexOf('.');
            var className = dot < 0 ? name : name.substring(0, dot);
            var named = Pattern.compile("(?<![\\w$.])" + Pattern.quote(className) + "\\.class\\b");
            if (test.matcher(text).find() && !named.matcher(runner).find()) {
                problems.add(className + " has a test but the runner's suite does not name it, so it never runs");
            }
        });
        return problems;
    }

    private static Map<String, String> texts(Iterable<File> files) {
        var texts = new TreeMap<String, String>();
        for (var file : files) {
            try {
                texts.put(file.getName(), Files.readString(file.toPath(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException("cannot read " + file, e);
            }
        }
        return texts;
    }
}
