(ns com.connexta.osgeyes.cmds

  "This is the CLI interface of the application, literally speaking. This is the namespace that
  is pre-loaded into the REPLy instance. Public functions that live here will be discoverable by
  end users as part of the application."

  (:require [com.connexta.osgeyes.graph :as graph]
            [com.connexta.osgeyes.env :as env]
            [com.connexta.osgeyes.files.manifest :as manifest]
            [com.connexta.osgeyes.index.core :as index]
            [clojure.java.io :as io])
  (:import (java.awt Desktop)
           (java.io File)))

(def ^:private cache (atom {}))
(def ^:private filter-terms
  (set [;; not actually on the data structure - convenience used to imply ":from AND :to"
        :node
        ;; actual data structure fields
        :type
        :cause
        :from
        :to]))

(def ^:private default-filter
  [:node "ddf/.*" :node ".*catalog.*|.*spatial.*" :node "(?!.*plugin.*$).*"])

(def sel_default [:node "ddf/.*" :node ".*catalog.*|.*spatial.*" :node "(?!.*plugin.*$).*"])

;; ----------------------------------------------------------------------
;; # Private helpers
;;
;; Covers opening the browser & mapping a [:node ".*text.*" ...] filter vector to a predicate.
;;

(comment
  "Still need tests for this section"
  ;; ---
  "Potentially faster to use subvec instead of partition; refer to Clojure docs"
  (partition 2 [:a :b :c :d :e :f])
  "vs"
  (let [input [:a :b :c :d :e :f]
        pars (range 2 (inc (count input)) 2)]
    (map #(subvec input (- % 2) %) pars))
  ;; ---
  "Ensure these functions yield predicates that match correctly"
  (let [p (filter->predicate [:node ".*catalog.*|.*spatial.*"])]
    (p {:from "A" :to "B" :cause "some.package" :type "misc"})))

(defn- !open-file-in-browser [path]
  (.browse (Desktop/getDesktop) (.toURI (File. ^String path))))

(defn- ^:private filter-valid-shape?
  "Validate the shape of a filter f and provide useful error messages."
  [f]
  (cond (empty? f)
        (throw (IllegalArgumentException.
                 (str "Filter vector cannot be empty")))
        (odd? (count f))
        (throw (IllegalArgumentException.
                 (str "Expected even filter vector arity, but got vector with arity " (count f))))
        :default
        f))

(defn- ^:private filter-pair-valid-types?
  "Validate the semantics of a filter, now reduced to keyword-string pairs. If valid, will attempt
  to remap the pairs but with the string compiled into a pattern (regex) object."
  [[kw re]]
  (cond (or (not (keyword? kw)) (not (string? re)))
        (throw (IllegalArgumentException.
                 (str "Expected keyword-string pairs but got " [kw re])))
        (not (contains? filter-terms kw))
        (throw (IllegalArgumentException.
                 (str "Invalid search term '" kw "', supported terms are " filter-terms)))
        :default
        `(~kw ~(re-pattern re))))

(defn- ^:private term-match-fn
  "Given an edge, return a mapper predicate fn for keyword-pattern (regex) pairs.

  The intent of the returned function is to evaluate an edge to get a
  mapping function for use on filtering criteria, like so:
  (map (term-match-fn edge) pairs)
  "
  [edge]
  (fn [[kw pattern]]
    (if (= :node kw)
      (let [{term-from :from term-to :to} edge]
        (and (= term-from (re-matches pattern term-from))
             (= term-to (re-matches pattern term-to))))
      (let [term (kw edge)]
        (= term (re-matches pattern term))))))

(defn- ^:private pred-fn
  "Given a coll of keyword-pattern (regex) pairs, returns a function that can
  filter an edge. All regex pairs are AND'd together for the final result."
  [pairs]
  (fn [edge]
    (let [matched? (term-match-fn edge)]
      (reduce #(and %1 %2) (map matched? pairs)))))

;; Update terminology to be 'selection', not filter
(defn- ^:private filter->predicate
  "Transforms f, a filter, to a predicate function that can be used to
  filter edges: (filter (filter->predicate [:node \"regex\" ...]) edges)"
  [f]
  (->> f
       (#(if (vector? %)
           %
           (throw (IllegalArgumentException.
                    (str "Argument filter must be a vector, but was " %)))))
       ;; allow nesting for convenience
       (flatten)
       (apply vector)
       ;; validation of final form
       (filter-valid-shape?)
       (partition 2)
       (map filter-pair-valid-types?)
       (pred-fn)))

(comment
  (filter->predicate '())
  (filter->predicate [])
  (filter->predicate [:term])
  (filter->predicate [:term "term" "hi"])
  (filter->predicate [[]])
  (filter->predicate [[:term]])
  (filter->predicate [[:term] ["term"]])
  (filter->predicate [[:term] ["term"] [:term]])
  (filter->predicate [[:term "one"] ["term"] [:term "two"]])

  ((filter->predicate [:term :term]) {})
  ((filter->predicate ["term" :term]) {})
  ((filter->predicate [:term "term"]) {})
  ((filter->predicate [:from "term"]) {:from "term"})

  ((filter->predicate [:node "term"]) {:from "my-term" :to "your-term"})
  ((filter->predicate [:node "term"]) {:from "term" :to "your-term"})
  ((filter->predicate [:node "term"]) {:from "my-term" :to "term"})
  ((filter->predicate [:node "term"]) {:from "term" :to "term"})

  ((filter->predicate [[:node "one.*" :node ".*two.*"] :cause ".*package.*"])
   {:from "one-two-three" :to "one-three-two" :cause "some.package"}))

;; ----------------------------------------------------------------------
;; # REFACTORING
;;
;; Temporary.
;;

(comment
  "Tentative game plan is to introduce concept of 'artifact facets' and support
   no indirection besides basic selection attributes which help hide function
   signatures."

  ;; Current representation
  {"ddf/symbolic-name" {::manifest/Manifest-Version    "value"
                        ::manifest/Bundle-SymbolicName "symbolic-name"
                        #_etc}}

  ;; Artifact facets ('facet map'), then 'facet maps' plural would be 'artifacts'
  {"mvn:group/art/ver" {"manifest"  {}
                        "pom"       {}
                        "blueprint" {}
                        "feature"   {}}}

  ;; Transform to get back to "old" way of doing it
  (into {} (map #(vector %1 (get "manifest" %2)))))

(defn- gen-edges
  "Given a locale, returns a list of edges."
  [locale]
  (let [connectors [manifest/locale->edges]]
    (->> connectors
         (map #(% locale))
         (flatten)
         (distinct))))

(defn- aggregate-from-repo [qual path]
  (let [is-manifest?
        (fn [file]
          (and
            (= (.getName file) "MANIFEST.MF")
            (.contains (.getParent file) "/target/classes/META-INF")))
        get-relevant-file-paths
        (fn [root-project-path]
          (->> root-project-path
               io/file
               ;; Revisit performance later - consider pulling from file-seq in parallel and not
               ;; waiting for manifest filtering and path mapping
               file-seq
               (filter is-manifest?)
               (map #(.getPath %))))]
    (->> (get-relevant-file-paths path)
         ;; Does Clojure have a "cold start" w.r.t threading? - might be REPL / JVM related
         (pmap manifest/parse-file)
         (map #(vector (str qual "/" (::manifest/Bundle-SymbolicName %)) %))
         (into {}))))

(defn- aggregate-from-m2 [name ver]
  (->> (index/gather-hierarchy name name ver)
       (filter #(= (:packaging %) "bundle"))
       (filter #(= (:file-ext %) "jar"))
       ;; Add keyword mapping / insolation for mvn-indexer attributes
       (map #(get-in % [:attrs "JAR_MANIFEST"]))
       (map #(manifest/parse-content %))
       (map #(vector (str name "/" (::manifest/Bundle-SymbolicName %)) %))
       (into {})))

(comment
  (index/gather-hierarchy "ddf" "ddf" "2.19.5")
  (aggregate-from-m2 "ddf" "2.19.5")
  (filter #(not-empty (filter (fn [line] (= "" line)) %)) (aggregate-from-m2 "ddf" "2.19.5"))
  (count (aggregate-from-m2 "ddf" "2.19.5"))
  (filter #(.contains (:artifact-id %) "security-core") (aggregate-from-m2 "ddf" "2.19.5")))

;; ----------------------------------------------------------------------
;; # Public CLI
;;
;; Commands the user invokes directly.
;;

(comment
  "Remember, still need tests for these things"
  ;; --- Navigation
  (open-tmp-dir)
  (open-working-dir)
  (open-repos-dir)
  ;; --- Index
  (count @cache)
  (index-load)
  (index-dump)
  (index-repos "ddf")
  ;; --- Viz
  (count (filter (filter->predicate default-filter) (gen-edges @cache)))
  (take 10 (filter default-filter (gen-edges @cache)))
  (draw-graph (filter->predicate default-filter)))

;;
;; Convenience commands for navigating to specific directories.
;;

(defn- !open-dir [dir] (do (!open-file-in-browser dir) (str "Navigating to " dir)))
(defn open-tmp-dir [] (!open-dir (env/resolve-tmp "")))
(defn open-working-dir [] (!open-dir (env/resolve-subdir "")))
(defn open-repos-dir [] (!open-dir (env/resolve-repo "")))

;;
;; Save / restore dependency data so that the repo can be iterated upon and rebuilt
;; while also doing analysis. It's time consuming to jump back and forth between master
;; and stable branches when a build is necessary, especially across dependent projects.
;;
(comment
  "For later - need to combine both sides of index lifecycle into one function / operation.
  For now, '(index-repos ddf)' will work for a fixed version and allow graph gen to continue.
  Indices built with a prior version can still be loaded.

  Remember that an 'index' was originally just a Map of String 'repo/bundle-symbolic-name'
  to manifest maps."

  ;; Consider keeping import/export of 'facet' data
  ;; Could also support JSON pretty easily
  (defn facet-export [] ())
  (defn facet-import [] ())

  ;; Consider adding a way to select the maven roots you want to gather for analysis.
  (defn show-roots [name ver]
    ())
  (defn add-root [name ver]
    ())
  (defn clear-roots []
    ())

  ;; Need a more comprehensive 'config' definition which targets precisely what to do.
  {:gather-from ["mvn:ddf/ddf/${VERSION}"
                 "mvn:other/other/${VERSION}"]
   :select [:node "ddf/.*" :node ".*/catalog.*"]})

(defn index-load
  ([]
   (index-load (env/resolve-tmp "viz-index.edn")))
  ([path]
   (->> (slurp path)
        (read-string)
        (swap! cache #(identity %2))
        (count)
        (hash-map :manifests)
        (do (println "Dependency cache loaded: ")))))

(defn index-dump
  ([]
   (index-dump (env/resolve-tmp "viz-index.edn")))
  ([path]
   (if (empty? @cache)
     "No cache exists to dump"
     (do (spit path (with-out-str (pr @cache)) :create true) (str "Index written to " path)))))

(defn index-repos [& repos]
  (let [with-output #(do (println (str (count repos) " repositories indexed: ")) %)]
    (->> repos
         (pmap #(aggregate-from-m2 % "2.19.5"))
         (apply merge)
         (swap! cache #(identity %2))
         (count)
         (hash-map :manifests)
         (with-output))))

(defn list-edges "New way to list things - takes a filter & options."
  [f & {:keys [max cause? type?]
        :or   {max 50 cause? false type? false}}]
  (let [dissoc-cause #(dissoc % :cause)
        dissoc-type #(dissoc % :type)]
    (->> @cache
         (gen-edges)
         (filter (filter->predicate f))
         ;; by default, do not print duplicate dependencies for each cause
         (#(if cause? % (distinct (map dissoc-cause %))))
         (#(if type? % (map dissoc-type %)))
         (take max)
         (#(do (clojure.pprint/print-table %)
               (str "Printed " (count %) " dependencies"))))))

(comment
  (list-edges sel_default)
  (list-edges sel_default :cause? true :type? true))

(defn draw-graph
  "Renders a graph of edges as HTML and opens the file in the browser. The default filter includes
  only DDF Catalog and Spatial nodes, but no plugins."
  ([] (draw-graph [:node "ddf/.*"
                   :node ".*catalog.*|.*spatial.*"
                   :node "(?!.*plugin.*$).*"]))
  ([f] (->> @cache
            (gen-edges)
            (filter (filter->predicate f))
            (graph/gen-html-from-edges)
            (graph/!write-html)
            (!open-file-in-browser))))

(comment
  @cache
  (index-repos "ddf")
  (list-edges [:node "ddf/.*" :node ".*catalog.*"] :max 200)
  (draw-graph [:node "ddf/.*" :node ".*catalog.*"]))