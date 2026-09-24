package com.thiru.wealthlens.taxengine.rules.timeline;

import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.pack.PackAssembler;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.pack.RulePackProvider;
import com.thiru.wealthlens.taxengine.rules.validate.PackValidationException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

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
        String lockPath = root + "/" + year.label() + ".lock.json";
        RulePackLock lock = RulePackLock.read(year, read(lockPath), lockPath);
        CompiledPack compiled = RulePackCompiler.compile(TimelineReader.read(timelineDirectory()), year);
        lock.verify(compiled);
        return assembler.assemble(year, lock.layers(), compiled.files());
    }

    /**
     * The timeline as a directory. Inside a JAR it is not one, and loading it from there is not built:
     * no item owns wiring a provider into the application yet (D-054), so this refuses rather than
     * guess.
     */
    private Path timelineDirectory() {
        String resource = root + "/timeline";
        URL url = getClass().getClassLoader().getResource(resource);
        if (url == null) {
            throw new PackValidationException(resource + " is not on the classpath");
        }
        if (!"file".equals(url.getProtocol())) {
            throw new PackValidationException(resource + " is at " + url + "; loading a timeline from inside a"
                    + " JAR is not built — it has no owner yet (D-054)");
        }
        try {
            return Path.of(url.toURI());
        } catch (URISyntaxException e) {
            throw new PackValidationException(resource + " has an unusable location " + url, e);
        }
    }

    private String read(String resourcePath) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new PackValidationException(resourcePath + " is not on the classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + resourcePath, e);
        }
    }
}
