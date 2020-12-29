package com.connexta.osgeyes.index;

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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.Query;
import org.apache.maven.index.ArtifactInfo;
import org.apache.maven.index.ArtifactInfoFilter;
import org.apache.maven.index.ArtifactScanningListener;
import org.apache.maven.index.DefaultScannerListener;
import org.apache.maven.index.FlatSearchRequest;
import org.apache.maven.index.FlatSearchResponse;
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
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;

/**
 * Custom Maven indexing app to provide build data directly to Clojure tooling.
 *
 * <p>(TO DO) Next primary objective is to figure out how this object's {@link Closeable} lifecycle
 * will work within a Clojure namespace.
 *
 * <p>(TO DO) Outstanding mvn-indexer behavior cases to verify:
 *
 * <ul>
 *   <li>VersionRangeFilter cases (max, min, and variations on bounds, inclusive/exclusive)
 *   <li>Criteria.of(MAVEN.PACKAGING, "jar")
 *   <li>Criteria.of(MAVEN.CLASSIFIER, Field.NOT_PRESENT).with(Occur.MUST_NOT)
 *   <li>Criteria.of(MAVEN.CLASSIFIER, "*").with(Occur.MUST_NOT)
 * </ul>
 *
 * <p>(TO DO) Other mvn-indexer behaviors to review:
 *
 * <ul>
 *   <li>How grouped searches behave
 *   <li>Differences between iterator and flat searches
 * </ul>
 */
public class IndexingApp implements Closeable {

  private static final String PROP_WORKING_DIR = System.getProperty("user.dir");

  private static final String PROP_USER_HOME = System.getProperty("user.home");

  private static final String PROP_USER_REPO = System.getProperty("user.repo");

  private static final String INDEX_DIR_NAME = ".index";

  private static final String MIN_INDEX_CREATOR_ID = "min";

  // Using a singleton helps the object cleanly map to a Clojure namespace
  private static IndexingApp INSTANCE = null;

  private final PlexusContainer plexusContainer;

  private final Indexer indexer;

  private final IndexerEngine indexerEngine;

  private final Scanner repositoryScanner;

  // Next up we're going to separate this as part of a standalone app
  // also move output to logger instead of std out
  private final BufferedReader consoleIn;

  private final Criteria criteria;

  // Controlled by object open(...) / close() lifecycle
  private IndexingContext indexingContext = null;

  // Using a singleton helps the object cleanly map to a Clojure namespace
  public static IndexingApp getInstance()
      throws PlexusContainerException, ComponentLookupException {
    if (INSTANCE == null) {
      INSTANCE = new IndexingApp();
      // In the REPL I keep forgetting to call close() so this just helps cover our bases.
      // Although, since we're SIGKILL-ing on REPL close this might not help anyway.
      Runtime.getRuntime().addShutdownHook(new Thread(IndexingApp::indexClosingShutdownHook));
    }

    return INSTANCE;
  }

