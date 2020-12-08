package com.connexta.osgeyes;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.maven.index.ArtifactContext;
import org.apache.maven.index.ArtifactInfo;
import org.apache.maven.index.IndexerField;
import org.apache.maven.index.IndexerFieldVersion;
import org.apache.maven.index.context.IndexCreator;
import org.apache.maven.index.util.zip.ZipFacade;
import org.apache.maven.index.util.zip.ZipHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Makes the artifacts full-text jar manifest retrievable from the index, but not searchable as part
 * of a query.
 *
 * <p>Code adapted from org.apache.maven.index.creator.OsgiArtifactIndexCreator and related source.
 * https://github.com/apache/maven-indexer/blob/maven-indexer-6.0.0/indexer-core/src/main/java/org/apache/maven/index/creator/OsgiArtifactIndexCreator.java#L423-L438
 */
@Singleton
@Named(JarManifestIndexCreator.ID)
public class JarManifestIndexCreator implements IndexCreator {
  // Useful to reference within package scope
  static final String ID = "deps/jar-manifest";

  private static final String MANFIEST_ENTRY = "META-INF/MANIFEST.MF";

  private static final Logger LOGGER = LoggerFactory.getLogger(JarManifestIndexCreator.class);

  private static final List<String> DEPS = Collections.singletonList("min");

  private static final IndexerField JAR_MANIFEST_FIELD =
      new IndexerField(
          MvnOntology.JAR_MANIFEST,
          IndexerFieldVersion.V3,
          MvnOntology.JAR_MANIFEST.getFieldName(),
          MvnOntology.JAR_MANIFEST.getDescription(),
          Field.Store.YES,
          Field.Index.NO);

  private static final List<IndexerField> FIELDS = Collections.singletonList(JAR_MANIFEST_FIELD);

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public List<String> getCreatorDependencies() {
    return DEPS;
  }

  @Override
  public Collection<IndexerField> getIndexerFields() {
    return FIELDS;
  }

  @Override
  public void populateArtifactInfo(ArtifactContext artifactContext) throws IOException {
    final ArtifactInfo info = artifactContext.getArtifactInfo();
    final Map<String, String> attributes = info.getAttributes();
    final File artifactFile = artifactContext.getArtifact();
    if (artifactFile != null && artifactFile.isFile() && artifactFile.getName().endsWith(".jar")) {
      ZipHandle handle = null;
      try {
        handle = ZipFacade.getZipHandle(artifactFile);
        boolean hasManifest = handle.getEntries().stream().anyMatch(MANFIEST_ENTRY::equals);
        if (!hasManifest) {
          return;
        }
        final String manifestText = readAllBytes(handle.getEntryContent(MANFIEST_ENTRY));
        if (manifestText != null && !manifestText.isEmpty()) {
          attributes.put(JAR_MANIFEST_FIELD.getKey(), manifestText);
        }
      } finally {
        try {
          ZipFacade.close(handle);
        } catch (Exception e) {
          LOGGER.error("Could not close jar file properly", e);
        }
      }
    }
  }

  @Override
  public void updateDocument(ArtifactInfo artifactInfo, Document document) {
    final MvnCoordinate currentPomCoord =
        MvnCoordinate.newInstance(
            artifactInfo.getGroupId(), artifactInfo.getArtifactId(), artifactInfo.getVersion());

    final Map<String, String> attributes = artifactInfo.getAttributes();
    final String manifestText = attributes.get(JAR_MANIFEST_FIELD.getKey());

    if (manifestText == null) {
      LOGGER.trace(
          "No manifest text to write to lucene index for artifact "
              + MvnCoordinate.write(currentPomCoord));
    } else {
      document.add(JAR_MANIFEST_FIELD.toField(manifestText));
    }
  }

  @Override
  public boolean updateArtifactInfo(Document document, ArtifactInfo artifactInfo) {
    final Map<String, String> attributes = artifactInfo.getAttributes();
    final String manifestText = document.get(JAR_MANIFEST_FIELD.getKey());

    final boolean updatedManifest = manifestText != null;
    if (updatedManifest) {
      attributes.put(JAR_MANIFEST_FIELD.getKey(), manifestText);
    }

    return updatedManifest;
  }

  @Override
  public String toString() {
    return ID;
  }

  private static String readAllBytes(InputStream in) {
    final Scanner s = new Scanner(in).useDelimiter("\\A");
    return s.hasNext() ? s.next() : "";
  }
}
