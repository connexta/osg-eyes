package com.connexta.osgeyes;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.Bits;
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
import org.apache.maven.index.context.IndexCreator;
import org.apache.maven.index.context.IndexUtils;
import org.apache.maven.index.context.IndexingContext;
import org.apache.maven.index.expr.SourcedSearchExpression;
import org.apache.maven.index.expr.UserInputSearchExpression;
import org.apache.maven.index.search.grouping.GAGrouping;
import org.apache.maven.index.updater.IndexUpdateRequest;
import org.apache.maven.index.updater.IndexUpdateResult;
import org.apache.maven.index.updater.IndexUpdater;
import org.apache.maven.index.updater.ResourceFetcher;
import org.apache.maven.index.updater.WagonHelper;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.events.TransferEvent;
import org.apache.maven.wagon.observers.AbstractTransferListener;
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

public class IndexingExample implements Callable<Void> {

  private final PlexusContainer plexusContainer;

  private final Indexer indexer;

  private final IndexerEngine indexerEngine;

  private final Scanner repositoryScanner;

  private final IndexUpdater indexUpdater;

  private final Wagon httpWagon;

  public static void main(String[] args) throws Exception {
    new IndexingExample().call();
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
    this.indexUpdater = plexusContainer.lookup(IndexUpdater.class);
    this.httpWagon = plexusContainer.lookup(Wagon.class, "http");
  }

  @Override
  public Void call() throws Exception {
    final String repoUrl = "http://localhost:8000/";
    final File repoLocation = new File("/cx/repos/connexta/osg-eyes-m2");
    final File repoIndexLocation = new File("/cx/repos/connexta/osg-eyes-m2-index");

    final List<IndexCreator> indexers = new ArrayList<>();
    indexers.add(plexusContainer.lookup(IndexCreator.class, "min"));

    final IndexingContext indexingContext =
        indexer.createIndexingContext(
            "localhost-osgeyes",
            "localhost",
            repoLocation,
            repoIndexLocation,
            // Could supply repoUrl here if you wanted to proxy
            null,
            null,
            true,
            true,
            indexers);

    indexCreate(indexingContext);
    //    remoteIndexUpdate(indexingContext);
    waitForUserToContinue();

    listAllArtifacts(indexingContext);
    waitForUserToContinue();

    exampleVersionSearch(indexingContext);
    waitForUserToContinue();

    BooleanQuery bq;

    logline("Searching for all artifacts under GA org.apache.maven.indexer:indexer-artifact");
    bq =
        new BooleanQuery.Builder()
            .add(
                indexer.constructQuery(
                    MAVEN.GROUP_ID, new SourcedSearchExpression("org.apache.maven.indexer")),
                Occur.MUST)
            .add(
                indexer.constructQuery(
                    MAVEN.ARTIFACT_ID, new SourcedSearchExpression("indexer-artifact")),
                Occur.MUST)
            .build();
    search(indexingContext, bq);
    waitForUserToContinue();

    logline("Searching for main artifacts under GA org.apache.maven.indexer:indexer-artifact");
    bq =
        new BooleanQuery.Builder()
            .add(
                indexer.constructQuery(
                    MAVEN.GROUP_ID, new SourcedSearchExpression("org.apache.maven.indexer")),
                Occur.MUST)
            .add(
                indexer.constructQuery(
                    MAVEN.ARTIFACT_ID, new SourcedSearchExpression("indexer-artifact")),
                Occur.MUST)
            .add(
                indexer.constructQuery(MAVEN.CLASSIFIER, new SourcedSearchExpression("*")),
                Occur.MUST_NOT)
            .build();
    search(indexingContext, bq);
    waitForUserToContinue();

    logline("Searching for SHA1 7ab67e6b20e5332a7fb4fdf2f019aec4275846c2");
    search(
        indexingContext,
        indexer.constructQuery(
            MAVEN.SHA1, new SourcedSearchExpression("7ab67e6b20e5332a7fb4fdf2f019aec4275846c2")));
    waitForUserToContinue();

    logline("Searching for SHA1 7ab67e6b20 (partial hash)");
    search(
        indexingContext,
        indexer.constructQuery(MAVEN.SHA1, new UserInputSearchExpression("7ab67e6b20")));
    waitForUserToContinue();

    logline(
        "Searching for classname DefaultNexusIndexer (note: Central does not publish classes in the index)");
    search(
        indexingContext,
        indexer.constructQuery(
            MAVEN.CLASSNAMES, new UserInputSearchExpression("DefaultNexusIndexer")));
    waitForUserToContinue();

    logline("Searching for all \"canonical\" maven plugins and their latest versions");
    bq =
        new BooleanQuery.Builder()
            .add(
                indexer.constructQuery(
                    MAVEN.PACKAGING, new SourcedSearchExpression("maven-plugin")),
                Occur.MUST)
            .add(
                indexer.constructQuery(
                    MAVEN.GROUP_ID, new SourcedSearchExpression("org.apache.maven.plugins")),
                Occur.MUST)
            .build();
    searchGrouped(indexingContext, bq, new GAGrouping());
    waitForUserToContinue();

    logline("Searching for all maven archetypes (latest versions)");
    searchGrouped(
        indexingContext,
        indexer.constructQuery(MAVEN.PACKAGING, new SourcedSearchExpression("maven-archetype")),
        new GAGrouping());
    waitForUserToContinue();

    logline("Closing indexing context...");
    indexer.closeIndexingContext(indexingContext, false);
    logline("...done!");

    logline("Shutting down");
    return null;
  }

