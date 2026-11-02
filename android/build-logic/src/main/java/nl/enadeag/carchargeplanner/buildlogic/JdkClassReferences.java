package nl.enadeag.carchargeplanner.buildlogic;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * The {@code java.*} and {@code javax.*} classes a compiled class refers to, read from its constant pool: the classes
 * it names, and the types in its descriptors and generic signatures. A tiny reader, as the build runs on a JDK without
 * {@code java.lang.classfile} and without ASM on its classpath.
 */
public final class JdkClassReferences {

    private static final Pattern IN_DESCRIPTOR = Pattern.compile("L(javax?/[^;<>.\\[]+)[;<]");

    private JdkClassReferences() {
    }

    /**
     * Reads one class file.
     *
     * @param classFile the class file's bytes, from the start; never null, not closed
     * @return the referenced JDK classes in internal form, such as {@code java/lang/ProcessHandle}, sorted
     * @throws IOException when the stream fails or does not hold a class file
     */
    public static Set<String> in(InputStream classFile) throws IOException {
        var in = new DataInputStream(classFile);
        if (in.readInt() != 0xCAFEBABE) {
            throw new IOException("not a class file");
        }
        in.readInt();
        var found = new TreeSet<String>();
        var texts = new String[in.readUnsignedShort()];
        var classIndexes = new TreeSet<Integer>();
        for (int i = 1; i < texts.length; i++) {
            int tag = in.readUnsignedByte();
            switch (tag) {
                case 1 -> texts[i] = in.readUTF();
                case 7 -> classIndexes.add(in.readUnsignedShort());
                case 8, 16, 19, 20 -> in.skipNBytes(2);
                case 15 -> in.skipNBytes(3);
                case 3, 4, 9, 10, 11, 12, 17, 18 -> in.skipNBytes(4);
                case 5, 6 -> {
                    in.skipNBytes(8);
                    i++;
                }
                default -> throw new IOException("unknown constant pool tag " + tag);
            }
        }
        for (int index : classIndexes) {
            var name = texts[index];
            if (name.startsWith("java/") || name.startsWith("javax/")) {
                found.add(name);
            }
        }
        for (var text : texts) {
            if (text != null) {
                IN_DESCRIPTOR.matcher(text).results().forEach(match -> found.add(match.group(1)));
            }
        }
        return found;
    }
}
