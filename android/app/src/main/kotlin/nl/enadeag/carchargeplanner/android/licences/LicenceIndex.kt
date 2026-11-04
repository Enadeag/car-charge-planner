package nl.enadeag.carchargeplanner.android.licences

import java.io.IOException

/**
 * What the screen of the licences lists (#331), as the build writes it into `licences/index.txt` from the
 * repository's files: the app's copyright line and the parts in their order, the app's own first.
 *
 * @property copyright the app's copyright line, as `NOTICE` has it
 * @property parts the parts, each a text of its own
 */
data class LicenceIndex(val copyright: String, val parts: List<LicencePart>) {

    companion object {
        /** The folder of the texts in the app's assets. */
        const val FOLDER = "licences/"

        /** The index's name in [FOLDER]. */
        const val INDEX = "index.txt"

        /**
         * Reads the index: a line `copyright<TAB>line`, and a line `part<TAB>asset<TAB>title` per part.
         *
         * @param text the index's text
         * @return the index
         * @throws IOException when a line is none of these, or the copyright line is missing, as the build never
         *     writes such an index
         */
        fun parse(text: String): LicenceIndex {
            var copyright: String? = null
            val parts = mutableListOf<LicencePart>()
            text.lineSequence().filter { it.isNotEmpty() }.forEach { line ->
                val fields = line.split('\t')
                when {
                    fields.size == 2 && fields[0] == "copyright" -> copyright = fields[1]
                    fields.size == 3 && fields[0] == "part" && fields[1].isNotEmpty() && fields[2].isNotEmpty() ->
                        parts += LicencePart(fields[1], fields[2])
                    else -> throw IOException("not a line of the licences' index: $line")
                }
            }
            return LicenceIndex(copyright ?: throw IOException("the licences' index has no copyright line"), parts)
        }
    }
}

/**
 * One text of the screen of the licences, such as the GPL or the Apache License with the libraries under it.
 *
 * @property asset its name in [LicenceIndex.FOLDER]
 * @property title what the screen lists it by, in English as the text is
 */
data class LicencePart(val asset: String, val title: String)

/** Reads the licences' texts; the app's assets in the app, a fixture in a test. Called off the main thread. */
fun interface LicenceFiles {

    /**
     * Reads one text.
     *
     * @param name its name in [LicenceIndex.FOLDER], or [LicenceIndex.INDEX]
     * @return its text
     * @throws IOException when it is missing or cannot be read
     */
    @Throws(IOException::class)
    fun read(name: String): String
}
