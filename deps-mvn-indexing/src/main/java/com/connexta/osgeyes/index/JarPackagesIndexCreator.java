package com.connexta.osgeyes.index;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Index;
import org.apache.maven.index.ArtifactContext;
import org.apache.maven.index.ArtifactInfo;
import org.apache.maven.index.IndexerField;
import org.apache.maven.index.IndexerFieldVersion;
import org.apache.maven.index.context.IndexCreator;
import org.apache.maven.index.util.zip.ZipFacade;
import org.apache.maven.index.util.zip.ZipHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Makes all packages contained in a jar indexed and searchable. */
@Singleton
@Named(JarPackagesIndexCreator.ID)
public class JarPackagesIndexCreator implements IndexCreator {

  // Useful to reference within package scope
  static final String ID = "deps/jar-packages";

  private static final Logger LOGGER = LoggerFactory.getLogger(JarPackagesIndexCreator.class);

  private static final IndexerField JAR_PACKAGES_FIELD =
      new IndexerField(
          MvnOntology.JAR_PACKAGES,
          IndexerFieldVersion.V3,
          MvnOntology.JAR_PACKAGES.getFieldName(),
          MvnOntology.JAR_PACKAGES.getDescription(),
          Field.Store.YES,
          Index.ANALYZED);

  private static final List<IndexerField> FIELDS = Collections.singletonList(JAR_PACKAGES_FIELD);

  private static final List<String> DEPS = Collections.singletonList("min");

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
        List<String> paths =
            handle.getEntries().stream()
                .filter(s -> s.endsWith(".class"))
                .map(JarPackagesIndexCreator::classFileToPackageNotation)
                .distinct()
                .collect(Collectors.toList());
        attributes.put(JAR_PACKAGES_FIELD.getKey(), String.join(",", paths));
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
    final String pathText = attributes.get(JAR_PACKAGES_FIELD.getKey());

    if (pathText == null) {
      LOGGER.trace(
          "No path text to write to lucene index for artifact {}",
          MvnCoordinate.write(currentPomCoord));
    } else {
      document.add(JAR_PACKAGES_FIELD.toField(pathText));
    }
  }

  @Override
  public boolean updateArtifactInfo(Document document, ArtifactInfo artifactInfo) {
    final Map<String, String> attributes = artifactInfo.getAttributes();
    final String pathText = document.get(JAR_PACKAGES_FIELD.getKey());

    final boolean validPath = pathText != null;
    if (validPath) {
      attributes.put(JAR_PACKAGES_FIELD.getKey(), pathText);
    }

    return validPath;
  }

  private static String classFileToPackageNotation(String classFile) {
    String[] split = classFile.split("/");
    return String.join(".", Arrays.copyOf(split, split.length - 1));
  }

  @Override
  public String toString() {
    return ID;
  }
}
