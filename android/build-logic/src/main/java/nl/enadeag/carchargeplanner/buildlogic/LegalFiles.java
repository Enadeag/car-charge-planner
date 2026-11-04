package nl.enadeag.carchargeplanner.buildlogic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Holds {@code android/legal} to what the Android app brings (#331): each library of the release build, by
 * {@code group:artifact} without its version as the desktop's {@code PackagingTest} does, is named by an
 * {@code Artifact:} line of exactly one file there and no file names a library the app no longer brings; each file a
 * {@code Copied:} block names exists, and each source file of the app and the engine that carries a third party's
 * header is named in one; each {@code META-INF/NOTICE*} in a library's jar is carried, under a
 * {@code ==== <entry> of <artifact>} line of the file that names the library, with the jar's text: line endings,
 * trailing spaces and blank lines at the ends of the text are ignored, any other difference fails. Aars are not looked
 * into: CONTRIBUTING has whoever adds one look into its {@code META-INF}.
 */
public final class LegalFiles {

    /** Where the files are, as the problems name them. */
    public static final String FOLDER = "android/legal/";

    private static final Pattern ARTIFACT = Pattern.compile("^Artifact: (\\S+)$", Pattern.MULTILINE);
    private static final Pattern CARRIED = Pattern.compile("^==== (\\S+) of ([^\\s,]+)", Pattern.MULTILINE);
    private static final Pattern PATH = Pattern.compile("\\S*/\\S*");

    /**
     * A comment line that starts with a copyright or a licence identifier, as a third party's header does; the app's
     * own files carry none, as {@code NOTICE} covers them.
     */
    private static final Pattern HEADER = Pattern.compile(
            "^\\s*(?:/\\*+|\\*|//|#|<!--)\\s*(?:Copyright\\b|\\(c\\)|©|SPDX-License-Identifier:)",
            Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

    private LegalFiles() {
    }

    /**
     * Whether a source file carries a third party's header.
     *
     * @param text the file's text; never null
     * @return true when one of its comment lines starts with a copyright or an SPDX licence identifier
     */
    public static boolean hasThirdPartyHeader(String text) {
        return HEADER.matcher(text).find();
    }

    /**
     * Compares the files with what the app brings.
     *
     * @param modulesByConfiguration per configuration's name, such as {@code releaseRuntimeClasspath}, the
     *     {@code group:artifact} of each library it brings, the app's own modules left out; never null
     * @param noticesByModule per {@code group:artifact}, per {@code META-INF/NOTICE*} entry of its jars, the entry's
     *     text; a library without any may be left out; never null
     * @param legalFiles per file name in {@code android/legal}, such as {@code apache-2.0.txt}, its text; never null
     * @param withHeader the paths, from the repository's root, of the app's source files that carry a third party's
     *     header; never null
     * @param exists whether a path from the repository's root, as a {@code Copied:} block names it, is a file
     * @return each problem with its fix, in English, sorted; empty when there is none
     */
    public static List<String> problems(Map<String, ? extends Collection<String>> modulesByConfiguration,
            Map<String, ? extends Map<String, String>> noticesByModule, Map<String, String> legalFiles,
            Collection<String> withHeader, Predicate<String> exists) {
        var problems = new ArrayList<String>();
        var namedIn = new TreeMap<String, Set<String>>();
        var copied = new TreeSet<String>();
        new TreeMap<>(legalFiles).forEach((file, text) -> {
            ARTIFACT.matcher(text).results().forEach(match ->
                    namedIn.computeIfAbsent(match.group(1), ignored -> new TreeSet<>()).add(file));
            copied.addAll(copiedFiles(file, text, exists, problems));
        });

        var used = new TreeMap<String, Set<String>>();
        modulesByConfiguration.forEach((configuration, modules) -> modules.forEach(module ->
                used.computeIfAbsent(module, ignored -> new TreeSet<>()).add(configuration)));
        used.forEach((module, configurations) -> {
            if (!namedIn.containsKey(module)) {
                problems.add("the release build has `" + module + "` (via `" + String.join("`, `", configurations)
                        + "`) but no file in android/legal names it: add an `Artifact:` line to the file of its "
                        + "licence (see CONTRIBUTING)");
            }
        });
        namedIn.forEach((module, files) -> {
            if (!used.containsKey(module)) {
                files.forEach(file -> problems.add(FOLDER + file + " names `" + module
                        + "`, which the app no longer uses: remove the line"));
            } else if (files.size() > 1) {
                problems.add("`" + module + "` is named in " + FOLDER + String.join(" and " + FOLDER, files)
                        + ": keep one `Artifact:` line, in the file of its licence");
            } else {
                var file = files.iterator().next();
                var artifact = module.substring(module.indexOf(':') + 1);
                Map<String, String> inJars = noticesByModule.get(module);
                for (var notice : new TreeMap<>(inJars == null ? Map.<String, String>of() : inJars).entrySet()) {
                    var name = notice.getKey();
                    var carried = carriedText(legalFiles.get(file), name, artifact);
                    if (carried == null) {
                        problems.add("`" + module + "` has " + name + " in its jar, which " + FOLDER + file
                                + " does not carry: add its text there under a line `==== " + name + " of "
                                + artifact + " ====`");
                    } else if (!tidy(carried).equals(tidy(notice.getValue()))) {
                        problems.add(FOLDER + file + " carries " + name + " of " + artifact + " with a text that "
                                + "differs from the one in the jar of `" + module + "`: take over the jar's new "
                                + "NOTICE text");
                    }
                }
            }
        });
        for (var path : new TreeSet<>(withHeader)) {
            if (!copied.contains(path)) {
                problems.add("`" + path + "` has a third party's header but is in no `Copied:` block: name it in the "
                        + "`Copied:` block of the file of its licence in android/legal (see CONTRIBUTING)");
            }
        }
        problems.sort(null);
        return problems;
    }

    /**
     * The files that a legal file's {@code Copied:} blocks name: each block runs from its {@code Copied:} line to its
     * {@code From:} line, and names each file on a line of its own, as a path from the repository's root.
     */
    private static List<String> copiedFiles(String file, String text, Predicate<String> exists,
            List<String> problems) {
        var named = new ArrayList<String>();
        List<String> block = null;
        for (var line : text.split("\n", -1)) {
            var trimmed = line.strip();
            if (trimmed.startsWith("Copied:")) {
                if (block != null) {
                    problems.add(FOLDER + file + " has a `Copied:` block without a `From:` line: say where the "
                            + "copied file comes from");
                }
                block = new ArrayList<>();
            } else if (block != null && trimmed.startsWith("From:")) {
                if (block.isEmpty()) {
                    problems.add(FOLDER + file + " has a `Copied:` block that names no file: put the copied "
                            + "file's path, from the repository's root, on a line of its own");
                }
                named.addAll(block);
                block = null;
            } else if (block != null && PATH.matcher(trimmed).matches()) {
                block.add(trimmed);
            }
        }
        if (block != null) {
            problems.add(FOLDER + file + " has a `Copied:` block without a `From:` line: say where the copied file "
                    + "comes from");
        }
        for (var path : named) {
            if (!exists.test(path)) {
                problems.add(FOLDER + file + " names `" + path + "` in a `Copied:` block, but that is no file of "
                        + "the repository (a folder, a symbolic link, a path through one and a path out of the "
                        + "repository are none): correct the path, or remove the block when the file is gone");
            }
        }
        return named;
    }

    /**
     * The text a file carries under the {@code ==== <entry> of <artifact>} line: from the line after it to the next
     * such line, or the end.
     *
     * @return the text, or null when no line of the file carries the entry of the artifact
     */
    private static String carriedText(String text, String notice, String artifact) {
        var headings = CARRIED.matcher(text).results().toList();
        for (var i = 0; i < headings.size(); i++) {
            var heading = headings.get(i);
            if (heading.group(1).equals(notice) && heading.group(2).equals(artifact)) {
                var lineEnd = text.indexOf('\n', heading.end());
                var end = i + 1 < headings.size() ? headings.get(i + 1).start() : text.length();
                return lineEnd < 0 || lineEnd >= end ? "" : text.substring(lineEnd + 1, end);
            }
        }
        return null;
    }

    /** The text with {@code \n} line endings, no trailing spaces and no blank lines at its start or end. */
    private static String tidy(String text) {
        return text.lines().map(String::stripTrailing)
                .collect(Collectors.joining("\n")).replaceAll("^\n+|\n+$", "");
    }
}
