(ns com.connexta.osgeyes.cmds

  "This is the CLI interface of the application, literally speaking. This is the namespace that
  is pre-loaded into the REPLy instance. Public functions that live here will be discoverable by
  end users as part of the application. Refer to the development notes below for details on how
  things work and how they can be extended. Actual function defs begin below the notes section."

  (:require [com.connexta.osgeyes.env :as env]
            [com.connexta.osgeyes.graph :as graph]
            [com.connexta.osgeyes.options :as opts]
            [com.connexta.osgeyes.files.manifest :as manifest]
            [com.connexta.osgeyes.index.core :as index])
  (:import (java.awt Desktop)
           (java.io File)))

;; ----------------------------------------------------------------------
;; # Development Notes
;;
;; Notes about data structures, new functions, and opportunities for planning
;; ahead future capability iterations.
;;

(comment
  "Tentative game plan is to introduce concept of 'artifact facets' and avoid any indirection
  besides basic selection attributes (i.e. use a :blueprint keyword if you want that data included,
  etc). Also remember that an 'index' is currently just a Map of String 'repo/bundle-symbolic-name'
  to manifest maps."

  ;; Current representation
  {"ddf/symbolic-name" {::manifest/Manifest-Version    "1.0"
                        ::manifest/Bundle-SymbolicName "symbolic-name"
                        #_etc}}

  ;; Artifact facets ('facet map'), then 'facet maps' plural would be 'artifacts'
  {"mvn:group/art/ver" {:manifest  {::manifest/Manifest-Version    "1.0"
                                    ::manifest/Bundle-SymbolicName "symbolic-name"
                                    #_etc}
                        :pom       {}
                        :blueprint {}
                        :feature   {}
                        #_etc}}

  ;; Transform to get back to "old" way of doing it
  (into {} (map #(vector %1 (get "manifest" %2))))

  "Considering a way to manage facets so you needn't always make requests to the mvn-indexer module.
  This could probably be done by simple import/export but the data needs to be more or less stable."

  ;; Consider keeping import/export of 'facet' data
  ;; Could also support JSON pretty easily
  (defn facet-export [] ())
  (defn facet-import [] ())

  "Considering a way to change preferences or default options (gathering, selections, etc) during
  runtime and then just re-dump the raw EDN each time."

  ;; Consider adding a way to read, change, and clear options; such as the maven roots you want to
  ;; gather for analysis.
  (defn mvn-roots-show)
  (defn mvn-roots-add)
  (defn mvn-roots-clear)
  ;; The maven roots, along with other stuff like selections, would be part of one big document of
  ;; options that you manage.
  (defn options-show)
  ;; Maybe we add named configurations, which would be easy to reference on a CLI
  (defn profile-add)
  (defn profile-clear)

  "Not actually sure how I feel about the below; might as well just use git at this point."

  ;; Using the above as a foundation, what if we provide one level of backup in case we bork our
  ;; options? Keeping options means we're stable and won't rollback; what we have now becomes our
  ;; safety point. Rolling back options resets us to our last safety point or system defaults.
  (defn options-keep)
  (defn options-rollback)

  "But we are definitely moving toward every command getting fed a full document (map) of options
  which just get merged from varying levels of control (global defaults, user options, and anything
  in the CLI input)."

  ;; Need a more comprehensive 'config' definition which targets precisely what to do.
  {:gather-from ["mvn:ddf/ddf/${VERSION}"
                 "mvn:other/other/${VERSION}"]
   :select      [:node "ddf/.*" :node ".*/catalog.*"]}

  "I like this format - 'ds' being a downstream project of some sort."

  {;; Manually specify named gathering configurations used to query maven and generate an initial
   ;; seq of artifacts to process.
   :gatherings  {:gather/ddf-2.19.5 ["mvn:ddf/ddf/2.19.5"]
                 :gather/ddf-2.23.9 ["mvn:ddf/ddf/2.23.9"]
                 :ds-1.0.5          ["mvn:ddf/ddf/2.19.5" "mvn:ds/ds/1.0.0"]
                 :ds-1.8.2          ["mvn:ddf/ddf/2.23.9" "mvn:ds/ds/1.8.2"]}

   ;; But depending on how the 'names' are referenced (i.e. a namespace is dynamically generated
   ;; with keywords or symbols) why could we not just grab this info from GitHub?
   :gatherings2 (poll-from "https:github.com/blah/blah" any other args)

   ;; Selections by name, which can be composed on the CLI
   :selections  {:select/ddf-catalog-only [:node "ddf/.*" :node ".*/catalog.*"]
                 :select/ds-catalog-only  [:node "ds/.*" :node ".*/catalog.*"]}

   ;; Presentation options by name, the fn's don't exist they're just examples
   :present     {:present/example [(graph/clustering :gen 3)
                                   (graph/coloring "ddf" :blue)]}

   ;; Defaults that fire when omitted on the CLI
   :defaults    {:gathering :gather/ddf-2.19.5
                 :selection :select/ddf-catalog-only}

   ;; Groups of named defaults that fire when omitted on the CLI
   :profiles    {:my-profile {:gathering :gather/ddf-2.19.5
                              :selection :select/ddf-catalog-only}}}

  "Could qualify some special keywords just for the CLI REPL context so we get autocomplete. Since
  all production code is qualified using 'com.connexta.osgeyes' it's probably not a big deal."

  {:gather/ddf-2.19.5       ["mvn:ddf/ddf/2.19.5"]
   :gather/ddf-2.23.9       ["mvn:ddf/ddf/2.23.9"]
   :select/ddf-catalog-only [:node "ddf/.*" :node ".*/catalog.*"]
   :select/ds-catalog-only  [:node "ds/.*" :node ".*/catalog.*"]}

  "Eventually, with some pre/post-processing macros, the CLI could get very close to a flat,
  traditional experience; it would be hard to tell the app was a Clojure REPL at all."

  (draw-graph :gather :ddf-2.19.5 :select :ddf-catalog-only)
  (draw-graph :profile :my-profile :select [])
  (diff-graph :from :gather/ddf-2.19.5 :to :gather/ddf-2.23.9 :select/ddf-catalog-only)
  (diff-graph from gather/ddf-2.19.5 to gather/ddf-2.23.9 select/ddf-catalog-only)

  "Ultimately it might be worth (down the road) looking into a simple CLI wrapper that delegates to
  the Clojure REPL so all the namespace dynamics are no longer necessary but that means portability
  between environments might get sacrificed (if it isn't already). Those implications are unknown."

  (comment))

;; ----------------------------------------------------------------------
;; # Defaults
;;

(def ^:private default-gather
  "Default aggregator poms for gathering artifacts. For now, just gather nodes from some
  known good build of DDF."
  ["mvn:ddf/ddf/2.19.5"])

(def ^:private default-select
  "Default selection of artifacts from the gathering. The default selection includes
  only DDF Catalog and Spatial nodes, but no plugins."
  [:node "ddf/.*" :node ".*catalog.*|.*spatial.*" :node "(?!.*plugin.*$).*"])

;; ----------------------------------------------------------------------
;; # Maven convenience functions.
;;

(defn mvn
  "Create a traditional mvn coordinate string of the form: 'mvn:groupId/artifactId/version'
  for use in specifying gather targets."
  ([name ver]
   (mvn name name ver))
  ([group artifact ver]
   (str "mvn:" group "/" artifact "/" ver)))

(defn gav
  "Create a GAV (groupId, artifactId, version) mapping from a traditional mvn coordinate
  string for use in specifying gather targets."
  [mvn-str]
  (let [throw-arg-check
        #(throw (IllegalArgumentException. (str "Invalid input string: (gav \"" mvn-str "\")")))]
    (when (not (.startsWith mvn-str "mvn:")) (throw-arg-check))
    (let [prefix-stripped (.substring mvn-str 4)
          parts (.split prefix-stripped "/")]
      (when (not= 3 (count parts)) (throw-arg-check))
      ;; The following is not necessary because of how (.split) behaves
      #_(when (->> parts (map nil?) (reduce #(or %1 %2))) (throw-arg-check))
      {:g (first parts)
       :a (second parts)
       :v (last parts)})))

;; ----------------------------------------------------------------------
;; # REFACTORING
;;
;; Temporary. Private helpers.
;;

(defn- gen-edges
  "Given a locale, returns a list of edges."
  [locale]
  (let [connectors [manifest/locale->edges]]
    (->> connectors
         (map #(% locale))
         (flatten)
         (distinct))))

(defn- aggregate-from-m2 [g a v]
  (->> (index/gather-hierarchy g a v)
       (filter #(= (:packaging %) "bundle"))
       (filter #(= (:file-ext %) "jar"))
       ;; Add keyword mapping / insolation for mvn-indexer attributes TODO
       (map #(get-in % [:attrs "JAR_MANIFEST"]))
       (map #(manifest/parse-content %))
       (map #(vector (str a "/" (::manifest/Bundle-SymbolicName %)) %))
       (into {})))

;; ----------------------------------------------------------------------
;; # Public CLI
;;
;; Commands the user invokes directly.
;; Convenience commands for navigating to specific directories.
;;

(defn- !open-file-in-browser [path] (.browse (Desktop/getDesktop) (.toURI (File. ^String path))))
(defn- !open-dir [dir] (do (!open-file-in-browser dir) (str "Navigating to " dir)))
(defn open-tmp-dir [] (!open-dir (env/resolve-tmp "")))
(defn open-working-dir [] (!open-dir (env/resolve-subdir "")))
(defn open-repos-dir [] (!open-dir (env/resolve-repo "")))

(defn list-edges
  "Lists the edges of a graph in a nicely formatted table.
    :gather - vector of mvn coordinates to serve as roots to the artifact trees.
    :select - selection vector for filtering the graph.
    :max    - maximum number of rows in the table (defaults to 100).
    :cause? - should each edge's cause be included as a column (defaults to false)?
    :type?  - should each edge's type be included as a column (defaults to false)?
  Note that enabling cause? might expose multiple connections between the same nodes,
  but for different reasons (i.e. service dependency vs bundle dependency, etc)."
  [& {:keys [gather select max cause? type?]
      ;; :as   all
      :or   {gather default-gather
             select default-select
             max    100
             cause? false
             type?  false}}]
  (let [dissoc-cause #(dissoc % :cause)
        dissoc-type #(dissoc % :type)]
    (->> gather
         (map gav)
         (map #(aggregate-from-m2 (:g %) (:a %) (:v %)))
         (apply merge)
         (gen-edges)
         (filter (opts/selection->predicate select))
         ;; optionally print duplicate dependencies for each cause
         (#(if cause? % (distinct (map dissoc-cause %))))
         ;; optionally print the type of edge
         (#(if type? % (map dissoc-type %)))
         ;; don't show more than the maximum
         (take max)
         (#(do (clojure.pprint/print-table %)
               (str "Printed " (count %) " dependencies"))))))

(defn draw-graph
  "Renders a graph of edges as HTML and opens the file in the browser.
    :gather - vector of mvn coordinates to serve as roots to the artifact trees.
    :select - selection vector for filtering the graph.
  The HTML file is saved to the user's tmp directory. Run (open-tmp-dir) to find it."
  [& {:keys [gather select]
      ;; :as   all
      :or   {gather default-gather
             select default-select}}]
  (->> gather
       (map gav)
       (map #(aggregate-from-m2 (:g %) (:a %) (:v %)))
       (apply merge)
       (gen-edges)
       (filter (opts/selection->predicate select))
       (graph/gen-html-from-edges)
       (graph/!write-html)
       (!open-file-in-browser)))

(defn export-graph
  "Exports a graph of edges as GraphML and opens the file in the browser.
    :gather - vector of mvn coordinates to serve as roots to the artifact trees.
    :select - selection vector for filtering the graph.
  The XML file is saved to the user's tmp directory. Run (open-tmp-dir) to find it."
  [& {:keys [gather select]
      ;; :as   all
      :or   {gather default-gather
             select default-select}}]
  (->> gather
       (map gav)
       (map #(aggregate-from-m2 (:g %) (:a %) (:v %)))
       (apply merge)
       (gen-edges)
       (filter (opts/selection->predicate select))
       (graph/gen-graphml-from-edges)
       (graph/!write-graphml)
       (#(str "Exported to " % (System/lineSeparator) "Call (open-tmp-dir) to navigate there."))))

;; ----------------------------------------------------------------------
;; # Namespace execution samples & support functions.
;;
;; Pre-defined evaluation samples for the above.
;;

(defn invoke-with
  "Takes a function f that requires named args and invokes it using the
  provided named-args map."
  [f named-args]
  (->> (seq named-args) (apply concat) (apply f)))

(defn show-args
  [& {:keys [gather select]
      :or   {gather default-gather
             select default-select}}]
  {:gather gather :select select})

(comment
  default-gather
  (show-args)
  (show-args :gather [(mvn "ddf" "2.23.1")])
  ;; Testing
  (list-edges)
  (draw-graph)
  (list-edges :gather [(mvn "ddf" "2.23.1")] :max 200 :cause? true :type? true)
  (draw-graph :gather [(mvn "ddf" "2.23.1")])
  ;; Mvn / GAV
  (mvn "ddf" "2.19.5")
  (mvn "group" "ddf" "12.17.8")
  (gav "mvn:ddf/ddf/2.19.5")
  (gav "hi")
  (gav "mvn:")
  (gav "mvn://")
  ;; Indexer Lifecycle
  (index/open-indexer!)
  (index/close-indexer!)
  ;; Artifact aggregation from maven
  (index/gather-hierarchy "ddf" "ddf" "2.19.5")
  (aggregate-from-m2 "ddf" "ddf" "2.19.5")
  ;; Composition ideas
  (draw-graph :select [])
  (draw-graph-with :select [])
  (draw-graph-with {:select []})
  (draw-graph (with :select []))
  (draw-graph (with-opts {:select []}))
  ;; Invocation & mapping
  (invoke-with new-draw-graph {:select [] :gather [] :extra 0}))