(ns com.connexta.osgeyes.dev.assertions
  "Docstring"
  (:require [com.connexta.osgeyes.index.core :as index]))

(def test-version "2.19.14")

(defn- run-tests
  "Takes a variadic number of zero-argument functions that produce maps of assertion statements.
  The format should be a string key that describes the test and a vector of the form:
  [expected-count actual-count]
  Runs each map entry as if it were a test and reports any discrepancies between expected and
  actual."
  [& test-fns]
  (let [fails (->> test-fns
                   (map #(%))
                   (map #(seq %))
                   (apply concat)
                   (filter #(let [tst (last %)] (not= (first tst) (last tst)))))]
    (if (empty? fails)
      "All tests have passed"
      (let [fail->msg #(let [tst-name (first %) expected (first (last %)) actual (last (last %))]
                         (str "  failure at " tst-name ", expected " expected " but got " actual))]
        (throw (AssertionError. (str "Runtime test assertions failed due to the following:"
                                     (System/lineSeparator)
                                     (->> fails
                                          (map fail->msg)
                                          (interpose (System/lineSeparator))
                                          (reduce str)))))))))

(defn- test-deps-mvn-indexing []
  ;; Ensure a basic search yields the expected pair of records, one for pom and one for jar.
  {"test-search-jar-and-pom"        [2 (count (index/query-mvn
                                                (index/lookfor-all
                                                  (index/lookfor :artifact-id "proxy-camel-servlet")
                                                  (index/lookfor :version test-version))))]

   ;; Also ensure narrowing things down produces only the jar record.
   "test-search-jar-only"           [1 (count (index/query-mvn
                                                (index/lookfor-all
                                                  (index/lookfor :artifact-id "proxy-camel-servlet")
                                                  (index/lookfor :version test-version)
                                                  (index/lookfor :file-ext "jar"))))]

   ;; If the .m2 builds downstream projects, and those projects pull in DDF dependencies by
   ;; downloading without building, *.lastUpdated files come too, and those should be ignored by
   ;; the indexer.
   "test-search-lastUpdated"        [0 (count (index/query-mvn
                                                (index/lookfor :file-ext "pom.lastUpdated")))]

   ;; Ensure the number of bundles in the project hierarchy remains consistent...
   "test-gather-hierarchy"          [358 (->> (index/gather-hierarchy "ddf" test-version)
                                              (filter #(= (:file-ext %) "jar"))
                                              (filter #(= (:packaging %) "bundle"))
                                              (count))]

   ;; ...and are consistent with equals/hashcode as well; Clojure data structures are not used
   ;; throughout the ENTIRE callstack, only part of it.
   "test-gather-hierarchy-distinct" [358 (->> (index/gather-hierarchy "ddf" test-version)
                                              (filter #(= (:file-ext %) "jar"))
                                              (filter #(= (:packaging %) "bundle"))
                                              (distinct)
                                              (count))]})

(defn- test-deps-draw-graph []
  {})

;; The following optional stats / notes were asserted in an .m2 of the following: DDF 2.19.5,
;; 2.19.14, 2.19.17-SNAPSHOT, 2.26.1, and 2.27.0-SNAPSHOT. While running these, YMMV if the .m2
;; is not in the correct state.
(defn- test-misc-stats []
  {"test-pax-web-xml-artifact-count" [7 (count (index/query-mvn
                                                 (index/lookfor-all
                                                   (index/lookfor :artifact-id "pax-web*")
                                                   (index/lookfor :file-ext "xml"))))]
   "test-cfg-artifact-count"         [31 (count (index/query-mvn
                                                  (index/lookfor :file-ext "cfg")))]
   "test-yml-artifact-count"         [2 (count (index/query-mvn
                                                 (index/lookfor :file-ext "yml")))]
   "test-tgz-artifact-count"         [7 (count (index/query-mvn
                                                 (index/lookfor :file-ext "tar.gz")))]})

(comment
  ;; Run this one for the stable tests against a static DDF version.
  (run-tests test-deps-mvn-indexing
             test-deps-draw-graph)

  ;; Run this one for everything
  (run-tests test-deps-mvn-indexing
             test-deps-draw-graph
             test-misc-stats))