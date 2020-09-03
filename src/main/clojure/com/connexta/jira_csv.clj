(ns com.connexta.jira-csv
  "Functions for manipulating a Jira issues CSV export."
  (:require [clojure.string :as cstr]
            [clojure.set :as cset]
    #_[clojure.tools.trace :as ctrace])
  (:import (java.util.regex Pattern)))

(def jira-prefix
  (let [prefix (System/getProperty "jira.prefix")]
    (if prefix
      prefix
      (throw (IllegalStateException. "System property jira.prefix not set")))))

(def data-file-old
  "/Users/lambeaux/Documents/jira/jira-sept-2nd-2018-to-sept-2nd-2016.csv")

(def data-file-new
  "/Users/lambeaux/Documents/jira/jira-sept-2nd-2020-to-sept-2nd-2018.csv")

(def jira-schema
  (sorted-map
    :parent-id "Parent id"
    ;:project-key "Project key"
    ;:project-name "Project name"
    ;:project-type "Project type"
    ;:project-lead "Project lead"
    ;:project-description "Project description"
    ;:project-url "Project url"
    :issue-summary "Summary"
    :issue-key "Issue key"
    :issue-id "Issue id"
    :issue-type "Issue Type"
    :issue-status "Status"
    :issue-priority "Priority"
    :issue-resolution "Resolution"
    :issue-assignee "Assignee"
    :issue-reporter "Reporter"
    :issue-creator "Creator"
    :issue-created-date "Created"
    :issue-updated-date "Updated"
    :issue-lastviewed-date "Last Viewed"
    :issue-resolved-date "Resolved"
    :issue-affects-versions "Affects Version/s"
    :issue-fix-versions "Fix Version/s"
    :issue-components "Component/s"
    :issue-labels "Labels"
    :issue-description "Description"
    :linkout-agile-stories "Outward issue link (Agile Stories)"
    :linkout-blocks "Outward issue link (Blocks)"
    :linkout-causality "Outward issue link (Causality)"
    :linkout-child "Outward issue link (Child-Issue)"
    :linkout-child-parent "Outward issue link (Child-Parent)"
    :linkout-clones "Outward issue link (Cloners)"
    :linkout-dependency "Outward issue link (Dependency)"
    :linkout-derives "Outward issue link (Derives)"
    :linkout-describes "Outward issue link (Describes)"
    :linkout-duplicate "Outward issue link (Duplicate)"
    :linkout-implements "Outward issue link (Implements)"
    :linkout-include "Outward issue link (Include)"
    :linkout-parent-child "Outward issue link (Parent-Child)"
    :linkout-relates "Outward issue link (Relates)"
    :linkout-requirement "Outward issue link (Requirement)"
    :linkout-spawned "Outward issue link (Spawned)"
    :linkout-supercedes "Outward issue link (Supercedes)"
    :linkout-testing "Outward issue link (Testing)"
    :linkout-traces "Outward issue link (Traces)"))

(def jira-schema-inverted (cset/map-invert jira-schema))
(def jira-attributes (into (sorted-set) (map second jira-schema)))
(def ^Pattern jira-line-regex (re-pattern (str "[^\\|]+\\|(" jira-prefix "-)[0-9]{1,5}\\|.+")))
(def ^Pattern jira-line-regex-old #"[^\|]+\|(xxx-)[0-9]{1,5}\|.+")

(defn- parse-path-to-lines
  [path]
  (with-open [rdr (clojure.java.io/reader path)]
    (doall (line-seq rdr))))

(defn- index-label-map [lines]
  (->> (cstr/split (first lines) #"\|")
       (map-indexed vector)
       (filter #(jira-attributes (second %)))
       (map #(vector (first %)
                     (jira-schema-inverted (second %))))
       (into (sorted-map))))

(defn- consolidate [lines]
  #_(clojure.tools.trace/dotrace)
  (if-not (->> lines first (.matcher jira-line-regex) .matches)
    (throw (IllegalArgumentException. "Invalid input, first line of CSV should match."))
    (reduce
      (fn [val next]
        (if (->> next (.matcher jira-line-regex) .matches)
          (conj val next)
          (conj (pop val) (str (last val) next))))
      [] lines)))

(defn- transform-lines [lines]
  (let [ilm
        (index-label-map lines)
        line->map
        (fn [line]
          (->> (cstr/split line #"\|")
               (map-indexed vector)
               (map #(vector (ilm (first %))
                             (second %)))
               (filter #(and (first %)
                             (seq (second %))))
               (into (sorted-map))))]
    (map line->map (consolidate (rest lines)))))

(comment ;; New experiments
  ;;
  ;; Isolate the labels
  ;;
  (map :issue-labels
    (take 50 (->> data-file-old
                  parse-path-to-lines
                  transform-lines))))

(comment ;; Top level invocations
  ;;
  ;; First 50 lines of raw data file
  ;;
  (take 50 (->> data-file-old
                parse-path-to-lines))
  ;;
  ;; First 50 true, aggregated lines of the data file,
  ;; minus the first lines with column headings
  ;;
  (take 50 (->> data-file-old
                parse-path-to-lines
                rest
                consolidate))
  ;;
  ;; First 50 aggregated records / issues
  ;;
  (take 50 (->> data-file-old
                parse-path-to-lines
                transform-lines))
  ;;
  ;; Get first line of CSV
  ;;
  (->> data-file-old
       parse-path-to-lines
       first)
  ;;
  ;; Get all lines of CSV except the first one, only
  ;; showing 10 of them
  ;;
  (->> data-file-old
       parse-path-to-lines
       rest
       (take 10)))

(comment ;; Old experiments
  ;;
  #_(ctrace/dotrace)
  ;;
  ;; Validate consolidation
  ;;
  (consolidate
    '((str "blah|" jira-prefix "-8012|blah|||, ")
      "more|| content "
      "even more"
      (str "ok|" jira-prefix "-12|yes||")
      " more"))
  ;;
  ;; Validate matching
  ;;
  (.matches (.matcher jira-line-regex (str "ok|" jira-prefix "-12|yes||")))
  ;;
  ;; Validate the index-label-map
  ;;
  (index-label-map (parse-path-to-lines data-file-old)))