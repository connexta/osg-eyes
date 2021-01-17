package org.apache.maven.index.util.zip;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Overwriting the third party ZipFacade with an enhanced version that will prevent noisy warn-level
 * exception propagation during indexing. Instead of catching a thrown exception on purpose when the
 * wrong file mistakenly tries to be unzipped, this version of ZipFacade detects files that are not
 * zips and provides a {@link ZipHandle} that is hard-coded to be empty.
 *
 * <p>It's worth noting the effects of this class will only be visible in the main repl application
 * (deps-draw-graph). The indexing demo application (this module), for now, does not alter the
 * classpath to make this override visible to the local main() method used for testing
 * index-specific functions.
 *
 * <p>Code in this file adapted from:
 * https://github.com/apache/maven-indexer/blob/maven-indexer-6.0.0/indexer-core/src/main/java/org/apache/maven/index/util/zip/ZipFacade.java
 */
public class ZipFacade {

  private static final Logger LOGGER = LoggerFactory.getLogger(ZipFacade.class);

  public static final long MEGABYTE = 1048576L;

  public static final long JAVA_ZIPFILE_SIZE_THRESHOLD =
      Long.getLong(
          "org.apache.maven.index.util.zip.ZipFacade.javaZipFileSizeThreshold", 100L * MEGABYTE);

  // Added for OSG-EYES ~ configure extensions that cannot be unzipped
  private static final Set<String> EXTS_TO_SKIP =
      Stream.of("xml", "cfg", "yml", "tar.gz").collect(Collectors.toSet());

  private static final boolean TRUEZIP_AVAILABLE;

  static {
    Class<?> clazz;

    try {
      clazz = Class.forName("de.schlichtherle.truezip.zip.ZipFile");
    } catch (ClassNotFoundException e) {
      clazz = null;
    }

    TRUEZIP_AVAILABLE = clazz != null;
  }

  public static ZipHandle getZipHandle(File targetFile) throws IOException {
    if (!targetFile.isFile()) {
      throw new IOException("The targetFile should point to an existing ZIP file:" + targetFile);
    }

    // Added for OSG-EYES ~ validate extensions that cannot be unzipped
    if (EXTS_TO_SKIP.stream().anyMatch(ext -> targetFile.getName().endsWith(ext))) {
      LOGGER.debug("Skipping pom extraction for non-zip file: " + targetFile.getAbsolutePath());
      return new EmptyZipHandle(targetFile);
    }

    if (TRUEZIP_AVAILABLE && targetFile.length() > JAVA_ZIPFILE_SIZE_THRESHOLD) {
      return new TrueZipZipFileHandle(targetFile);
    }

    return new JavaZipFileHandle(targetFile);
  }

  public static void close(ZipHandle handle) throws IOException {
    if (handle != null) {
      handle.close();
    }
  }

  private static class EmptyZipHandle implements ZipHandle {

    private final File target;

    private EmptyZipHandle(File target) {
      this.target = target;
    }

    @Override
    public boolean hasEntry(String path) throws IOException {
      return false;
    }

    @Override
    public List<String> getEntries() {
      return Collections.emptyList();
    }

    @Override
    public List<String> getEntries(EntryNameFilter filter) {
      return Collections.emptyList();
    }

    @Override
    public InputStream getEntryContent(String path) throws IOException {
      throw new UnsupportedOperationException(
          "No zip content exists to retrieve for artifact: " + target.getAbsolutePath());
    }

    @Override
    public void close() throws IOException {
      // no-op, nothing to close
    }
  }
}
