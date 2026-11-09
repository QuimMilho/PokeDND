/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.cards;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.quimmilho.pokednd.api.cards.CardData.MoveLine;
import net.quimmilho.pokednd.api.cards.CardData.Multiplier;
import net.quimmilho.pokednd.api.cards.CardData.Stats;
import net.quimmilho.pokednd.api.domain.reference.ReferenceRepository;
import net.quimmilho.pokednd.api.domain.reference.ReferenceRepository.EntryKind;
import net.quimmilho.pokednd.api.domain.reference.ReferenceRepository.NamedRecord;
import net.quimmilho.pokednd.api.domain.reference.ReferenceRepository.SpeciesRecord;
import net.quimmilho.pokednd.api.error.ApiException;
import net.quimmilho.pokednd.core.combat.CombatSettings;
import net.quimmilho.pokednd.core.combat.HitRoll;
import net.quimmilho.pokednd.core.combat.MoveProfile;
import net.quimmilho.pokednd.core.dice.DiceChoice;
import net.quimmilho.pokednd.core.importer.Catalog.EffectClass;
import net.quimmilho.pokednd.core.importer.Catalog.MoveEntry;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Collects what is printed on a species card (chapter 9.2): the data from the reference tables, the dice from Core's calculators
 * (default rule set), the texts from Cobblemon's language files and the art from {@code resources/card-art}.
 */
@ApplicationScoped
public class CardDataFactory {

    /** One card to make: a species and one of its forms. */
    public record CardRef(String speciesId, String formId) {
    }

    @Inject
    ReferenceRepository repository;

    @Inject
    ObjectMapper json;

    @ConfigProperty(name = "pokednd.resources.dir")
    String resourcesDir;

    private volatile GameText gameText;

    private GameText text() {
        GameText t = gameText;
        if (t == null) {
            t = new GameText(Path.of(resourcesDir), json);
            gameText = t;
        }
        return t;
    }

    /** The folder with {@code card-art}, {@code icons} and the language files. */
    public Path resources() {
        return Path.of(resourcesDir);
    }

    /** Builds the cards, in the order given. */
    public List<CardData> build(List<CardRef> refs, String language) {
        return new Builder(repository, text(), resources(), CombatSettings.defaults()).build(refs, language);
    }

    /** Which cards exist for a species: every playable form except the purely cosmetic gender variants. */
    public static List<CardRef> cardsOf(SpeciesRecord s, boolean includeForms) {
        List<CardRef> out = new ArrayList<>();
        for (JsonNode f : s.data().path("forms")) {
            if (!f.path("playable").asBoolean(false)) {
                continue;
            }
            String slug = f.path("slug").asText();
            boolean base = isBase(f);
            if (!base && (!includeForms || slug.equals("female") && f.path("kind").asText().equals("NAMED")
                    || slug.equals("hisuian-female"))) {
                continue;
            }
            out.add(new CardRef(s.id(), f.path("id").asText()));
        }
        return out;
    }

    static boolean isBase(JsonNode form) {
        return "BASE".equals(form.path("kind").asText()) || form.path("id").asText().endsWith("_base");
    }

    /** Does the work; separate from the CDI bean so that plain tests can use it. */
    public static final class Builder {

        private final ReferenceRepository repository;
        private final GameText text;
        private final Path resources;
        private final CombatSettings settings;

        public Builder(ReferenceRepository repository, GameText text, Path resources, CombatSettings settings) {
            this.repository = repository;
            this.text = text;
            this.resources = resources;
            this.settings = settings;
        }

        public List<CardData> build(List<CardRef> refs, String language) {
            Set<String> speciesIds = new LinkedHashSet<>();
            refs.forEach(r -> speciesIds.add(r.speciesId()));
            Map<String, SpeciesRecord> species = new HashMap<>();
            repository.speciesByIds(speciesIds).forEach(s -> species.put(s.id(), s));

            Set<String> moveIds = new LinkedHashSet<>();
            Set<String> abilityIds = new LinkedHashSet<>();
            for (CardRef r : refs) {
                SpeciesRecord s = species.get(r.speciesId());
                if (s == null) {
                    throw ApiException.notFound("The species \"" + r.speciesId() + "\"");
                }
                if (s.derived() != null) {
                    s.derived().path("signature").path(r.formId()).forEach(m -> moveIds.add(m.asText()));
                }
                JsonNode form = form(s, r.formId());
                form.path("abilities").forEach(a -> abilityIds.add(a.asText()));
                if (form.hasNonNull("hiddenAbility")) {
                    abilityIds.add(form.get("hiddenAbility").asText());
                }
                s.data().path("abilities").forEach(a -> abilityIds.add(a.asText()));
                if (s.data().hasNonNull("hiddenAbility")) {
                    abilityIds.add(s.data().get("hiddenAbility").asText());
                }
            }
            Map<String, NamedRecord> moves = new HashMap<>();
            repository.entries(EntryKind.MOVE, moveIds).forEach(m -> moves.put(m.id(), m));
            Map<String, NamedRecord> abilities = new HashMap<>();
            repository.entries(EntryKind.ABILITY, abilityIds).forEach(a -> abilities.put(a.id(), a));

            List<CardData> out = new ArrayList<>();
            for (CardRef r : refs) {
                out.add(card(species.get(r.speciesId()), r.formId(), language, moves, abilities));
            }
            return out;
        }

