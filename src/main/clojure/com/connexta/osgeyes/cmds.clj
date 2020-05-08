(ns com.connexta.osgeyes.cmds

  "This is the CLI interface of the application, literally speaking. This is the namespace that
  is pre-loaded into the REPLy instance. Public functions that live here will be discoverable by
  end users as part of the application."

  (:require [com.connexta.osgeyes.locale :as locale]
            [com.connexta.osgeyes.graph :as graph]
            [com.connexta.osgeyes.env :as env])
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
  (count (filter (filter->predicate default-filter) (locale/gen-edges @cache)))
  (take 10 (filter default-filter (locale/gen-edges @cache)))
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
  (let [agg-with-repo #(locale/aggregate % (env/resolve-repo %))
        with-output #(do (println (str (count repos) " repositories indexed: ")) %)]
    (->> repos
         (pmap agg-with-repo)
         (apply merge)
         (swap! cache #(identity %2))
         (count)
         (hash-map :manifests)
         (with-output))))

(defn list-connections
  "Lists connections as a nicely formatted table. Displays no more than 50 by default, or max if it
  is specified. The default filter includes only DDF Catalog and Spatial nodes, but no plugins."
  ([] (list-connections 50 [:node "ddf/.*"
                            :node ".*catalog.*|.*spatial.*"
                            :node "(?!.*plugin.*$).*"]))
  ([max f] (let [with-output #(do (clojure.pprint/print-table %)
                                  (str "Printed " (count %) " dependencies"))]
             (->> @cache
                  (locale/gen-edges)
                  (filter (filter->predicate f))
                  ;; by default, do not print duplicate dependencies for each cause
                  (map #(dissoc % :cause))
                  (distinct)
                  ;; --
                  (take max)
                  (with-output)))))

(defn draw-graph
  "Renders a graph of edges as HTML and opens the file in the browser. The default filter includes
  only DDF Catalog and Spatial nodes, but no plugins."
  ([] (draw-graph [:node "ddf/.*"
                   :node ".*catalog.*|.*spatial.*"
                   :node "(?!.*plugin.*$).*"]))
  ([f] (->> @cache
            (locale/gen-edges)
            (filter (filter->predicate f))
            (graph/gen-html-from-edges)
            (graph/!write-html)
            (!open-file-in-browser))))