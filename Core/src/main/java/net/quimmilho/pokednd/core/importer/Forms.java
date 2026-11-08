/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.importer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Turns the free-text {@code form} values of {@code pokemon-attacks.csv} into form ids (chapter 19.4).
 *
 * <p>The 63 values found in the data are listed here, each with a short slug and a kind. A value that is not in the list is
 * <em>unmapped</em>: it is reported and never guessed. Two values (the Gigantamax ones) are listed on purpose as
 * unsupported, because the assets have no Gigantamax forms.
 *
 * <p>A form id is {@code <speciesId>_<slug>}; the base form is {@code <speciesId>_base}. When the species is in the roster
 * and has a resolver file for the form, the roster's own id is used and the form is <em>playable</em>.
 */
public final class Forms {

    /** What kind of form a text value stands for. */
    public enum Kind {
        /** The base form (blank text). */
        BASE,
        /** Alolan, Galarian, Hisuian or Paldean. */
        REGIONAL,
        /** The female form of a species whose genders look different. */
        GENDER,
        /** A type (Arceus and Silvally). */
        TYPE,
        /** Any other named form (Origin, Dusk, Zen…). */
        NAMED,
        /** Known, but the game does not support it (Gigantamax). */
        UNSUPPORTED
    }

    /**
     * One known value of the {@code form} column.
     *
     * @param text the value as written ({@code ""} for the base form)
     * @param slug short lower-case name used in form ids
     * @param kind what kind of form it is
     */
    public record Known(String text, String slug, Kind kind) {
    }

    /**
     * A form id and whether it can be played.
     *
     * @param id       form id (the roster's own id when playable)
     * @param text     the form text as written in the data
     * @param slug     short name of the form
     * @param kind     what kind of form it is
     * @param playable the roster has a resolver file for it
     */
    public record FormRef(String id, String text, String slug, Kind kind, boolean playable) {
    }

    private static final Map<String, Known> KNOWN = new LinkedHashMap<>();

    private static void add(String text, String slug, Kind kind) {
        KNOWN.put(text, new Known(text, slug, kind));
    }

    static {
        add("", "base", Kind.BASE);
        add("Alola", "alolan", Kind.REGIONAL);
        add("Galar", "galarian", Kind.REGIONAL);
        add("Hisui", "hisuian", Kind.REGIONAL);
        add("Paldea", "paldean", Kind.REGIONAL);
        add("Paldea-Combat", "paldean_combat", Kind.REGIONAL);
        add("Paldea-Blaze", "paldean_blaze", Kind.REGIONAL);
        add("Paldea-Aqua", "paldean_aqua", Kind.REGIONAL);
        add("F", "female", Kind.GENDER);
        for (String type : List.of("Fighting", "Flying", "Poison", "Ground", "Rock", "Bug", "Ghost", "Steel", "Fire", "Water",
                "Grass", "Electric", "Psychic", "Ice", "Dragon", "Dark", "Fairy")) {
            add(type, type.toLowerCase(Locale.ROOT), Kind.TYPE);
        }
        for (String named : List.of("Attack", "Defense", "Speed", "Sandy", "Trash", "Sunshine", "Heat", "Wash", "Frost", "Fan",
                "Mow", "Origin", "Sky", "White-Striped", "Zen", "Galar-Zen", "White", "Black", "Eternal", "Blade", "Complete",
                "Unbound", "Dusk", "Midnight", "Busted", "Dusk-Mane", "Dawn-Wings", "Low-Key", "Rapid-Strike", "Shadow",
                "Bloodmoon", "Hero")) {
            add(named, named.toLowerCase(Locale.ROOT).replace("-", ""), Kind.NAMED);
        }
        add("10%", "10percent", Kind.NAMED);
        add("10%-C", "10percent_c", Kind.NAMED);
        add("50%-C", "50percent_c", Kind.NAMED);
        add("Low-Key-Gmax", "lowkey_gmax", Kind.UNSUPPORTED);
        add("Rapid-Strike-Gmax", "rapidstrike_gmax", Kind.UNSUPPORTED);
    }

    private Forms() {
    }

    /** All known values, in the order listed above. */
    public static List<Known> known() {
        return List.copyOf(KNOWN.values());
    }

    /** The known value for this text, if it is one of the reviewed values. */
    public static Optional<Known> lookup(String text) {
        return Optional.ofNullable(KNOWN.get(text));
    }

    /** Lower-case letters and digits only, so {@code paldean_combat} and {@code paldean-combat} compare equal. */
    static String squash(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /**
     * Maps one form text of one species to a form id.
     *
     * @param speciesId the Cobblemon species id (for example {@code mrmime})
     * @param dex       National Dex number (to find the species in the roster)
     * @param text      the form text as written
     * @param roster    the playable roster
     * @return the form, or empty when the text is not a reviewed value or is unsupported
     */
    public static Optional<FormRef> map(String speciesId, int dex, String text, Roster roster) {
        Known k = KNOWN.get(text);
        if (k == null || k.kind() == Kind.UNSUPPORTED) {
            return Optional.empty();
        }
        Roster.Entry entry = roster.species().get(dex);
        if (entry != null) {
            Optional<Roster.Form> match = k.kind() == Kind.BASE ? entry.base()
                    : entry.forms().stream().filter(f -> matches(entry, f, k)).findFirst();
            if (match.isPresent()) {
                return Optional.of(new FormRef(match.get().id(), text, k.slug(), k.kind(), true));
            }
        }
        return Optional.of(new FormRef(speciesId + "_" + k.slug(), text, k.slug(), k.kind(), false));
    }

    /** Whether a resolver form is the one a known value stands for. */
    private static boolean matches(Roster.Entry entry, Roster.Form form, Known k) {
        if (form.aspects().isEmpty()) {
            return false; // the base form only matches the blank text
        }
        String want = squash(k.slug());
        String id = squash(form.id());
        String prefix = squash(entry.id());
        if (id.startsWith(prefix) && id.substring(prefix.length()).equals(want)) {
            return true;
        }
        if (form.aspects().size() == 1) {
            return squash(form.aspects().get(0).replaceAll("-form$", "")).equals(want);
        }
        return false;
    }

    /** The distinct texts among {@code texts} that are not a reviewed value or are unsupported, sorted. */
    public static List<String> unmapped(Iterable<String> texts) {
        TreeSet<String> out = new TreeSet<>();
        for (String t : texts) {
            Known k = KNOWN.get(t);
            if (k == null || k.kind() == Kind.UNSUPPORTED) {
                out.add(t);
            }
        }
        return new ArrayList<>(out);
    }
}