        private CardData card(SpeciesRecord s, String formId, String language, Map<String, NamedRecord> moves, Map<String, NamedRecord> abilities) {
            JsonNode form = form(s, formId);
            List<String> warnings = new ArrayList<>();
            boolean base = isBase(form);
            String key = CardIds.key(s.dex(), s.id(), formId, base);

            List<String> types = strings(form.has("types") ? form.get("types") : s.data().path("types"));
            List<String> typeNames = types.stream().map(t -> text.get("cobblemon.type." + t, language).orElse(capitalise(t))).toList();

            String baseName = text.get("cobblemon.species." + s.id() + ".name", language).orElse(s.name());
            String formText = form.path("text").asText("");
            String name = base || formText.isBlank() ? baseName : baseName + " (" + formText + ")";

            JsonNode d = s.data().path("baseStats");
            Stats stats = new Stats(d.path("attack").asInt(), d.path("defense").asInt(), d.path("specialAttack").asInt(),
                    d.path("specialDefense").asInt(), d.path("speed").asInt());

            List<String> abilityIds = strings(form.has("abilities") ? form.get("abilities") : s.data().path("abilities"));
            String hiddenId = form.hasNonNull("hiddenAbility") ? form.get("hiddenAbility").asText()
                    : form.has("abilities") ? null : s.data().path("hiddenAbility").asText(null);
            List<String> abilityNames = abilityIds.stream().map(a -> abilityName(a, language, abilities)).toList();
            String hidden = hiddenId == null || hiddenId.isEmpty() ? null : abilityName(hiddenId, language, abilities);

            JsonNode derived = s.derived() == null ? json() : s.derived();
            JsonNode weak = form.has("weaknesses") ? form.get("weaknesses") : derived.path("weaknesses");
            JsonNode resist = form.has("resistances") ? form.get("resistances") : derived.path("resistances");
            JsonNode immune = form.has("immunities") ? form.get("immunities") : derived.path("immunities");

            List<MoveLine> lines = new ArrayList<>();
            for (JsonNode m : derived.path("signature").path(formId)) {
                NamedRecord rec = moves.get(m.asText());
                if (rec == null) {
                    warnings.add(key + ": the move " + m.asText() + " is not in the move table");
                    continue;
                }
                lines.add(moveLine(rec, language));
            }
            if (lines.size() < 4) {
                warnings.add(key + ": only " + lines.size() + " signature move" + (lines.size() == 1 ? "" : "s") + " (see reports/signature.md)");
            }

            // the art: high-resolution card art first, then the 128 px icon
            String file = (base ? s.id() : formId) + ".png";
            String folder = String.format(Locale.ROOT, "%04d_%s", s.dex(), s.id());
            Path art = resources.resolve("card-art").resolve(folder).resolve(file);
            boolean icon = false;
            if (!Files.isRegularFile(art)) {
                Path ic = resources.resolve("icons").resolve(folder).resolve(file);
                if (Files.isRegularFile(ic)) {
                    art = ic;
                    icon = true;
                    warnings.add(key + ": no card art, used the 128 px icon");
                } else {
                    art = null;
                    warnings.add(key + ": no card art and no icon");
                }
            }

            String flavour = text.get("cobblemon.species." + s.id() + ".desc", language).map(t -> t.replaceAll("\\s+", " ").strip()).orElse("");
            String stage = stage(derived.path("stage").asText("BASIC"), language);

            return new CardData(key, CardIds.qrId(key), s.dex(), s.id(), formId, name, types, typeNames, stage, s.data().path("baseStats").path("hp").asInt(),
                    stats, lines, abilityNames, hidden, multipliers(weak, language), multipliers(resist, language),
                    strings(immune).stream().map(t -> text.get("cobblemon.type." + t, language).orElse(capitalise(t))).toList(), flavour, art, icon,
                    warnings);
        }

