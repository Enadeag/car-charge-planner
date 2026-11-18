package nl.enadeag.carchargeplanner.buildlogic;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * Fails when the release build is not shrunk by R8, R8 may rename or optimise its classes, or no rule keeps the
 * source files and lines ({@code -keepattributes SourceFile,LineNumberTable}): the log's stack traces
 * and tags name the app's classes, files and lines, so they must stay as they are (docs/design/android/build.md, R8).
 * A tripwire against a mistake in the build script or the app's own rule files, not against intent: it reads only
 * those files, not the libraries' consumer rules, which cannot switch renaming or optimising back on once
 * {@code -dontobfuscate} and {@code -dontoptimize} are given.
 */
public abstract class ReleaseShrinkingCheck extends DefaultTask {

    /** Makes the task; Gradle fills in its properties. */
    public ReleaseShrinkingCheck() {
        setGroup("verification");
        setDescription("Checks that R8 shrinks the release build and keeps its names, files and lines.");
    }

    /**
     * Whether the release build type has {@code isMinifyEnabled}.
     *
     * @return the switch
     */
    @Input
    public abstract Property<Boolean> getMinified();

    /**
     * The release build type's own rule files; AGP's default file, which never turns renaming off, is left out.
     *
     * @return the files
     */
    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getRuleFiles();

    /**
     * Reads the rule files and fails on a problem.
     *
     * @throws GradleException naming each problem
     */
    @TaskAction
    public void check() {
        var texts = new ArrayList<String>();
        for (var file : getRuleFiles().getFiles()) {
            try {
                texts.add(Files.readString(file.toPath(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException("cannot read the rule file " + file, e);
            }
        }
        var problems = problems(getMinified().get(), texts);
        if (!problems.isEmpty()) {
            throw new GradleException("The release build must be shrunk by R8 and keep its names and lines (#485): "
                    + String.join("; ", problems));
        }
    }

    /** What is wrong with the release build, one text per problem; a {@code #} starts a comment, as in R8's files. */
    static List<String> problems(boolean minified, List<String> ruleTexts) {
        var options = new ArrayList<String>();
        for (var text : ruleTexts) {
            for (var line : text.split("\\R")) {
                var hash = line.indexOf('#');
                var rule = hash < 0 ? line : line.substring(0, hash);
                options.addAll(Arrays.asList(rule.trim().split("\\s+")));
            }
        }
        var problems = new ArrayList<String>();
        if (!minified) {
            problems.add("isMinifyEnabled is false");
        }
        if (options.contains("-dontshrink")) {
            problems.add("a rule file says -dontshrink");
        }
        if (!options.contains("-dontobfuscate")) {
            problems.add("no rule file says -dontobfuscate, so R8 would rename the classes");
        }
        if (!options.contains("-dontoptimize")) {
            problems.add("no rule file says -dontoptimize, so R8 would number the lines anew");
        }
        // the attributes that follow each -keepattributes, up to the next option
        var attributes = new ArrayList<String>();
        var kept = false;
        for (var option : options) {
            kept = option.equals("-keepattributes") || kept && !option.startsWith("-");
            if (kept && !option.startsWith("-")) {
                attributes.addAll(Arrays.asList(option.split(",")));
            }
        }
        if (!attributes.containsAll(List.of("SourceFile", "LineNumberTable"))) {
            problems.add("no rule file keeps SourceFile,LineNumberTable, so frames would lose their files and lines");
        }
        return problems;
    }
}
