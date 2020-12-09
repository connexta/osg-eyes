package com.connexta.osgeyes.index;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.maven.index.ArtifactContext;
import org.apache.maven.index.ArtifactInfo;
import org.apache.maven.index.IndexerField;
import org.apache.maven.index.IndexerFieldVersion;
import org.apache.maven.index.context.IndexCreator;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Write information to the index that would allow the traversal of any pom's {@code <modules/>}
 * block. This is better than manual navigation of source code directories because all versions of
 * the artifacts are discoverable at once.
 */
@Singleton
@Named(MvnHierarchyIndexCreator.ID)
public class MvnHierarchyIndexCreator implements IndexCreator {

  // Useful to reference within package scope
  static final String ID = "deps/mvn-hierarchy";

  private static final Logger LOGGER = LoggerFactory.getLogger(MvnHierarchyIndexCreator.class);

  private static final List<String> DEPS = Collections.singletonList("min");

  private static final IndexerField POM_PARENT_FIELD =
      new IndexerField(
          MvnOntology.POM_PARENT,
          IndexerFieldVersion.V3,
          MvnOntology.POM_PARENT.getFieldName(),
          MvnOntology.POM_PARENT.getDescription(),
          Field.Store.YES,
          Field.Index.NOT_ANALYZED);

  private static final IndexerField POM_MODULES_FIELD =
      new IndexerField(
          MvnOntology.POM_MODULES,
          IndexerFieldVersion.V3,
          MvnOntology.POM_MODULES.getFieldName(),
          MvnOntology.POM_MODULES.getDescription(),
          Field.Store.YES,
          Field.Index.NOT_ANALYZED);

  private static final List<IndexerField> FIELDS =
      Arrays.asList(POM_PARENT_FIELD, POM_MODULES_FIELD);

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
    final ArtifactInfo minInfo = artifactContext.getArtifactInfo();
    final Map<String, String> attributes = minInfo.getAttributes();
    final Model pomModel = artifactContext.getPomModel();
    if (pomModel == null) {
      LOGGER.trace(
          "No pom model was found for artifact " + artifactContext.getArtifact().getAbsolutePath());
      return;
    }

    final MvnCoordinate currentPomCoord =
        MvnCoordinate.newInstance(
            minInfo.getGroupId(), minInfo.getArtifactId(), minInfo.getVersion());

    final Parent pomParent = pomModel.getParent();
    if (pomParent == null) {
      LOGGER.trace("No pom parent was found for artifact " + MvnCoordinate.write(currentPomCoord));
    } else {
      final MvnCoordinate pomParentCoord =
          MvnCoordinate.newInstance(
              pomParent.getGroupId(), pomParent.getArtifactId(), pomParent.getVersion());
      attributes.put(POM_PARENT_FIELD.getKey(), MvnCoordinate.write(pomParentCoord));
    }

    final List<String> pomModules = pomModel.getModules();
    if (pomModules == null || pomModules.isEmpty()) {
      LOGGER.trace(
          "No pom modules were found for artifact " + MvnCoordinate.write(currentPomCoord));
    } else {
      attributes.put(POM_MODULES_FIELD.getKey(), String.join(",", pomModules));
    }
  }

  @Override
  public void updateDocument(ArtifactInfo artifactInfo, Document document) {
    final MvnCoordinate currentPomCoord =
        MvnCoordinate.newInstance(
            artifactInfo.getGroupId(), artifactInfo.getArtifactId(), artifactInfo.getVersion());

    final Map<String, String> attributes = artifactInfo.getAttributes();
    final String pomParent = attributes.get(POM_PARENT_FIELD.getKey());
    final String pomModules = attributes.get(POM_MODULES_FIELD.getKey());

    if (pomParent == null) {
      LOGGER.trace(
          "No pom parent attribute to write to lucene index for artifact "
              + MvnCoordinate.write(currentPomCoord));
    } else {
      document.add(POM_PARENT_FIELD.toField(pomParent));
    }

    if (pomModules == null) {
      LOGGER.trace(
          "No pom modules attribute to write to lucene index for artifact "
              + MvnCoordinate.write(currentPomCoord));
    } else {
      final List<String> modules = Arrays.asList(pomModules.split(","));
      modules.forEach(m -> document.add(POM_MODULES_FIELD.toField(m)));
    }
  }

  @Override
  public boolean updateArtifactInfo(Document document, ArtifactInfo artifactInfo) {
    final Map<String, String> attributes = artifactInfo.getAttributes();

    final String parent = document.get(POM_PARENT_FIELD.getKey());
    final boolean updatedParent = parent != null;
    if (updatedParent) {
      attributes.put(POM_PARENT_FIELD.getKey(), parent);
    }

    final List<String> modules = Arrays.asList(document.getValues(POM_MODULES_FIELD.getKey()));
    final boolean updatedModules = !modules.isEmpty();
    if (updatedModules) {
      attributes.put(POM_MODULES_FIELD.getKey(), String.join(",", modules));
    }

    return Stream.of(updatedParent, updatedModules).reduce(Boolean::logicalOr).orElse(false);
  }

  @Override
  public String toString() {
    return ID;
  }
}
