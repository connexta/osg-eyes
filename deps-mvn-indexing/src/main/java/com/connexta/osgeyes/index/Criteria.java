package com.connexta.osgeyes.index;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.maven.index.Field;
import org.apache.maven.index.Indexer;
import org.apache.maven.index.expr.SourcedSearchExpression;
import org.apache.maven.index.expr.UserInputSearchExpression;

/**
 * General mechanism for specifying search criteria.
 *
 * <p>The {@link Criteria} class itself serves as a factory for producing subclasses or anything
 * that can specify search criteria. Injected options are propagated unless explicitly overwritten.
 * The default options are suitable for traditional AND queries.
 */
public class Criteria {

  private final Options options;

  /**
   * Eventually it would be nice to remove this as a dependency. To do so, we would need to
   * understand how mvn-indexer is building the query and own that process ourselves. For now that's
   * too big a jump to be immediately useful.
   */
  private final Indexer indexer;

  public Criteria(Indexer indexer) {
    this(new Options(), indexer);
  }

  public Criteria(Options options, Indexer indexer) {
    this.options = Objects.requireNonNull(options, "options cannot be null");
    this.indexer = Objects.requireNonNull(indexer, "indexer cannot be null");
  }

  public Options getOptions() {
    return options;
  }

  public Indexer getIndexer() {
    return indexer;
  }

  public Queryable of(Field field, String value) {
    return new KeyValue(field, value, options, indexer);
  }

  public Queryable of(Field field, String value, Options options) {
    return new KeyValue(field, value, options, indexer);
  }

  public Queryable of(Queryable... criteria) {
    return new Compound(Arrays.asList(criteria), indexer);
  }

  public Options options() {
    return new Options();
  }

  /**
   * Supports all the different options available to customize criteria behavior as part of a query.
   */
  public static class Options {

    private BooleanClause.Occur occur;

    private boolean exact;

    private Options() {
      this.occur = BooleanClause.Occur.MUST;
      this.exact = true;
    }

    public Options with(BooleanClause.Occur occurrancePolicy) {
      this.occur = occurrancePolicy;
      return this;
    }

    public Options partialInput() {
      this.exact = false;
      return this;
    }
  }

  /**
   * Supports converting a nested criteria structure to a proper Lucene query. Using this helps wrap
   * the Lucene structure and gradually add validation to support all the invariants that Lucene
   * expects.
   */
  public abstract static class Queryable extends Criteria {

    public Queryable(Options options, Indexer indexer) {
      super(options, indexer);
    }

    public abstract Query getQuery();
  }

  /**
   * Collection of criteria that maps to a traditional boolean query using the AND / OR predicates.
   * Only aggregates terms; actual query behavior determined by {@link BooleanClause.Occur} settings
   * throughout the query's structure.
   */
  private static class Compound extends Queryable {

    private final List<Queryable> criteria;

    public Compound(List<Queryable> criteria, Indexer indexer) {
      super(new Options(), indexer);
      if (criteria == null || criteria.isEmpty()) {
        throw new IllegalArgumentException("Null or empty criteria is not supported");
      }
      this.criteria = criteria;
    }

    @Override
    public Query getQuery() {
      if (criteria.size() == 1) {
        return criteria.get(0).getQuery();
      }
      BooleanQuery.Builder builder = new BooleanQuery.Builder();
      criteria.forEach(c -> builder.add(c.getQuery(), c.getOptions().occur));
      return builder.build();
    }

    @Override
    public String toString() {
      return "["
          + criteria.stream().map(Criteria::toString).collect(Collectors.joining(", "))
          + "]";
    }
  }

  /**
   * Terminal search criteria that looks for a particular matching between a key and a value,
   * typically a maven attribute indexed as part of the build model.
   *
   * <p>Currently leverages code from the maven indexer library to hide some lucene details,
   * although many parts of lucene are still leaking and should be cleaned up if further iteration
   * is expected (depending on scope).
   */
  private static class KeyValue extends Queryable {

    private final Field field;

    private final String value;

    private KeyValue(Field field, String value, Options options, Indexer indexer) {
      super(options, indexer);
      this.field = field;
      this.value = value;
    }

    @Override
    public Query getQuery() {
      // Note the use of indexer expression classes, not strictly lucene classes
      return getIndexer()
          .constructQuery(
              field,
              getOptions().exact
                  ? new SourcedSearchExpression(value)
                  : new UserInputSearchExpression(value));
    }

    @Override
    public String toString() {
      return String.format("(%s %s %s)", field.getFieldName(), getOperator(), value);
    }

    private String getOperator() {
      if (getOptions().exact) {
        switch (getOptions().occur) {
          case MUST:
          case FILTER:
            return "MUST MATCH";
          case SHOULD:
            return "SHOULD MATCH";
          case MUST_NOT:
            return "MUST NOT MATCH";
        }
      } else {
        switch (getOptions().occur) {
          case MUST:
          case FILTER:
            return "MUST START WITH";
          case SHOULD:
            return "SHOULD START WITH";
          case MUST_NOT:
            return "MUST NOT START WITH";
        }
      }
      throw new IllegalStateException(
          String.format(
              "Unexpected combination of values, exact = '%s' and occurrance = '%s'",
              getOptions().exact, getOptions().occur));
    }
  }
}
