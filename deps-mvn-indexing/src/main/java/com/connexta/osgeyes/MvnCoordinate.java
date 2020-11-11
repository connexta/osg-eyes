package com.connexta.osgeyes;

import java.util.regex.Pattern;

public class MvnCoordinate {

  private static final Pattern COORD_STR_PATTERN =
      Pattern.compile("mvn:\\p{Alpha}+\\p{Alnum}*/\\p{Alpha}+\\p{Alnum}*/\\p{Alnum}+");

  private final String groupId;

  private final String artifactId;

  private final String version;

  private MvnCoordinate(String groupId, String artifactId, String version) {
    this.groupId = groupId;
    this.artifactId = artifactId;
    this.version = version;
  }

  public String getGroupId() {
    return groupId;
  }

  public String getArtifactId() {
    return artifactId;
  }

  public String getVersion() {
    return version;
  }

  @Override
  public String toString() {
    return write(this);
  }

  /**
   * Create a new coordinate object given the minimum amount of information. Throws an {@link
   * IllegalArgumentException} for any null or blank args.
   *
   * @param groupId the artifact's group ID.
   * @param artifactId the artifact's artifact ID.
   * @param version the artifact's version.
   * @return the new coordinate object.
   */
  public static MvnCoordinate newInstance(String groupId, String artifactId, String version) {
    if (groupId == null
        || artifactId == null
        || version == null
        || groupId.isEmpty()
        || artifactId.isEmpty()
        || version.isEmpty()) {
      throw new IllegalArgumentException(
          String.format(
              "Invalid GAV info, g = '%s', a = '%s', v = '%s'", groupId, artifactId, version));
    }
    return new MvnCoordinate(groupId, artifactId, version);
  }

  /**
   * Parses the provided string of the form {@code mvn:groupId/artifactId/version} into a new
   * instance of {@link MvnCoordinate}. Throws an {@link IllegalArgumentException} if the input
   * string is malformed.
   *
   * @param mvnString the maven coordinates to parse.
   * @return a coordinate string object.
   */
  public static MvnCoordinate parse(String mvnString) {
    if (!COORD_STR_PATTERN.matcher(mvnString).matches()) {
      throw new IllegalArgumentException("Invalid coordinate string: " + mvnString);
    }
    String prefixStripped = mvnString.substring(4, mvnString.length());
    String[] parts = prefixStripped.split("/");
    return new MvnCoordinate(parts[0], parts[1], parts[2]);
  }

  /**
   * Writes the provided {@link MvnCoordinate} object into its parseable string representation.
   *
   * @param mvnObject the object to write out to a string.
   * @return the coordinate string.
   */
  public static String write(MvnCoordinate mvnObject) {
    return String.format(
        "mvn:%s/%s/%s", mvnObject.getGroupId(), mvnObject.getArtifactId(), mvnObject.getVersion());
  }
}
