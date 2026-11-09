/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.cards;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.quimmilho.pokednd.api.auth.Ids;
import net.quimmilho.pokednd.api.auth.RateLimiter;
import net.quimmilho.pokednd.api.cards.CardDataFactory.CardRef;
import net.quimmilho.pokednd.api.domain.cards.ExportJobRepository;
import net.quimmilho.pokednd.api.domain.cards.ExportJobRepository.ExportJob;
import net.quimmilho.pokednd.api.domain.reference.ReferenceRepository;
import net.quimmilho.pokednd.api.domain.reference.ReferenceRepository.Page;
import net.quimmilho.pokednd.api.domain.reference.ReferenceRepository.SpeciesFilter;
import net.quimmilho.pokednd.api.domain.reference.ReferenceRepository.SpeciesRecord;
import net.quimmilho.pokednd.api.error.ApiException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Card export jobs (chapters 9.6 and 16.5): a request is checked and stored as a job, a single background worker builds the PDF, and
 * the finished file stays downloadable for {@code cards.export.fileTtlHours}.
 */
@ApplicationScoped
public class ExportService {

    private static final Logger LOG = Logger.getLogger(ExportService.class);
    private static final Pattern CARD_KEY = Pattern.compile("^(\\d{4})_[a-z0-9_.\\-]+$");
    private static final int BATCH = 25;
    private static final int MAX_WARNINGS = 200;

    @Inject
    ExportJobRepository jobs;

    @Inject
    ReferenceRepository reference;

    @Inject
    CardDataFactory factory;

    @Inject
    ExportConfig config;

    @Inject
    ObjectMapper json;

    @Inject
    RateLimiter limiter;

    @ConfigProperty(name = "pokednd.data.dir")
    String dataDir;

    private ExecutorService worker;