        private MoveLine moveLine(NamedRecord rec, String language) {
            JsonNode m = rec.data();
            MoveProfile profile = MoveProfile.from(entry(m));
            String name = text.get("cobblemon.move." + rec.id(), language).orElse(rec.name());

            String dice;
            if (!profile.damaging()) {
                dice = "—";
            } else if (profile.ohko()) {
                dice = "OHKO";
            } else if (profile.fixedDamage().isPresent()) {
                dice = language.equals("pt") ? "fixo" : "fixed";
            } else {
                dice = profile.damageDice(settings).map(Object::toString).orElse(language.equals("pt") ? "varia" : "varies");
            }

            HitRoll.Odds odds = profile.normalHit(settings);
            String hit = hitText(odds.choice(), language);

            String description = text.get("cobblemon.move." + rec.id() + ".desc", language).orElse(m.path("description").asText(""));
            return new MoveLine(name, profile.type().id(), profile.category().name().toLowerCase(Locale.ROOT), dice, hit, firstSentence(description),
                    m.path("pp").asInt());
        }

        /** {@code d20 · miss 1–4 · hit 5–19 · crit 20}, or "always hits". */
        static String hitText(DiceChoice choice, String language) {
            boolean pt = language.equals("pt");
            if (!choice.rolled()) {
                return pt ? "acerto automático" : "always hits";
            }
            StringBuilder sb = new StringBuilder(choice.dieName());
            for (DiceChoice.Band b : choice.bands()) {
                if (b.faces() == 0) {
                    continue;
                }
                String label = switch (b.label()) {
                    case "miss" -> pt ? "falha" : "miss";
                    case "hit" -> pt ? "acerto" : "hit";
                    case "crit" -> pt ? "crítico" : "crit";
                    default -> b.label();
                };
                sb.append(" · ").append(label).append(' ').append(b.range());
            }
            return sb.toString();
        }

        private static MoveEntry entry(JsonNode m) {
            Optional<Integer> accuracy = m.hasNonNull("accuracy") ? Optional.of(m.get("accuracy").asInt()) : Optional.empty();
            return new MoveEntry(m.path("id").asText(), m.path("name").asText(), m.path("type").asText(), m.path("category").asText(), m.path("power").asInt(),
                    accuracy, m.path("pp").asInt(), m.path("priority").asInt(), m.path("target").asText(), m.path("description").asText(""),
                    EffectClass.valueOf(m.path("effectClass").asText("AUTOMATIC")), List.of(), List.of(), m.path("effect"));
        }

        private String abilityName(String id, String language, Map<String, NamedRecord> abilities) {
            return text.get("cobblemon.ability." + id, language).orElseGet(() -> abilities.containsKey(id) ? abilities.get(id).name() : capitalise(id));
        }

        private List<Multiplier> multipliers(JsonNode node, String language) {
            List<Multiplier> out = new ArrayList<>();
            node.fields().forEachRemaining(e -> out.add(new Multiplier(e.getKey(), text.get("cobblemon.type." + e.getKey(), language).orElse(capitalise(e.getKey())),
                    e.getValue().asDouble())));
            out.sort(java.util.Comparator.comparingDouble((Multiplier m) -> -m.factor()).thenComparing(Multiplier::typeId));
            return out;
        }

        private static String stage(String stage, String language) {
            boolean pt = language.equals("pt");
            return switch (stage) {
                case "STAGE_1" -> pt ? "Fase 1" : "Stage 1";
                case "STAGE_2" -> pt ? "Fase 2" : "Stage 2";
                default -> pt ? "Básico" : "Basic";
            };
        }

        /** The first sentence of a text (cards have room for one line). */
        static String firstSentence(String description) {
            String t = description.replaceAll("\\s+", " ").strip();
            int dot = t.indexOf(". ");
            return dot > 0 ? t.substring(0, dot + 1) : t;
        }

        private static JsonNode form(SpeciesRecord s, String formId) {
            for (JsonNode f : s.data().path("forms")) {
                if (f.path("id").asText().equals(formId)) {
                    return f;
                }
            }
            throw ApiException.notFound("The form \"" + formId + "\" of " + s.id());
        }

        private static List<String> strings(JsonNode node) {
            List<String> out = new ArrayList<>();
            node.forEach(n -> out.add(n.asText().toLowerCase(Locale.ROOT)));
            return out;
        }

        private static JsonNode json() {
            return new ObjectMapper().createObjectNode();
        }

        private static String capitalise(String id) {
            return id.isEmpty() ? id : Character.toUpperCase(id.charAt(0)) + id.substring(1);
        }
    }
}
