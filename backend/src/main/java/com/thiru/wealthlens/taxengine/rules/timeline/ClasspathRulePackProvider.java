package com.thiru.wealthlens.taxengine.rules.timeline;

import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.PackJson;
import com.thiru.wealthlens.taxengine.rules.pack.PackAssembler;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.pack.RulePackProvider;
import com.thiru.wealthlens.taxengine.rules.validate.PackValidationException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Serves each tax year compiled from the timeline on the classpath, refused unless it matches that
 * year's lock (D-034, D-054).
 *
 * <p>The lock is the reason this class is more than a compile. A timeline edited without review would
 * otherwise change the law of every year it touches, silently; here a year whose compiled law differs
 * from its lock fails to load, naming each rule that moved.
 *
 * <p>Compiled content is handed to a {@link PackAssembler}; this class has no opinion about what a
 * rule row looks like.
 */
public final class ClasspathRulePackProvider implements RulePackProvider {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final String root;
    private final Set<TaxYearId> years;
    private final PackAssembler assembler;

    /**
     * @param root the classpath directory holding {@code timeline/} and one {@code <year>.lock.json}
     *     per year, e.g. {@code "rulepacks"}
     * @param years the years this provider serves; declared rather than scanned, because a year that
     *     silently fails to appear is worse than one that fails to load
     * @param assembler turns verified content into a pack
     */
    public ClasspathRulePackProvider(String root, Set<TaxYearId> years, PackAssembler assembler) {
        if (root == null || root.isBlank()) {
            throw new IllegalArgumentException("A classpath pack provider needs a root directory");
        }
        if (years == null || years.isEmpty()) {
            throw new IllegalArgumentException(
                    "A pack provider serving no years cannot compute anything; declare its years");
        }
        if (assembler == null) {
            throw new IllegalArgumentException("A pack provider needs an assembler");
        }
        this.root = root.endsWith("/") ? root.substring(0, root.length() - 1) : root;
        this.years = Set.copyOf(years);
        this.assembler = assembler;
    }

    @Override
    public Set<TaxYearId> availableYears() {
        return years;
    }

    @Override
    public RulePack load(TaxYearId year) {
        if (year == null || !years.contains(year)) {
            throw new PackValidationException(
                    "No pack for " + year + "; this provider serves " + new TreeSet<>(
                            years.stream().map(TaxYearId::label).toList()));
        }
        RulePackLock lock = lock(root, year);
        CompiledPack compiled = RulePackCompiler.compile(timeline(), year);
        lock.verify(compiled);
        return assembler.assemble(year, lock.layers(), compiled.files());
    }

    private Timeline timeline() {
        String resource = root + "/timeline";
        URL url = getClass().getClassLoader().getResource(resource);
        if (url == null) {
            throw new PackValidationException(resource + " is not on the classpath");
        }
        return timelineAt(url);
    }

    /**
     * Reads the timeline at {@code url}: a directory when run from the build's classes, an archive entry
     * when run from the application's JAR (D-210). The Spring Boot loader already has a filesystem open
     * for its own nested JAR; a plain JAR has none, so one is opened for the read and closed after it.
     * Any other location is refused, because it is not a place this application ships from.
     */
    static Timeline timelineAt(URL url) {
        URI uri;
        try {
            uri = url.toURI();
        } catch (URISyntaxException e) {
            throw new PackValidationException("The timeline has an unusable location " + url, e);
        }
        return switch (url.getProtocol()) {
            case "file" -> TimelineReader.read(Path.of(uri));
            case "jar" -> timelineInArchive(uri);
            default -> throw new PackValidationException("The timeline is at " + url + "; it is read only from a"
                    + " directory or a JAR on the classpath");
        };
    }

    private static Timeline timelineInArchive(URI uri) {
        try {
            return TimelineReader.read(Path.of(uri));
        } catch (FileSystemNotFoundException notOpen) {
            try (FileSystem archive = FileSystems.newFileSystem(uri, Map.of())) {
                return TimelineReader.read(archive.provider().getPath(uri));
            } catch (IOException e) {
                throw new UncheckedIOException("Could not open the archive holding the timeline at " + uri, e);
            }
        }
    }

    /**
     * The tax years shipped under the classpath directory {@code root}, as its {@code shipped-years.json} declares
     * them: declared rather than scanned, because a year that silently fails to appear is worse than one that fails
     * to load (D-210, D-268). Each is written as its label, as the locks name it.
     */
    public static Set<TaxYearId> shippedYears(String root) {
        String path = root + "/shipped-years.json";
        JsonNode manifest;
        try {
            manifest = MAPPER.readTree(read(path));
        } catch (JacksonException e) {
            throw new PackValidationException(path + ": not well-formed JSON — " + e.getOriginalMessage(), e);
        }
        Set<TaxYearId> years = new LinkedHashSet<>();
        for (PackJson.Element label : PackJson.array(manifest, "taxYears", path, element -> element)) {
            TaxYearId year = taxYear(label.node().asString(), label.path());
            if (!years.add(year)) {
                throw new PackValidationException(label.path() + ": " + year.label() + " is listed twice");
            }
        }
        return Set.copyOf(years);
    }

    /** The year a label names, refused unless the label is that year's own ("2026-27"). */
    private static TaxYearId taxYear(String label, String path) {
        if (label.length() < 4 || !label.substring(0, 4).chars().allMatch(Character::isDigit)) {
            throw new PackValidationException(path + ": \"" + label + "\" is not a tax year");
        }
        TaxYearId year = TaxYearId.of(Integer.parseInt(label.substring(0, 4)));
        if (!year.label().equals(label)) {
            throw new PackValidationException(path + ": \"" + label + "\" is not a tax year; did you mean "
                    + year.label() + "?");
        }
        return year;
    }

    /** The lock for {@code year} under the classpath directory {@code root}. */
    static RulePackLock lock(String root, TaxYearId year) {
        String lockPath = root + "/" + year.label() + ".lock.json";
        return RulePackLock.read(year, read(lockPath), lockPath);
    }

    private static String read(String resourcePath) {
        try (InputStream in = ClasspathRulePackProvider.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new PackValidationException(resourcePath + " is not on the classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + resourcePath, e);
        }
    }
}