  private void indexCreate(IndexingContext indexingContext) {
    logline("Creating index for repository at " + indexingContext.getRepository());
    logline("Creating index at " + indexingContext.getIndexDirectoryFile());

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
  }

  // Won't work for local repo, use scanner instead
  private void remoteIndexUpdate(IndexingContext indexingContext) throws IOException {
    // Create ResourceFetcher implementation to be used with IndexUpdateRequest
    // Here, we use Wagon based one as shorthand, but all we need is a ResourceFetcher
    // implementation
    final ResourceFetcher resourceFetcher =
        //  new DefaultIndexUpdater.FileFetcher(indexingContext.getRepository());
        new WagonHelper.WagonFetcher(httpWagon, new LoggingTransferListener(), null, null);

    logline("Updating Index...");
    logline("This might take a while on first run, so please be patient");

    Date contextCurrentTimestamp = indexingContext.getTimestamp();
    IndexUpdateRequest updateRequest = new IndexUpdateRequest(indexingContext, resourceFetcher);
    IndexUpdateResult updateResult = indexUpdater.fetchAndUpdateIndex(updateRequest);

    logline("Using index " + indexingContext.getId());
    if (updateResult.isFullUpdate()) {
      logline("Full update happened");
    } else if (updateResult.getTimestamp().equals(contextCurrentTimestamp)) {
      logline("No update needed, index is up to date");
    } else {
      logline(
          "Incremental update happened, change covered "
              + contextCurrentTimestamp
              + " - "
              + updateResult.getTimestamp()
              + " period.");
    }
  }

  private void listAllArtifacts(IndexingContext indexingContext) throws IOException {
    final IndexSearcher searcher = indexingContext.acquireIndexSearcher();
    try {
      final IndexReader reader = searcher.getIndexReader();
      final Bits liveDocs = MultiFields.getLiveDocs(reader);
      for (int i = 0; i < reader.maxDoc(); i++) {
        if (liveDocs == null || liveDocs.get(i)) {
          final Document doc = reader.document(i);
          final ArtifactInfo artifact = IndexUtils.constructArtifactInfo(doc, indexingContext);
          if (artifact == null) {
            logline(
                "Couldn't transform document to artifact with indexed fields: "
                    + doc.getFields().stream()
                        .map(IndexableField::name)
                        .collect(Collectors.joining(",")));
            continue;
          }
          logline(
              artifact.getGroupId()
                  + ":"
                  + artifact.getArtifactId()
                  + ":"
                  + artifact.getVersion()
                  + ":"
                  + artifact.getClassifier()
                  + " (sha1="
                  + artifact.getSha1()
                  + ")");
        }
      }
    } finally {
      indexingContext.releaseIndexSearcher(searcher);
    }
  }

