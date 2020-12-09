package com.connexta.osgeyes.index;

import org.apache.maven.index.ArtifactInfo;
import org.apache.maven.index.ArtifactInfoFilter;
import org.apache.maven.index.context.IndexingContext;
import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionScheme;

public class VersionRangeFilter implements ArtifactInfoFilter {

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
