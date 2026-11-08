/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.importer;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.quimmilho.pokednd.core.importer.SpeciesMerger.Conflict;
import net.quimmilho.pokednd.core.importer.SpeciesMerger.FormUse;

/**
 * Writes the human-readable reports of the importer as Markdown text (chapter 19.2, {@code reports/*.md}).
 * Every report is a pure function of its input: the same input gives the same text, byte for byte.
 */
public final class Reports {

    private Reports() {
    }

    private static String cell(String s) {
        return s.replace("|", "\\|").replace("\n", " ");
    }

    /** {@code conflicts.md}: where the CSV and the Cobblemon JSON disagree. The CSV value is the one kept. */
    public static String conflicts(List<Conflict> conflicts) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Conflicts between the CSV and the Cobblemon JSON\n\n");
        sb.append("When the two disagree the **CSV wins** (decided, chapter 19.3). Total: ").append(conflicts.size())
                .append(".\n\n");
        TreeMap<String, Integer> perField = new TreeMap<>();
        for (Conflict c : conflicts) {
            perField.merge(c.field(), 1, Integer::sum);
        }
        if (conflicts.isEmpty()) {
            sb.append("The two sources agree on everything that was compared.\n");
            return sb.toString();
        }
        sb.append("| Field | Conflicts |\n|---|---:|\n");
        perField.forEach((f, n) -> sb.append("| ").append(cell(f)).append(" | ").append(n).append(" |\n"));
        sb.append("\n| Dex | Species | Field | CSV (kept) | Other | Other source |\n|---:|---|---|---|---|---|\n");
        for (Conflict c : conflicts) {
            sb.append("| ").append(c.dex()).append(" | ").append(c.species()).append(" | ").append(cell(c.field()))
                    .append(" | ").append(cell(c.csv())).append(" | ").append(cell(c.other())).append(" | ")
                    .append(c.source()).append(" |\n");
        }
        return sb.toString();
    }

    /** {@code forms.md}: how every form text of the learnsets was mapped, and what was left unmapped. */
    public static String forms(List<FormUse> uses, Map<String, Integer> unmapped) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Forms\n\n");
        sb.append("Every free-text `form` value of `pokemon-attacks.csv` and the form id it was mapped to (chapter 19.4). ")
                .append("A form is **playable** only when the roster has a resolver file for it.\n\n");
        sb.append("## Unmapped\n\n");
        if (unmapped.isEmpty()) {
            sb.append("None.\n\n");
        } else {
            sb.append("These values are known but not supported, or not known at all. Their learnset rows are not attached to a form.\n\n");
            sb.append("| Form text | Rows |\n|---|---:|\n");
            unmapped.forEach((t, n) -> sb.append("| `").append(cell(t)).append("` | ").append(n).append(" |\n"));
            sb.append('\n');
        }
        TreeMap<String, int[]> perText = new TreeMap<>();
        for (FormUse u : uses) {
            int[] c = perText.computeIfAbsent(u.text(), t -> new int[3]);
            c[0] += u.rows();
            c[1]++;
            if (u.form().isPresent() && u.form().get().playable()) {
                c[2]++;
            }
        }
        sb.append("## Values\n\n| Form text | Rows | Species | Of which playable |\n|---|---:|---:|---:|\n");
        perText.forEach((t, c) -> sb.append("| `").append(cell(t)).append("` | ").append(c[0]).append(" | ").append(c[1])
                .append(" | ").append(c[2]).append(" |\n"));
        sb.append("\n## Species and forms\n\n| Dex | Species | Form text | Form id | Playable | Rows |\n|---:|---|---|---|---|---:|\n");
        for (FormUse u : uses) {
            if (u.text().isEmpty()) {
                continue; // the base form of every species; it is the rule, not news
            }
            sb.append("| ").append(u.dex()).append(" | ").append(u.species()).append(" | `").append(cell(u.text())).append("` | ")
                    .append(u.form().map(f -> "`" + f.id() + "`").orElse("*unmapped*")).append(" | ")
                    .append(u.form().map(f -> f.playable() ? "yes" : "no").orElse("-")).append(" | ").append(u.rows())
                    .append(" |\n");
        }
        return sb.toString();
    }
}
