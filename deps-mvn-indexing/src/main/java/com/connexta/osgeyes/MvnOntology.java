package com.connexta.osgeyes;

import org.apache.maven.index.Field;

/** Application Maven ontology for indexing dependency information. */
public interface MvnOntology {

  String NAMESPACE = "urn:deps#";

  Field POM_PARENT = new Field(null, NAMESPACE, "POM_PARENT", "The parent element of the pom");

  Field POM_MODULES = new Field(null, NAMESPACE, "POM_MODULES", "The modules element of the pom");

  Field JAR_MANIFEST =
      new Field(null, NAMESPACE, "JAR_MANIFEST", "Plain text content of the jar manifest");
}
