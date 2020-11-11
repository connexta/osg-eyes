package com.connexta.osgeyes;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.maven.index.ArtifactInfo;
import org.apache.maven.index.ArtifactInfoFilter;
import org.apache.maven.index.ArtifactInfoGroup;
import org.apache.maven.index.ArtifactScanningListener;
import org.apache.maven.index.DefaultScannerListener;
import org.apache.maven.index.Field;
import org.apache.maven.index.FlatSearchRequest;
import org.apache.maven.index.FlatSearchResponse;
import org.apache.maven.index.GroupedSearchRequest;
import org.apache.maven.index.GroupedSearchResponse;
import org.apache.maven.index.Grouping;
import org.apache.maven.index.Indexer;
import org.apache.maven.index.IndexerEngine;
import org.apache.maven.index.IteratorSearchRequest;
import org.apache.maven.index.IteratorSearchResponse;
import org.apache.maven.index.MAVEN;
import org.apache.maven.index.Scanner;
import org.apache.maven.index.ScanningRequest;
import org.apache.maven.index.ScanningResult;
import org.apache.maven.index.context.ExistingLuceneIndexMismatchException;
import org.apache.maven.index.context.IndexCreator;
import org.apache.maven.index.context.IndexingContext;
import org.apache.maven.index.expr.SourcedSearchExpression;
import org.apache.maven.index.expr.UserInputSearchExpression;
import org.apache.maven.index.search.grouping.GAGrouping;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.util.StringUtils;
import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionScheme;

public class IndexingExample implements Callable<Void>, Closeable {

  private static final String PROP_WORKING_DIR = System.getProperty("user.dir");

  private static final String PROP_USER_HOME = System.getProperty("user.home");

  private static final String PROP_USER_REPO = System.getProperty("user.repo");

  private static final String INDEX_DIR_NAME = ".index";

  private static final String MIN_INDEX_CREATOR_ID = "min";

  private static final String OSGI_INDEX_CREATOR_ID = "osgi-metadatas";

  private final PlexusContainer plexusContainer;

  private final Indexer indexer;

  private final IndexerEngine indexerEngine;

  private final Scanner repositoryScanner;

  private final BufferedReader consoleIn;

  public static void main(String[] args) throws Exception {
    try (final IndexingExample app = new IndexingExample()) {
      app.call();
    }
  }

  @Override
  public void close() throws IOException {
    consoleIn.close();
  }

  public IndexingExample() throws PlexusContainerException, ComponentLookupException {
    // Create a Plexus container, the Maven default IoC container
    // Note that maven-indexer is a Plexus component
    final DefaultContainerConfiguration config = new DefaultContainerConfiguration();
    config.setClassPathScanning(PlexusConstants.SCANNING_INDEX);

    this.plexusContainer = new DefaultPlexusContainer(config);
    this.indexer = plexusContainer.lookup(Indexer.class);
    this.indexerEngine = plexusContainer.lookup(IndexerEngine.class);
    this.repositoryScanner = plexusContainer.lookup(Scanner.class);
    this.consoleIn = new BufferedReader(new InputStreamReader(System.in));

    plexusContainer.addComponent(
        new MvnHierarchyIndexCreator(), IndexCreator.class, MvnHierarchyIndexCreator.ID);
  }

  @Nullable
  private static Path getUserSpecifiedRepoLocation() {
    if (PROP_USER_REPO == null) {
      return null;
    }

    final Path userRepo = Paths.get(PROP_USER_REPO);
    if (userRepo.isAbsolute()) {
      final File userRepoFile = userRepo.toFile();
      if (userRepoFile.exists() && userRepoFile.isDirectory()) {
        return userRepo;
      }
    } else {
      final Path workingDir = Paths.get(PROP_WORKING_DIR);
      final Path userRepoResolved = workingDir.resolve(userRepo);
      final File userRepoResolvedFile = userRepoResolved.toFile();
      if (userRepoResolvedFile.exists() && userRepoResolvedFile.isDirectory()) {
        return userRepoResolved;
      }
    }

    return null;
  }