  private static void indexClosingShutdownHook() {
    if (INSTANCE == null) {
      return;
    }
    try {
      INSTANCE.close();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private IndexingApp() throws PlexusContainerException, ComponentLookupException {
    // Create a Plexus container, the Maven default IoC container
    // Note that maven-indexer is a Plexus component
    final DefaultContainerConfiguration config = new DefaultContainerConfiguration();

    // Needed SCANNING_ON instead of SCANNING_INDEX so other main modules can find components
    config.setClassPathScanning(PlexusConstants.SCANNING_ON);

    this.plexusContainer = new DefaultPlexusContainer(config);
    this.indexer = plexusContainer.lookup(Indexer.class);
    this.indexerEngine = plexusContainer.lookup(IndexerEngine.class);
    this.repositoryScanner = plexusContainer.lookup(Scanner.class);
    this.consoleIn = new BufferedReader(new InputStreamReader(System.in));

    this.criteria = new Criteria(indexer);

    plexusContainer.addComponent(
        new MvnHierarchyIndexCreator(), IndexCreator.class, MvnHierarchyIndexCreator.ID);
    plexusContainer.addComponent(
        new JarManifestIndexCreator(), IndexCreator.class, JarManifestIndexCreator.ID);
    plexusContainer.addComponent(
        new JarPackagesIndexCreator(), IndexCreator.class, JarPackagesIndexCreator.ID);
  }

  /**
   * Opens the indexing context and other resources necessary for querying.
   *
   * @param repoLocation the path of the repository to open.
   * @throws IOException if an error occurs while opening the indexing resources.
   * @throws ComponentLookupException if dependencies cannot be satisfied.
   */
  public void open(Path repoLocation) throws IOException, ComponentLookupException {
    if (indexingContext != null) {
      throw new IllegalStateException(
          "Cannot open indexer, it's already open, " + indexingContext.toString());
    }
    indexingContext = indexTryCreate(repoLocation);
    // Will revisit incremental updates later
    // remoteIndexUpdate(indexingContext);
  }

  /**
   * Closes the indexing app, along with the indexing context and other resources.
   *
   * @throws IOException if an error occurs while closing.
   */
  @Override
  public void close() throws IOException {
    consoleIn.close();
    if (indexingContext != null) {
      logline("Closing indexing context...");
      indexer.closeIndexingContext(indexingContext, false);
      logline("...done!");
      indexingContext = null;
    }
  }

  public static void main(String[] args) throws Exception {
    try (final IndexingApp app = IndexingApp.getInstance()) {
      logline("----------------------------------------------------------------------------------");
      logline("OSG-Eyes Maven Indexer");
      logline("----------------------------------------------------------------------------------");

      final Path repoLocation = getRepoLocation();

      logline("JVM working directory: " + PROP_WORKING_DIR);
      logline("User home directory: " + PROP_USER_HOME);
      logline("User repo directory: " + repoLocation);

      logline("Registered index creators:");
      app.plexusContainer
          .getComponentDescriptorList(IndexCreator.class, null)
          .forEach(cd -> logline("  " + cd.getImplementation()));

      app.open(repoLocation);
      app.waitForUserToContinue();
      logline("Searching for security-core-api");
      app.search(
          IndexingApp.getInstance()
              .criteria
              .of(
                  IndexingApp.getInstance().criteria.of(MAVEN.EXTENSION, "jar"),
                  app.criteria.of(MAVEN.ARTIFACT_ID, "security-core-api"),
                  app.criteria.of(MAVEN.VERSION, "2.19.15")));

      // OSGI Attributes are using an unsupported indexing model
      // --
      // search(indexingContext, Criteria.of(OSGI.IMPORT_PACKAGE, "ddf.catalog.validation*"));
      // waitForUserToContinue();

      // Sample grouped search
      // --
      // searchGroupedMavenPlugins(indexingContext);
      // waitForUserToContinue();

      logline("Searching jar packages...");
      app.search(
          app.criteria.of(
              app.criteria.of(
                  MvnOntology.JAR_PACKAGES,
                  "mil.nga.gsr*",
                  app.criteria.getOptions().partialInput()),
              app.criteria.of(MAVEN.VERSION, "16*")));

      Collection<ArtifactInfo> results =
          app.gatherHierarchy(MvnCoordinate.newInstance("ddf", "ddf", "2.19.15"));

      logNames(results);
      app.waitForUserToContinue();

      logline("Shutting down");
    }
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

  // Invokable by Clojure
  public static Path getRepoLocation() {
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

  // Clojure friendly wrapper
  public Collection<ArtifactInfo> gatherHierarchy(String groupId, String artifactId, String version)
      throws IOException {
    return gatherHierarchy(MvnCoordinate.newInstance(groupId, artifactId, version));
  }

  /**
   * Returns a sorted set of all artifacts within a maven hierarchy, given the root of that
   * hierarchy. Should accurately mimic traversing a code repository file structure.
   *
   * <p>Currently this search only targets modules with packaging {@code pom} or {@code bundle} but
   * can be evolved to be more flexible in the future.
   *
   * @param root the coordinate of the root node.
   * @return a collection of all terminal artifacts within the hierarchy.
   */
  public Collection<ArtifactInfo> gatherHierarchy(MvnCoordinate root) throws IOException {

    validateContext();
    validateRoot(root);

    /*
    // NOTE - the equals() and hashcode() for ArtifactInfo is wrong so Sets are broken
    //    final Set<ArtifactInfo> totalResults =
    //        new TreeSet<>(
    //            Comparator.comparing(ArtifactInfo::getGroupId)
    //                .thenComparing(ArtifactInfo::getArtifactId)
    //                .thenComparing(ArtifactInfo::getVersion));
    */
    final List<ArtifactInfo> totalResults = new ArrayList<>();

    boolean keepGoing = true;
    List<MvnCoordinate> nextUp = new ArrayList<>();
    nextUp.add(root);

    do {

      final List<IteratorSearchRequest> requests =
          nextUp.stream()
              .map(this::createSubmoduleQuery)
              .map(q -> new IteratorSearchRequest(q, indexingContext))
              .collect(Collectors.toList());

      final List<IteratorSearchResponse> responses = new ArrayList<>(requests.size());
      for (IteratorSearchRequest request : requests) {
        responses.add(indexer.searchIterator(request));
      }

      final List<ArtifactInfo> results =
          responses.stream()
              .map(IteratorSearchResponse::getResults)
              .flatMap(r -> StreamSupport.stream(r.spliterator(), false))
              .collect(Collectors.toList());

      totalResults.addAll(results);

      keepGoing = !responses.isEmpty();
      nextUp =
          results.stream()
              .map(
                  info ->
                      MvnCoordinate.newInstance(
                          info.getGroupId(), info.getArtifactId(), info.getVersion()))
              .collect(Collectors.toList());

    } while (keepGoing);

    totalResults.sort(
        Comparator.comparing(ArtifactInfo::getGroupId)
            .thenComparing(ArtifactInfo::getArtifactId)
            .thenComparing(ArtifactInfo::getVersion));

    return totalResults;
  }

  /**
   * Creates a query that will retrieve modules that specify the provided parent as their {@code
   * <parent/>} in their pom.
   *
   * <p>The original strategy was to be a bit more precise with the query and leverage the {@code
   * <modules/>} block as well, but there is no guarantee that a module string is also the artifact
   * ID. See the discrepancy in DDF for an example.
   *
   * <p><a href="https://github.com/codice/ddf/blob/ddf-2.19.5/pom.xml#L1233">Parent</a> <a
   * href="https://github.com/codice/ddf/blob/ddf-2.19.5/libs/pom.xml#L23">Child</a>
   *
   * <p>It was also intended to include 'jar' but those results were not necessary for this
   * iteration and the artifacts would just have to be filtered down to bundles anyway (for now).
   *
   * @param parent the parent module.
   * @return a query that will yield children of the parent.
   */
  private Query createSubmoduleQuery(MvnCoordinate parent) {
    final Criteria.Queryable queryable =
        criteria.of(
            criteria.of(MvnOntology.POM_PARENT, MvnCoordinate.write(parent)),
            // criteria.of(MAVEN.ARTIFACT_ID, submoduleArtifactId),
            //            criteria.of(
            //                criteria.of(MAVEN.EXTENSION, "pom",
            // criteria.options().with(Occur.SHOULD)),
            //                criteria.of(MAVEN.EXTENSION, "jar",
            // criteria.options().with(Occur.SHOULD))),
            criteria.of(
                criteria.of(MAVEN.PACKAGING, "pom", criteria.options().with(Occur.SHOULD)),
                // criteria.of(MAVEN.PACKAGING, "jar", criteria.options().with(Occur.SHOULD)),
                criteria.of(MAVEN.PACKAGING, "bundle", criteria.options().with(Occur.SHOULD))));
    // logline("Building query " + queryable.toString());
    return queryable.getQuery();
  }

  /**
   * Ensures the provided "root" maven coordinate exists and is suitable for retrieving a hierarchy.
   * Right now only {@code <packaging>pom</packaging>} is supported for hierarchies.
   *
   * @param root target maven artifact to validate.
   * @throws IllegalArgumentException if root is invalid for the purposes of hierarchy retrieval.
   * @throws IOException if any intermediate queries fail.
   */
  private void validateRoot(MvnCoordinate root) throws IOException {
    // TODO - note that we might be able to sub-interface MAVEN with our own (MvnOntology too long)
    final Query rootCriteria =
        criteria
            .of(
                criteria.of(MAVEN.GROUP_ID, root.getGroupId()),
                criteria.of(MAVEN.ARTIFACT_ID, root.getArtifactId()),
                criteria.of(MAVEN.VERSION, root.getVersion()),
                criteria.of(MAVEN.PACKAGING, "pom"))
            .getQuery();

    final FlatSearchResponse rootResponse =
        indexer.searchFlat(new FlatSearchRequest(rootCriteria, indexingContext));

    final Set<ArtifactInfo> rootResults = rootResponse.getResults();
    if (rootResults.size() != 1) {
      throw new IllegalArgumentException("Provided root coordinates did not yield a single result");
    }
  }

  private void validateContext() {
    if (indexingContext == null) {
      throw new IllegalStateException("Cannot perform index operations on an unopened index");
    }
  }

  /**
   * Attempts to create an {@link IndexingContext} for use on a local M2 repository.
   *
   * <p>The default index location will be in {@code ~/.m2/repository/.index/} right alongside the
   * artifacts themselves, unless system property {@code user.repo} is specified to overwrite this.
   *
   * @see #getRepoLocation()
   * @see #getUserSpecifiedRepoLocation()
   * @param repoLocation the location to create the index directory for storing the index.
   * @return an indexing context for the user's local maven repository.
   * @throws IOException if an error occurs during index discovery or initialization.
   * @throws ComponentLookupException if the application container doesn't have requisite
   *     dependencies.
   */
  private IndexingContext indexTryCreate(Path repoLocation)
      throws IOException, ComponentLookupException {
    final Path indexLocation = repoLocation.resolve(INDEX_DIR_NAME);
    final File indexLocationDir = indexLocation.toFile();
    final String[] indexDirContents = indexLocationDir.list();

    final File repoLocationDir = repoLocation.toFile();
    final List<IndexCreator> indexers = new ArrayList<>();

    indexers.add(plexusContainer.lookup(IndexCreator.class, MIN_INDEX_CREATOR_ID));
    indexers.add(plexusContainer.lookup(IndexCreator.class, MvnHierarchyIndexCreator.ID));
    indexers.add(plexusContainer.lookup(IndexCreator.class, JarManifestIndexCreator.ID));
    indexers.add(plexusContainer.lookup(IndexCreator.class, JarPackagesIndexCreator.ID));

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

  private void search(ArtifactInfoFilter filter, Criteria.Queryable criteria) throws IOException {
    validateContext();
    final Query query = criteria.getQuery();
    logline("Searching for " + criteria.toString());

    final IteratorSearchResponse response =
        indexer.searchIterator(
            new IteratorSearchRequest(query, Collections.singletonList(indexingContext), filter));

    logNames(response.getResults());

    logline("------------");
    logline("Total: " + response.getTotalHitsCount());
    logline();
  }

  private void search(Criteria.Queryable criteria) throws IOException {
    final Query query = criteria.getQuery();
    logline("Searching for " + criteria.toString());
    search(query);
  }

  private void search(Query query) throws IOException {
    validateContext();
    final FlatSearchResponse response =
        indexer.searchFlat(new FlatSearchRequest(query, indexingContext));

    //    logall(response.getResults());
    logNamesAndPackages(response.getResults());

    logline("------------");
    logline("Total: " + response.getTotalHitsCount());
    logline();
  }

  private static void logline() {
    System.out.println();
  }

  private static void logline(String log) {
    System.out.println(log);
  }

  private static void logNames(Iterable<ArtifactInfo> artifacts) {
    for (ArtifactInfo artifact : artifacts) {
      logline(artifact.toString());
    }
  }

  private static void logNamesAndPackages(Iterable<ArtifactInfo> artifacts) {
    for (ArtifactInfo artifact : artifacts) {
      logline(artifact.toString());
      String packages = artifact.getAttributes().get(MvnOntology.JAR_PACKAGES.getFieldName());
      if (packages != null) {
        logline("    " + packages.toString());
      }
    }
  }

  private static void logall(Iterable<ArtifactInfo> artifacts) {
    for (ArtifactInfo artifact : artifacts) {
      logline(artifact.toString());
      artifact.getAttributes().forEach((k, v) -> logline("  [ " + k + " " + v + " ]"));
    }
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

  private static class ArtifactInfoFixed extends ArtifactInfo {
    public ArtifactInfoFixed() {}

    public ArtifactInfoFixed(
        String repository,
        String groupId,
        String artifactId,
        String version,
        String classifier,
        String extension) {
      super(repository, groupId, artifactId, version, classifier, extension);
    }
  }
}
