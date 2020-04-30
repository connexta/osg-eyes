(ns com.connexta.osgeyes.locale

  "Support for the aggregation of dependency data.

  Locales are functions that represent the different ways nodes or node collections can be
  gathered or discovered. Some examples include:
  * Directory structure of the source code itself post-build ('project' locale - the default)
  * Any Maven repository ('m2' locale)
  * Interacting with an OSGi container directly ('karaf' locale)

  Note that all of the above examples act as suppliers of nodes in the dependency graph and
  depending on the granularity, could also be treated as suppliers of node lists for the graph.

  For now only the project locale is supported and it assumes the project is a Maven project."

  (:require [clojure.java.io :as io]
            [com.connexta.osgeyes.manifest :as manifest]))

;; ----------------------------------------------------------------------
;; # Repository Manifests
;;
;; Discover manifest files in a repository. At some point this needs to be decoupled from
;; the locale. These are technically connector predicates that determine if they can handle
;; the provided file.
;;

(defn- is-manifest? [file]
  (and
    (= (.getName file) "MANIFEST.MF")
    (.contains (.getParent file) "/target/classes/META-INF")))

;; ----------------------------------------------------------------------
;; # Repository Graph Generation
;;
;; Dispatch control for going from a locale instance, through all available connectors, to a final
;; collection of edges.
;;

(defn gen-edges
  "Given a locale, returns a list of edges."
  [locale]
  (let [connectors [manifest/locale->edges]]
    (->> connectors
         (map #(% locale))
         (flatten)
         (distinct))))

;; ----------------------------------------------------------------------
;; # Repository Discovery
;;
;; Discover relevant files in a repository and aggregate the results. There are several approaches
;; to gathering the information. The search function can check each file, like it does now, and
;; provide the file to a connector function that knows how to handle it. Alternatively, the search
;; function can simply provide the node or node collection directory where data lives and allow any
;; connectors to pull out the file they operate on. This will be subject to change over time.
;;
;; For the time being, a neutral node identifier of Bundle-SymbolicName is being pulled from the
;; manifest and namespaced by source project. This works because most projects forward the
;; artifactId to be used as the symbolic name. In the future this should also be decoupled from
;; manifests and the identifier should get pulled from the pom manually as a separate task.
;;

(defn- get-relevant-file-paths
  "Given a string path to a root Maven project, returns a coll of absolute, fully
  qualified path strings that point to Karaf feature files."
  [root-project-path]
  (->> root-project-path
       io/file
       ;; Revisit performance later - consider pulling from file-seq in parallel and not
       ;; waiting for manifest filtering and path mapping
       file-seq
       (filter is-manifest?)
       (map #(.getPath %))))

(defn aggregate
  "Collects nodes from an instance of a locale and returns dependency information across all
  available connectors. Takes a string used to qualify the named results for this instance of
  locale and a fully qualified path to the root pom.xml file of the project to aggregate across."
  [qual path]
  (->> (get-relevant-file-paths path)
       ;; Does Clojure have a "cold start" w.r.t threading? - might be REPL / JVM related
       (pmap manifest/parse-file)
       (map #(vector (str qual "/" (::manifest/Bundle-SymbolicName %)) %))
       (into {})))
