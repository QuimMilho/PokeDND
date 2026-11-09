/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.trainer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The class list of a game ({@code trainer.classes.set}, chapter 4.4). It is data: {@link #defaults()} reads the nine default
 * classes from {@code trainer/classes.json}, and {@link #fromJson} / {@link #toJson} let the API store an edited list.
 *
 * @param classes the classes, in the order they are shown
 */
public record TrainerClasses(List<TrainerClass> classes) {

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public TrainerClasses {
        classes = List.copyOf(classes);
        Set<String> seen = new HashSet<>();
        for (TrainerClass c : classes) {
            if (!seen.add(c.id())) {
                throw new IllegalArgumentException("Two classes have the id " + c.id());
            }
        }
    }

    /** The nine default classes of chapter 4.4. */
    public static TrainerClasses defaults() {
        try (InputStream in = TrainerClasses.class.getResourceAsStream("/trainer/classes.json")) {
            if (in == null) {
                throw new IllegalStateException("trainer/classes.json is missing from the Core jar");
            }
            return fromJson(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Could not read trainer/classes.json", e);
        }
    }

    /** Reads a class list written as {@code {"classes": [...]}}. A wrong file gives a readable message. */
    public static TrainerClasses fromJson(String json) {
        try {
            return MAPPER.readValue(json, TrainerClasses.class);
        } catch (JsonProcessingException e) {
            Throwable cause = e.getCause() instanceof IllegalArgumentException ? e.getCause() : e;
            throw new IllegalArgumentException("The class list is not valid: " + firstLine(cause.getMessage()), e);
        }
    }

    /** The list as JSON, in the form {@link #fromJson} reads. */
    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** One class by its id. */
    public Optional<TrainerClass> find(String id) {
        return classes.stream().filter(c -> c.id().equals(id)).findFirst();
    }

    /** The ids of all classes. */
    public List<String> ids() {
        return classes.stream().map(TrainerClass::id).toList();
    }

    /** Checks that every skill the classes mention exists in the skill list. Returns the problems, empty if all is well. */
    public List<String> check(List<SkillDef> skills) {
        Set<String> known = skills.stream().map(SkillDef::id).collect(Collectors.toSet());
        List<String> problems = new ArrayList<>();
        for (TrainerClass c : classes) {
            for (String s : c.skillChoices().from()) {
                if (!known.contains(s)) {
                    problems.add("The class " + c.id() + " lets players pick the skill " + s + ", which is not in the skill list.");
                }
            }
            for (ClassFeature f : c.features()) {
                for (FeatureEffect e : f.effects()) {
                    List<String> mentioned = new ArrayList<>(e.list("skills"));
                    if (e.params().containsKey("skill")) {
                        mentioned.add(e.params().get("skill"));
                    }
                    for (String s : mentioned) {
                        if (!known.contains(s)) {
                            problems.add("The feature " + f.id() + " of " + c.id() + " mentions the skill " + s + ", which is not in the skill list.");
                        }
                    }
                }
            }
        }
        return problems;
    }

    private static String firstLine(String text) {
        if (text == null) {
            return "unknown error";
        }
        int nl = text.indexOf('\n');
        return nl < 0 ? text : text.substring(0, nl);
    }
}