  private static Path getRepoLocation() {
    // Set by the JVM - should not be null
    assert PROP_WORKING_DIR != null && !PROP_WORKING_DIR.isEmpty();
    assert PROP_USER_HOME != null && !PROP_USER_HOME.isEmpty();

    final Path userRepo = getUserSpecifiedRepoLocation();
    if (userRepo != null) {
      return userRepo;
    }

    final Path defaultMavenM2 = Paths.get(PROP_USER_HOME).resolve(".m2").resolve("repository");
    final File defaultMavenM2File = defaultMavenM2.toFile();
    if (defaultMavenM2File.exists() && defaultMavenM2File.isDirectory()) {
      return defaultMavenM2;
    }

    String message =
        "No valid indexing target could be acquired"
            + System.lineSeparator()
            + "  JVM working directory: "
            + PROP_WORKING_DIR
            + System.lineSeparator()
            + "  User home directory: "
            + PROP_USER_HOME
            + System.lineSeparator()
            + "  User repo directory: "
            + PROP_USER_REPO;

    throw new IllegalStateException(message);
  }

  @Override
  public Void call() throws Exception {
    logline("------------------------------------------------------------------------------------");
    logline("OSG-Eyes Maven Indexer");
    logline("------------------------------------------------------------------------------------");

    final Path repoLocation = getRepoLocation();

    logline("JVM working directory: " + PROP_WORKING_DIR);
    logline("User home directory: " + PROP_USER_HOME);
    logline("User repo directory: " + repoLocation);

    logline("Registered index creators:");
    plexusContainer
        .getComponentDescriptorList(IndexCreator.class, null)
        .forEach(cd -> logline("  " + cd.getImplementation()));

    IndexingContext indexingContext = null;
    try {
      indexingContext = indexTryCreate(repoLocation);
      // Will revisit incremental updates later
      // remoteIndexUpdate(indexingContext);
      waitForUserToContinue();

      // No need to list artifacts right now
      // listAllArtifacts(indexingContext);
      // waitForUserToContinue();

      search(
          indexingContext,
          VersionRangeFilter.atMinimum("2.19.0").butStrictlyLessThan("2.20.0"),
          Criteria.of(MAVEN.GROUP_ID, "ddf"),
          Criteria.of(MAVEN.ARTIFACT_ID, "ddf"),
          Criteria.of(MAVEN.PACKAGING, "pom"));
      waitForUserToContinue();

      search(
          indexingContext,
          Criteria.of(MAVEN.GROUP_ID, "ddf"),
          Criteria.of(MAVEN.ARTIFACT_ID, "ddf"),
          Criteria.of(MAVEN.PACKAGING, "pom"),
          Criteria.of(MAVEN.VERSION, "2.19.5"));
      waitForUserToContinue();

      search(
          indexingContext,
          // Criteria.of(MAVEN.VERSION, "2.19.5"),
          // Criteria.of(MvnOntology.POM_MODULES, "catalog"),
          Criteria.of(MAVEN.ARTIFACT_ID, "catalog"),
          Criteria.of(MvnOntology.POM_PARENT, "mvn:ddf/ddf/2.19.5"));
      waitForUserToContinue();

      // OSGI Attributes are using an unsupported indexing model
      // --
      // search(indexingContext, Criteria.of(OSGI.IMPORT_PACKAGE, "ddf.catalog.validation*"));
      // waitForUserToContinue();

      // Sample grouped search
      // --
      // searchGroupedMavenPlugins(indexingContext);
      // waitForUserToContinue();

    } finally {
      if (indexingContext != null) {
        logline("Closing indexing context...");
        indexer.closeIndexingContext(indexingContext, false);
        logline("...done!");
      }
    }

    logline("Shutting down");
    return null;
  }

  private void searchGroupedMavenPlugins(IndexingContext indexingContext) throws IOException {
    searchGrouped(
        indexingContext,
        new GAGrouping(),
        Criteria.of(MAVEN.GROUP_ID, "org.apache.maven.plugins"),
        Criteria.of(MAVEN.PACKAGING, "maven-plugin"));
    waitForUserToContinue();
  }