    @PostConstruct
    void init() {
        // one job at a time: building a PDF takes memory
        worker = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "card-export");
            t.setDaemon(true);
            return t;
        });
    }

    @PreDestroy
    void shutdown() {
        worker.shutdownNow();
    }

    /** Jobs that a restart interrupted cannot continue: say so instead of leaving them "running" forever. */
    void onStart(@Observes StartupEvent event) {
        for (ExportJob job : jobs.unfinished()) {
            jobs.update(job.failed("The server restarted before this export finished. Start it again."));
        }
    }

    // ------------------------------------------------------------------ starting a job

    /**
     * Checks the request, stores the job and queues it.
     *
     * @throws ApiException 422 for an empty or too large selection, or options that make no sense; 429 if the person starts too many
     */
    public ExportJob start(UUID userId, ExportRequest request) {
        limiter.hit("export-user", userId.toString(), config.maxJobsPerHour(), Duration.ofHours(1));
        CardOptions options = options(request);
        List<CardRef> refs = resolve(request);
        if (refs.isEmpty()) {
            throw new ApiException(422, "EMPTY_SELECTION", "No card matches this selection. Choose a generation, a type or some cards.");
        }
        int printed = refs.size() * options.copies();
        if (printed > config.maxCardsPerJob()) {
            throw new ApiException(422, "EXPORT_TOO_LARGE", "This export has " + printed + " cards (" + refs.size() + " cards x " + options.copies()
                    + " copies) but one export may have at most " + config.maxCardsPerJob() + ". Choose fewer cards or fewer copies.",
                    Map.of("cards", refs.size(), "copies", options.copies(), "printed", printed, "limit", config.maxCardsPerJob()));
        }
        PageLayout.compute(options); // refuses sizes that do not fit the paper, now rather than in the background

        ObjectNode stored = json.createObjectNode();
        stored.set("request", json.valueToTree(request));
        stored.set("options", optionsJson(options));
        ArrayNode cardList = stored.putArray("cards");
        refs.forEach(r -> cardList.addObject().put("species", r.speciesId()).put("form", r.formId()));

        Instant now = Instant.now();
        ExportJob job = new ExportJob(Ids.newId(), userId, stored, ExportJobRepository.QUEUED, 0, refs.size(), null, List.of(), null, null, null, null,
                now, now);
        jobs.create(job);
        worker.submit(() -> run(job.id()));
        return job;
    }

    private CardOptions options(ExportRequest r) {
        CardOptions.CardSize size = CardOptions.CardSize.parse(r.size() == null ? config.defaultSizeMm() : r.size());
        CardOptions.Paper paper = CardOptions.Paper.parse(r.paper() == null ? config.defaultPaper() : r.paper());
        String colour = r.colour() == null ? "full" : r.colour().strip().toLowerCase(Locale.ROOT);
        if (!colour.equals("full") && !colour.equals("ink-saving")) {
            throw new ApiException(422, "INVALID_EXPORT_OPTIONS", "The colour must be full or ink-saving.");
        }
        if (r.rulesetId() != null && !r.rulesetId().isBlank() && !r.rulesetId().equals("default")) {
            throw new ApiException(422, "RULESET_UNSUPPORTED", "Only the default rule set can be used for cards for now.");
        }
        return new CardOptions(size, paper, r.bleedMm() == null ? config.bleedMm() : r.bleedMm(), r.gapMm() == null ? config.gapMm() : r.gapMm(),
                Boolean.TRUE.equals(r.backs()), r.language(), colour.equals("ink-saving"),
                r.copies() == null ? 1 : r.copies(), Boolean.TRUE.equals(r.showBackId()), true);
    }

    private ObjectNode optionsJson(CardOptions o) {
        ObjectNode n = json.createObjectNode();
        n.put("size", o.size().toString()).put("paper", o.paper().name()).put("bleedMm", o.bleedMm()).put("gapMm", o.gapMm())
                .put("backs", o.backs()).put("language", o.language()).put("inkSaving", o.inkSaving()).put("copies", o.copies())
                .put("showBackId", o.showBackId()).put("cropMarks", o.cropMarks());
        return n;
    }

    private static CardOptions optionsFrom(JsonNode n) {
        return new CardOptions(CardOptions.CardSize.parse(n.path("size").asText()), CardOptions.Paper.parse(n.path("paper").asText()),
                n.path("bleedMm").asDouble(), n.path("gapMm").asDouble(), n.path("backs").asBoolean(), n.path("language").asText("en"),
                n.path("inkSaving").asBoolean(), n.path("copies").asInt(1), n.path("showBackId").asBoolean(), n.path("cropMarks").asBoolean(true));
    }

    // ------------------------------------------------------------------ which cards

    /**
     * The cards of a request, in card-key order: those that match the generation and type filters, plus the ones named by hand.
     * Species without complete assets have no card.
     */
    List<CardRef> resolve(ExportRequest r) {
        boolean includeForms = r.includeForms() == null || r.includeForms();
        Map<String, CardRef> out = new TreeMap<>();

        Set<Integer> generations = new HashSet<>(r.generations() == null ? List.of() : r.generations());
        for (int g : generations) {
            if (g < 1 || g > 9) {
                throw new ApiException(422, "BAD_GENERATION", "The generation must be 1 to 9, not " + g + ".");
            }
        }
        Set<String> types = new HashSet<>();
        if (r.types() != null) {
            r.types().forEach(t -> types.add(t.strip().toLowerCase(Locale.ROOT)));
        }
        if (!generations.isEmpty() || !types.isEmpty()) {
            SpeciesFilter filter = new SpeciesFilter(generations, types, Boolean.TRUE.equals(r.primaryTypeOnly()), null, true);
            String cursor = null;
            do {
                Page<SpeciesRecord> page = reference.findSpecies(filter, cursor, 500);
                page.items().forEach(s -> add(out, s, CardDataFactory.cardsOf(s, includeForms)));
                cursor = page.next();
            } while (cursor != null);
        }

        if (r.cards() != null && !r.cards().isEmpty()) {
            Set<Integer> dexes = new HashSet<>();
            List<String> bad = new ArrayList<>();
            for (String key : r.cards()) {
                Matcher m = CARD_KEY.matcher(key == null ? "" : key.strip().toLowerCase(Locale.ROOT));
                if (m.matches()) {
                    dexes.add(Integer.parseInt(m.group(1)));
                } else {
                    bad.add(String.valueOf(key));
                }
            }
            Map<String, CardRef> known = new HashMap<>();
            for (SpeciesRecord s : reference.speciesByDex(dexes)) {
                for (CardRef ref : CardDataFactory.cardsOf(s, true)) {
                    known.put(keyOf(s, ref), ref);
                }
            }
            for (String key : r.cards()) {
                String k = key == null ? "" : key.strip().toLowerCase(Locale.ROOT);
                if (known.containsKey(k)) {
                    out.put(k, known.get(k));
                } else if (!bad.contains(String.valueOf(key))) {
                    bad.add(key);
                }
            }
            if (!bad.isEmpty()) {
                throw new ApiException(422, "UNKNOWN_CARD", "These cards do not exist (or have no complete assets): " + String.join(", ", bad) + ".",
                        Map.of("cards", bad));
            }
        }
        return new ArrayList<>(out.values());
    }

    private static void add(Map<String, CardRef> out, SpeciesRecord s, List<CardRef> refs) {
        for (CardRef ref : refs) {
            out.put(keyOf(s, ref), ref);
        }
    }

    private static String keyOf(SpeciesRecord s, CardRef ref) {
        boolean base = false;
        for (JsonNode f : s.data().path("forms")) {
            if (f.path("id").asText().equals(ref.formId())) {
                base = CardDataFactory.isBase(f);
            }
        }
        return CardIds.key(s.dex(), s.id(), ref.formId(), base);
    }

    // ------------------------------------------------------------------ the background work

    private void run(UUID id) {
        ExportJob job = jobs.find(id).orElse(null);
        if (job == null || !ExportJobRepository.QUEUED.equals(job.state())) {
            return;
        }
        try {
            job = job.withState(ExportJobRepository.RUNNING);
            jobs.update(job);
            CardOptions options = optionsFrom(job.request().path("options"));
            List<CardRef> refs = new ArrayList<>();
            job.request().path("cards").forEach(c -> refs.add(new CardRef(c.path("species").asText(), c.path("form").asText())));

            List<CardData> cards = new ArrayList<>();
            for (int i = 0; i < refs.size(); i += BATCH) {
                cards.addAll(factory.build(refs.subList(i, Math.min(refs.size(), i + BATCH)), options.language()));
                job = job.withProgress(cards.size());
                jobs.update(job);
            }
            CardPdf.Result result = CardPdf.write(cards, options);

            Path dir = exportDir();
            Files.createDirectories(dir);
            Path pdf = dir.resolve(id + ".pdf");
            Path manifest = dir.resolve(id + ".manifest.csv");
            write(pdf, result.pdf());
            write(manifest, result.manifestCsv().getBytes(StandardCharsets.UTF_8));
            List<String> warnings = result.warnings().size() > MAX_WARNINGS ? result.warnings().subList(0, MAX_WARNINGS) : result.warnings();
            jobs.update(job.done(warnings, pdf.toString(), manifest.toString(), result.pages(), Instant.now().plus(config.fileTtlHours(), ChronoUnit.HOURS)));
            LOG.infof("Card export %s done: %d cards, %d pages, %d warnings", id, refs.size(), result.pages(), result.warnings().size());
        } catch (Throwable e) {
            LOG.error("Card export " + id + " failed", e);
            String why = e instanceof ApiException ? e.getMessage() : "The export failed: " + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : " (" + e.getMessage() + ")");
            try {
                jobs.update((job == null ? jobs.find(id).orElseThrow() : job).failed(why));
            } catch (RuntimeException inner) {
                LOG.error("Cannot record the failure of export " + id, inner);
            }
        }
    }

    private static void write(Path file, byte[] bytes) throws IOException {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(tmp, bytes);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    Path exportDir() {
        return Path.of(dataDir).resolve("exports");
    }

    // ------------------------------------------------------------------ reading jobs

    /** A job of this person. Other people's jobs look like missing ones. */
    public ExportJob get(UUID userId, UUID id) {
        return jobs.find(id).filter(j -> j.userId().equals(userId)).orElseThrow(() -> ApiException.notFound("The export"));
    }

    public List<ExportJob> list(UUID userId, int limit) {
        return jobs.ofUser(userId, limit);
    }

    /**
     * The file of a finished export.
     *
     * @param manifest the manifest CSV instead of the PDF
     * @throws ApiException 409 while it is not finished or when it failed; 404 once it has expired
     */
    public Path file(UUID userId, UUID id, boolean manifest) {
        ExportJob job = get(userId, id);
        switch (job.state()) {
            case ExportJobRepository.QUEUED, ExportJobRepository.RUNNING ->
                    throw new ApiException(409, "EXPORT_NOT_READY", "The export is not finished yet (" + job.progress() + " of " + job.total() + " cards).");
            case ExportJobRepository.FAILED -> throw new ApiException(409, "EXPORT_FAILED", job.message() == null ? "The export failed." : job.message());
            default -> {
            }
        }
        if (ExportJobRepository.DONE.equals(job.state()) && job.expiresAt() != null && !job.expiresAt().isAfter(Instant.now())) {
            expire(job);
            job = jobs.find(id).orElse(job);
        }
        String path = manifest ? job.manifestPath() : job.filePath();
        if (!ExportJobRepository.DONE.equals(job.state()) || path == null || !Files.isRegularFile(Path.of(path))) {
            throw new ApiException(404, "EXPORT_EXPIRED", "This export is no longer available: finished exports are kept for " + config.fileTtlHours()
                    + " hours. Start a new export.");
        }
        return Path.of(path);
    }

    // ------------------------------------------------------------------ expiry

    /** Deletes the files of finished exports whose time is up and marks the jobs expired. Called by the scheduler. */
    public int cleanupExpired() {
        List<ExportJob> expired = jobs.doneAndExpiredBefore(Instant.now());
        expired.forEach(this::expire);
        return expired.size();
    }

    private void expire(ExportJob job) {
        for (String p : new String[] {job.filePath(), job.manifestPath()}) {
            if (p != null) {
                try {
                    Files.deleteIfExists(Path.of(p));
                } catch (IOException e) {
                    LOG.warnf("Cannot delete the expired export file %s: %s", p, e.getMessage());
                }
            }
        }
        jobs.update(job.expired());
    }

    /** Used by tests: waits for a job to leave the queue. */
    ExportJob waitUntilFinished(UUID userId, UUID id, Duration timeout) throws InterruptedException {
        long end = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < end) {
            ExportJob j = get(userId, id);
            if (!ExportJobRepository.QUEUED.equals(j.state()) && !ExportJobRepository.RUNNING.equals(j.state())) {
                return j;
            }
            Thread.sleep(100);
        }
        return get(userId, id);
    }
}
