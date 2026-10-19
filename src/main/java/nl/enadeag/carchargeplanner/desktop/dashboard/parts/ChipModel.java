package nl.enadeag.carchargeplanner.desktop.dashboard.parts;

/**
 * A short figure about one source of energy, as a {@link Chip} shows it: "92 % sun".
 *
 * @param source where the energy in the figure comes from or goes to
 * @param text the figure in words, such as "92 % sun" or "Battery 50 → 74 %"
 */
public record ChipModel(EnergySource source, String text) {
}