  private void exampleVersionSearch(IndexingContext indexingContext)
      throws IOException, InvalidVersionSpecificationException {
    final GenericVersionScheme versionScheme = new GenericVersionScheme();
    final String versionString = "1.5.0";
    final Version version = versionScheme.parseVersion(versionString);

    // Construct the query for known GA
    final Query groupIdQ =
        indexer.constructQuery(MAVEN.GROUP_ID, new SourcedSearchExpression("org.sonatype.nexus"));
    final Query artifactIdQ =
        indexer.constructQuery(MAVEN.ARTIFACT_ID, new SourcedSearchExpression("nexus-api"));

    final BooleanQuery query =
        new BooleanQuery.Builder()
            .add(groupIdQ, Occur.MUST)
            .add(artifactIdQ, Occur.MUST)
            // Get "jar" artifacts only
            .add(
                indexer.constructQuery(MAVEN.PACKAGING, new SourcedSearchExpression("jar")),
                Occur.MUST)
            // Get main artifacts only (no classifier)
            // Note: this below is unfinished API, needs fixing
            .add(
                indexer.constructQuery(
                    MAVEN.CLASSIFIER, new SourcedSearchExpression(Field.NOT_PRESENT)),
                Occur.MUST_NOT)
            .build();

    // Construct the filter to express "V greater than"
    final ArtifactInfoFilter versionFilter =
        (ctx, ai) -> {
          try {
            final Version artVer = versionScheme.parseVersion(ai.getVersion());
            return artVer.compareTo(version) > 0; // Use ">=" if INCLUSIVE behavior is preferred
          } catch (InvalidVersionSpecificationException e) {
            // Do something here? Be safe and include?
            return true;
          }
        };

    logline(
        "Searching for all GAVs with G=org.sonatype.nexus and nexus-api and having V greater than 1.5.0");
    final IteratorSearchRequest request =
        new IteratorSearchRequest(query, Collections.singletonList(indexingContext), versionFilter);
    final IteratorSearchResponse response = indexer.searchIterator(request);
    for (ArtifactInfo artifact : response) {
      logline(artifact.toString());
    }
  }

  private void search(IndexingContext indexingContext, Query query) throws IOException {
    logline("Searching for " + query.toString());
    final FlatSearchResponse response =
        indexer.searchFlat(new FlatSearchRequest(query, indexingContext));
    for (ArtifactInfo artifact : response.getResults()) {
      logline(artifact.toString());
    }
    logline("------------");
    logline("Total: " + response.getTotalHitsCount());
    logline();
  }

  private void searchGrouped(IndexingContext indexingContext, Query query, Grouping grouping)
      throws IOException {
    final int maxArtifactDescriptionStringWidth = 60;
    logline("Searching (grouped) for " + query.toString());

    final GroupedSearchResponse response =
        indexer.searchGrouped(new GroupedSearchRequest(query, grouping, indexingContext));

    for (Map.Entry<String, ArtifactInfoGroup> entry : response.getResults().entrySet()) {
      final ArtifactInfo artifact = entry.getValue().getArtifactInfos().iterator().next();
      logline("* Entry " + artifact);
      logline("  Latest version:  " + artifact.getVersion());
      logline(
          StringUtils.isBlank(artifact.getDescription())
              ? "No description in plugin's POM."
              : StringUtils.abbreviate(
                  artifact.getDescription(), maxArtifactDescriptionStringWidth));
      logline();
    }

    logline("------------");
    logline("Total record hits: " + response.getTotalHitsCount());
    logline();
  }

  private static void logline() {
    System.out.println();
  }

  private static void logline(String log) {
    System.out.println(log);
  }

  private static void waitForUserToContinue() throws IOException {
    logline();
    logline("Press ENTER to continue");
    final int next = System.in.read();
    if (next > -1) {
      logline("Note: STDIN is being ignored");
    }
    logline();
    logline();
  }

  private static class LoggingTransferListener extends AbstractTransferListener {
    public void transferStarted(TransferEvent transferEvent) {
      logline("  Downloading " + transferEvent.getResource().getName());
    }

    public void transferProgress(TransferEvent transferEvent, byte[] buffer, int length) {
      // Not currently interested in progress events
    }

    public void transferCompleted(TransferEvent transferEvent) {
      logline(" - Done");
    }
  }
}