  private void searchMoreGuava(IndexingContext indexingContext)
      throws InvalidVersionSpecificationException, IOException {

    // CASES TO VERIFY
    // - VersionRangeFilter cases (max, min, and variations on bounds, inclusive/exclusive)
    // - Criteria.of(MAVEN.PACKAGING, "jar")
    // - Criteria.of(MAVEN.CLASSIFIER, Field.NOT_PRESENT).with(Occur.MUST_NOT)
    // - Criteria.of(MAVEN.CLASSIFIER, "*").with(Occur.MUST_NOT)
    // ETC
    // - How grouped searches behave
    // - Differences between iterator and flat searches

    search(
        indexingContext,
        Criteria.of(MAVEN.GROUP_ID, "com.google.guava"),
        Criteria.of(MAVEN.ARTIFACT_ID, "guava"),
        Criteria.of(MAVEN.PACKAGING, "bundle"),
        Criteria.of(MAVEN.CLASSIFIER, "*").with(Occur.MUST_NOT));

    search(
        indexingContext,
        Criteria.of(MAVEN.GROUP_ID, "com.google.guava"),
        Criteria.of(MAVEN.ARTIFACT_ID, "guava"),
        Criteria.of(MAVEN.PACKAGING, "bundle"),
        Criteria.of(MAVEN.CLASSIFIER, "*").with(Occur.MUST_NOT));

    // Expected: [20.0, 27.0.1]
    search(
        indexingContext,
        VersionRangeFilter.atMinimum("20.0"),
        Criteria.of(MAVEN.GROUP_ID, "com.google.guava"),
        Criteria.of(MAVEN.ARTIFACT_ID, "guava"));

    // Expected: [14.0.1, 19.0]
    search(
        indexingContext,
        VersionRangeFilter.atMinimum("14.0").butStrictlyLessThan("20.0"),
        Criteria.of(MAVEN.GROUP_ID, "com.google.guava"),
        Criteria.of(MAVEN.ARTIFACT_ID, "guava"));

    // Expected: [19.0, 20.0]
    search(
        indexingContext,
        VersionRangeFilter.atMaximum("20.0").butStrictlyGreaterThan("14.0.1"),
        Criteria.of(MAVEN.GROUP_ID, "com.google.guava"),
        Criteria.of(MAVEN.ARTIFACT_ID, "guava"));

    search(indexingContext, Criteria.of(MAVEN.SHA1, "89507701249388").partialInput());

    waitForUserToContinue();
  }

