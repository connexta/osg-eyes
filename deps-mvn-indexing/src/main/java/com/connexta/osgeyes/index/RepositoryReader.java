package com.connexta.osgeyes.index;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.maven.index.ArtifactContext;
import org.apache.maven.index.ArtifactContextProducer;
import org.apache.maven.index.Scanner;
import org.apache.maven.index.ScanningRequest;
import org.apache.maven.index.ScanningResult;
import org.apache.maven.index.context.IndexingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Custom {@link Scanner} added so certain files can be omitted from the indexing process. Longer
 * term this can be broken down into two implementations, one for files on disk and one for
 * traversing files over HTTP (such as a Sonatype Nexus instance).
 *
 * <p>The interface and other components will have to change but as long as processing can be mapped
 * to a sequence of calls to {@link
 * org.apache.maven.index.ArtifactScanningListener#artifactDiscovered(ArtifactContext)} then
 * indexing should proceed like normal.
 *
 * <p>Code in this file adapted from:
 * https://github.com/apache/maven-indexer/blob/maven-indexer-6.0.0/indexer-core/src/main/java/org/apache/maven/index/DefaultScanner.java
 */
public class RepositoryReader implements Scanner {

  private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryReader.class);

  // Added for OSG-EYES ~ configure bad extensions
  private static final Set<String> EXTS_TO_SKIP =
      Stream.of(".lastUpdated").collect(Collectors.toSet());

  private final ArtifactContextProducer artifactContextProducer;

  public RepositoryReader(ArtifactContextProducer artifactContextProducer) {
    this.artifactContextProducer = artifactContextProducer;
  }

  public ScanningResult scan(ScanningRequest request) {
    request.getArtifactScanningListener().scanningStarted(request.getIndexingContext());

    ScanningResult result = new ScanningResult(request);
    scanDirectory(request.getStartingDirectory(), request);

    request.getArtifactScanningListener().scanningFinished(request.getIndexingContext(), result);
    return result;
  }

  private void scanDirectory(File dir, ScanningRequest request) {
    if (dir == null) {
      return;
    }

    File[] fileArray = dir.listFiles();
    if (fileArray == null) {
      return;
    }

    Set<File> files = new TreeSet<>(new ScannerFileComparator());
    files.addAll(Arrays.asList(fileArray));

    for (File f : files) {
      if (f.getName().startsWith(".")) {
        continue; // skip all hidden files and directories
      }

      if (f.isDirectory()) {
        scanDirectory(f, request);
      } else {
        processFile(f, request);
      }
    }
  }

  private void processFile(File file, ScanningRequest request) {
    // Added for OSG-EYES
    // ~ files with inappropriate extensions should not be indexed
    if (EXTS_TO_SKIP.stream().anyMatch(ext -> file.getName().endsWith(ext))) {
      LOGGER.debug("Skipping ineligible artifact file: " + file.getAbsolutePath());
      return;
    }

    IndexingContext context = request.getIndexingContext();
    ArtifactContext ac = artifactContextProducer.getArtifactContext(context, file);

    if (ac != null) {
      request.getArtifactScanningListener().artifactDiscovered(ac);
    }
  }

  /**
   * A special comparator to overcome some very bad limitations of nexus-indexer during scanning:
   * using this comparator, we force to "discover" POMs last, before the actual artifact file. The
   * reason for this, is to guarantee that scanner will provide only "best" informations 1st about
   * same artifact, since the POM->artifact direction of discovery is not trivial at all (pom read
   * -> packaging -> extension -> artifact file). The artifact -> POM direction is trivial.
   */
  private static class ScannerFileComparator implements Comparator<File> {
    public int compare(File o1, File o2) {
      if (o1.getName().endsWith(".pom") && !o2.getName().endsWith(".pom")) {
        // 1st is pom, 2nd is not
        return 1;
      } else if (!o1.getName().endsWith(".pom") && o2.getName().endsWith(".pom")) {
        // 2nd is pom, 1st is not
        return -1;
      } else {
        // both are "same" (pom or not pom)
        // Use reverse order so that timestamped snapshots
        // use latest - not first
        return o2.getName().compareTo(o1.getName());
      }
    }
  }
}