  private IndexingContext indexTryCreate(Path repoLocation)
      throws IOException, ComponentLookupException {
    final Path indexLocation = repoLocation.resolve(INDEX_DIR_NAME);
    final File indexLocationDir = indexLocation.toFile();
    final String[] indexDirContents = indexLocationDir.list();

    final File repoLocationDir = repoLocation.toFile();
    final List<IndexCreator> indexers = new ArrayList<>();

    indexers.add(plexusContainer.lookup(IndexCreator.class, MIN_INDEX_CREATOR_ID));
    indexers.add(plexusContainer.lookup(IndexCreator.class, OSGI_INDEX_CREATOR_ID));
    indexers.add(plexusContainer.lookup(IndexCreator.class, MvnHierarchyIndexCreator.ID));

    final Supplier<IndexingContext> contextSupplier =
        () -> {
          try {
            return indexer.createIndexingContext(
                "localhost-osgeyes",
                "localhost",
                repoLocationDir,
                indexLocationDir,
                // Could supply repoUrl here if you wanted to proxy
                // "http://localhost:8000/"
                null,
                null,
                true,
                true,
                indexers);
          } catch (ExistingLuceneIndexMismatchException em) {
            throw new IllegalStateException(em);
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        };

    // Crude way to account for '.DS_Store' by checking for > 1 instead of > 0
    if (indexLocationDir.exists()
        && indexLocationDir.isDirectory()
        && indexDirContents != null
        && indexDirContents.length > 1) {
      logline("Index found: " + indexLocationDir);
      return contextSupplier.get();
    }

    if (indexLocationDir.exists()) {
      if (indexLocationDir.isDirectory()) {
        try (final Stream<Path> paths = Files.walk(indexLocation)) {
          paths
              .sorted(Comparator.reverseOrder())
              .forEach(
                  f -> {
                    try {
                      Files.delete(f);
                    } catch (IOException e) {
                      throw new UncheckedIOException(e);
                    }
                  });
        }
      } else {
        Files.delete(indexLocation);
      }
    }

    Files.createDirectories(indexLocation);

    final IndexingContext indexingContext = contextSupplier.get();
    logline("Creating index for repository at " + indexingContext.getRepository());
    logline("Creating index at " + indexLocationDir);

    final ArtifactScanningListener listener =
        new DefaultScannerListener(indexingContext, indexerEngine, false, null);
    final ScanningRequest scanningRequest = new ScanningRequest(indexingContext, listener);
    final ScanningResult result = repositoryScanner.scan(scanningRequest);

    logline("Scan has finished");
    logline("Total files: " + result.getTotalFiles());
    logline("Total deleted: " + result.getDeletedFiles());

    if (!result.getExceptions().isEmpty()) {
      logline("Some problems occurred during the scan:");
      result.getExceptions().forEach(Exception::printStackTrace);
    }

    return indexingContext;
  }

  private void search(
      IndexingContext indexingContext, ArtifactInfoFilter filter, Criteria... criteria)
      throws IOException {
    final Query query = getQuery(criteria);
    logline("Searching for " + criteriaToString(criteria));

    final IteratorSearchResponse response =
        indexer.searchIterator(
            new IteratorSearchRequest(query, Collections.singletonList(indexingContext), filter));

    for (ArtifactInfo artifact : response.getResults()) {
      logline(artifact.toString());
      artifact.getAttributes().forEach((k, v) -> logline("  [ " + k + " " + v + " ]"));
    }

    logline("------------");
    logline("Total: " + response.getTotalHitsCount());
    logline();
  }

  private void search(IndexingContext indexingContext, Criteria... criteria) throws IOException {
    final Query query = getQuery(criteria);
    logline("Searching for " + criteriaToString(criteria));

    final FlatSearchResponse response =
        indexer.searchFlat(new FlatSearchRequest(query, indexingContext));

    for (ArtifactInfo artifact : response.getResults()) {
      logline(artifact.toString());
      artifact.getAttributes().forEach((k, v) -> logline("  [ " + k + " " + v + " ]"));
    }

    logline("------------");
    logline("Total: " + response.getTotalHitsCount());
    logline();
  }

  private void searchGrouped(
      IndexingContext indexingContext, Grouping grouping, Criteria... criteria) throws IOException {
    final int maxArtifactDescriptionStringWidth = 60;
    final Query query = getQuery(criteria);
    logline("Searching (grouped) for " + criteriaToString(criteria));

    final GroupedSearchResponse response =
        indexer.searchGrouped(new GroupedSearchRequest(query, grouping, indexingContext));

    for (Map.Entry<String, ArtifactInfoGroup> entry : response.getResults().entrySet()) {
      final ArtifactInfo artifact = entry.getValue().getArtifactInfos().iterator().next();
      logline("Entry: " + artifact);
      logline("Latest version:  " + artifact.getVersion());
      logline(
          StringUtils.isBlank(artifact.getDescription())
              ? "No description in plugin's POM."
              : StringUtils.abbreviate(
                  artifact.getDescription(), maxArtifactDescriptionStringWidth));
      artifact.getAttributes().forEach((k, v) -> logline("  [ " + k + " " + v + " ]"));
      logline();
    }

    logline("------------");
    logline("Total record hits: " + response.getTotalHitsCount());
    logline();
  }

  private Query getQuery(Criteria... criteria) {
    final List<Criteria> criteriaList = Arrays.asList(criteria);
    if (criteriaList.isEmpty()) {
      throw new IllegalArgumentException("No search criteria specified");
    }

    if (criteriaList.size() == 1) {
      final Criteria c = criteriaList.get(0);
      return criteriaToQuery(c);
    }

    final BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();
    criteriaList.forEach(c -> queryBuilder.add(criteriaToQuery(c), c.occurrancePolicy));
    return queryBuilder.build();
  }

  private Query criteriaToQuery(Criteria c) {
    return indexer.constructQuery(
        c.field,
        c.exact ? new SourcedSearchExpression(c.value) : new UserInputSearchExpression(c.value));
  }

  private String criteriaToString(Criteria... criteria) {
    return "["
        + Arrays.stream(criteria).map(Criteria::toString).collect(Collectors.joining(", "))
        + "]";
  }

  private static class Criteria {

    private final Field field;

    private final String value;

    private Occur occurrancePolicy = Occur.MUST;

    private boolean exact = true;

    private Criteria(Field field, String value) {
      this.field = field;
      this.value = value;
    }

    public Criteria partialInput() {
      this.exact = false;
      return this;
    }

    public Criteria with(Occur occurrancePolicy) {
      this.occurrancePolicy = occurrancePolicy;
      return this;
    }

    public static Criteria of(Field field, String value) {
      return new Criteria(field, value);
    }

    @Override
    public String toString() {
      return String.format("(%s %s %s)", field.getFieldName(), getOperator(), value);
    }

    private String getOperator() {
      if (exact) {
        switch (occurrancePolicy) {
          case MUST:
          case FILTER:
            return "MUST MATCH";
          case SHOULD:
            return "SHOULD MATCH";
          case MUST_NOT:
            return "MUST NOT MATCH";
        }
      } else {
        switch (occurrancePolicy) {
          case MUST:
          case FILTER:
            return "MUST START WITH";
          case SHOULD:
            return "SHOULD START WITH";
          case MUST_NOT:
            return "MUST NOT START WITH";
        }
      }
      throw new IllegalStateException(
          String.format(
              "Unexpected combination of values, exact = '%s' and occurrance = '%s'",
              exact, occurrancePolicy));
    }
  }

  private static class VersionRangeFilter implements ArtifactInfoFilter {

    private final VersionScheme versionScheme = new GenericVersionScheme();

    // Nullable
    private final Version minVer;

    // Nullable
    private final Version maxVer;

    private final boolean exclusiveMin;

    private final boolean exclusiveMax;

    public static VersionRangeFilter atMinimum(String version)
        throws InvalidVersionSpecificationException {
      checkVersionString(version);
      return new VersionRangeFilter(version, null, false, false);
    }

    public static VersionRangeFilter atMaximum(String version)
        throws InvalidVersionSpecificationException {
      checkVersionString(version);
      return new VersionRangeFilter(null, version, false, false);
    }

    private VersionRangeFilter(
        String minVer, String maxVer, boolean exclusiveMin, boolean exclusiveMax)
        throws InvalidVersionSpecificationException {
      this.minVer = minVer == null ? null : versionScheme.parseVersion(minVer);
      this.maxVer = maxVer == null ? null : versionScheme.parseVersion(maxVer);
      this.exclusiveMin = exclusiveMin;
      this.exclusiveMax = exclusiveMax;
    }

    private VersionRangeFilter(
        Version minVer, Version maxVer, boolean exclusiveMin, boolean exclusiveMax) {
      this.minVer = minVer;
      this.maxVer = maxVer;
      this.exclusiveMin = exclusiveMin;
      this.exclusiveMax = exclusiveMax;
    }

    public VersionRangeFilter butStrictlyLessThan(String version)
        throws InvalidVersionSpecificationException {
      checkVersionString(version);
      return new VersionRangeFilter(
          this.minVer, versionScheme.parseVersion(version), this.exclusiveMin, true);
    }

    public VersionRangeFilter butStrictlyGreaterThan(String version)
        throws InvalidVersionSpecificationException {
      checkVersionString(version);
      return new VersionRangeFilter(
          versionScheme.parseVersion(version), this.maxVer, true, this.exclusiveMax);
    }

    @Override
    public boolean accepts(IndexingContext ctx, ArtifactInfo ai) {
      try {
        final Version artifactVersion = versionScheme.parseVersion(ai.getVersion());
        boolean matchesMin;
        if (minVer == null) {
          matchesMin = true;
        } else if (exclusiveMin) {
          matchesMin = artifactVersion.compareTo(minVer) > 0;
        } else {
          matchesMin = artifactVersion.compareTo(minVer) >= 0;
        }
        boolean matchesMax;
        if (maxVer == null) {
          matchesMax = true;
        } else if (exclusiveMax) {
          matchesMax = artifactVersion.compareTo(maxVer) < 0;
        } else {
          matchesMax = artifactVersion.compareTo(maxVer) <= 0;
        }
        return matchesMin && matchesMax;
      } catch (InvalidVersionSpecificationException e) {
        // Do something here? Be safe and include?
        return true;
      }
    }

    private static void checkVersionString(String version) {
      if (version == null || version.isEmpty()) {
        throw new IllegalArgumentException("Cannot supply a null or empty version");
      }
    }
  }

  private static void logline() {
    System.out.println();
  }

  private static void logline(String log) {
    System.out.println(log);
  }

  private void waitForUserToContinue() throws IOException {
    logline();
    logline("Press ENTER to continue");
    final String nextLine = consoleIn.readLine();
    if (!nextLine.trim().isEmpty()) {
      logline("Note: STDIN is being ignored");
    }
    logline();
    logline();
  }
}
