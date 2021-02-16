(ns com.connexta.osgeyes.graph.core

  "This is the CLI interface of the application, literally speaking. This is the namespace that
  is pre-loaded into the REPLy instance. Public functions that live here will be discoverable by
  end users as part of the application. Refer to the development notes below for details on how
  things work and how they can be extended. Actual function defs begin below the notes section."

  (:use [loom.graph]
        [loom.attr])
  (:require [com.connexta.osgeyes.graph.env :as env]
            [com.connexta.osgeyes.graph.export :as export]
            [com.connexta.osgeyes.graph.query :as query]
            [com.connexta.osgeyes.graph.connectors.manifest :as manifest]
            [com.connexta.osgeyes.index.core :as index]
            [ubergraph.core :as uber])
  (:import (java.awt Desktop)
           (java.io File)))

;;
;; ----------------------------------------------------------------------------------------------
;; Defaults
;; ----------------------------------------------------------------------------------------------
;;

(def ^:private default-gather
  "Default aggregator poms for gathering artifacts. For now, just gather nodes from some
  known good build of DDF."
  #_["mvn:ddf/ddf/2.19.5"]
  ["mvn:ddf/ddf/2.19.14"])

(def ^:private default-select
  "Default selection of artifacts from the gathering. The default selection includes
  only DDF Catalog and Spatial nodes, but no plugins."
  [:node "ddf/.*" :node ".*catalog.*|.*spatial.*" :node "(?!.*plugin.*$).*"])

;;
;; ----------------------------------------------------------------------------------------------
;; Maven convenience functions
;; ----------------------------------------------------------------------------------------------
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

;;
;; ----------------------------------------------------------------------------------------------
;; Categorization attribute.
;; ----------------------------------------------------------------------------------------------
;;

;; @formatter:off
(def ^:private categories
  "Category names and the criteria that defines them, in the order that they should be applied."
  (vector
    [:solr               #".*solr.*"]
    [:admin-core         #".*admin.*" #".*core.*"]
    [:admin              #".*admin.*"]
    [:catalog-core       #".*catalog.*" #".*core.*"]
    [:catalog-transform  #".*catalog.*" #".*transformer.*"]
    [:catalog-opensearch #".*catalog.*" #".*opensearch.*"]
    [:catalog-rest       #".*catalog.*" #".*rest.*"]
    [:catalog-validator  #".*catalog.*" #".*validator.*"]
    [:catalog-plugins    #".*catalog.*" #".*plugin.*"]
    [:catalog            #".*catalog.*"]
    [:spatial-csw        #".*spatial.*" #".*csw.*"]
    [:spatial-geocoding  #".*spatial.*" #".*geocoding.*"]
    [:spatial-kml        #".*spatial.*" #".*kml.*"]
    [:spatial-ogc        #".*spatial.*" #".*ogc.*"]
    [:spatial-wfs        #".*spatial.*" #".*wfs.*"]
    [:spatial            #".*spatial.*"]
    [:security-core      #".*security.*" #".*core.*"]
    [:security-claims    #".*security.*" #".*claims.*"]
    [:security-encrypt   #".*security.*" #".*encryption.*"]
    [:security-expand    #".*security.*" #".*expansion.*"]
    [:security-filter    #".*security.*" #".*filter.*"]
    [:security-handler   #".*security.*" #".*handler.*"]
    [:security-intercept #".*security.*" #".*interceptor.*"]
    [:security-policy    #".*security.*" #".*policy.*"]
    [:security-realm     #".*security.*" #".*realm.*"]
    [:security-rest      #".*security.*" #".*rest.*"]
    [:security-saml      #".*saml.*"]
    [:security-tokens    #".*token.*" #".*storage.*"]
    [:security-servlet   #".*security.*" #".*servlet.*"]
    [:security-sessions  #".*session.*" #".*management.*"]
    [:security           #".*security.*"]
    [:registry           #".*registry.*"]
    [:resourcemanagement #".*resourcemanagement.*"]
    [:persistence        #".*persistence.*"]
    [:action             #".*action.*"]
    [:metrics            #".*metrics.*|.*micrometer.*"]
    [:mime               #".*mime.*"]
    [:platform           #".*platform.*"]))
;; @formatter:on

(defn- categorize
  "Categorizes the provided input string."
  [str]
  (loop [cats (seq categories)]
    (if (empty? cats)
      :none
      (let [next-cat-vec (first cats)
            match? (reduce #(and %1 %2) (map #(boolean (re-matches % str)) (rest next-cat-vec)))]
        (if match?
          (first next-cat-vec)
          (recur (rest cats)))))))

(defn- add-category
  [graph qualname artifact]
  (let [id (get-in artifact [:maven :artifact-id])]
    (add-attr graph qualname :category (categorize id))))

(defn- add-api-flag
  [graph qualname artifact]
  (let [id (get-in artifact [:maven :artifact-id])]
    (add-attr graph qualname :api-flag (.contains id "api"))))

;;
;; ----------------------------------------------------------------------------------------------
;; Creates a Loom graph with artifact metadata embedded into the nodes and edges as attributes.
;; ----------------------------------------------------------------------------------------------
;;

(defn- add-disconnected-nodes-to-graph
  "Reducing function that ensures any node not inferrable from edges is still apart of the graph."
  [graph [qualname artifact]]
  (if (has-node? graph qualname)
    graph
    (add-nodes graph qualname)))

(defn- with-node-attrs
  "Reducing function that adds maven artifact metadata to graph node attributes."
  [graph [qualname artifact]]
  ;; If Loom complains about a 'string' not satisfying the 'Edge' protocol, it might mean
  ;; the node/edge doesn't exist so can't be used to tweak attributes
  (when (not (has-node? graph qualname))
    (throw (IllegalArgumentException. (str "Could not find " qualname " in nodes"))))
  (let [add-mvn-attr (fn [g node artifact key]
                       (add-attr g node key (get-in artifact [:maven key])))]
    (-> graph
        (add-category qualname artifact)
        (add-api-flag qualname artifact)
        (add-mvn-attr qualname artifact :group-id)
        (add-mvn-attr qualname artifact :artifact-id)
        (add-mvn-attr qualname artifact :version)
        (add-mvn-attr qualname artifact :packaging))))

(defn- artifacts->edges
  "Given a collection of artifacts, returns a list of edges."
  [artifact-map]
  (let [connectors [manifest/artifacts->edges]]
    (->> connectors
         (map #(% artifact-map))
         (flatten)
         (distinct))))

(defn create-graph-with-attrs
  "Given a collection of artifacts, creates a graph with original metadata preserved as
  graph attributes."
  [artifact-map]
  (let [edges (artifacts->edges artifact-map)
        ;; Vector used to setup (Uber)graph using edge descriptor: [source, destination, attributes]
        ;; Refer to README: https://github.com/Engelberg/ubergraph#edge-descriptions
        graph (->> edges (map #(vector (:from %) (:to %) %)) (apply uber/ubergraph true false))
        pairs (seq artifact-map)]
    (as-> graph g
          (reduce add-disconnected-nodes-to-graph g pairs)
          (reduce with-node-attrs g pairs))))

(comment
  ;; Preview raw graph
  (create-graph-with-attrs (create-artifact-map "ddf" "ddf" "2.19.5"))
  ;; Generate simpler, hard-coded graph
  (-> (uber/ubergraph true false [:a :b])
      (add-attr [:a :b] "k" "v")
      (add-nodes "mystr")
      (edges)
      (first)
      (dest))
  (comment))

;;
;; ----------------------------------------------------------------------------------------------
;; Aggregation chain that preserves the original data.
;; ----------------------------------------------------------------------------------------------
;;

(defn- add-manifest [artifact]
  (assoc artifact :manifest
                  ;; Add keyword mapping / insulation for mvn-indexer attributes TODO
                  (manifest/parse-content (get-in artifact [:maven :attrs "JAR_MANIFEST"]))))

(defn create-artifact-map-bundles-only [g a v]
  (->> (index/gather-hierarchy g a v)
       (filter #(= (:packaging %) "bundle"))
       (filter #(= (:file-ext %) "jar"))
       (map #(hash-map :maven %))
       (map add-manifest)
       (map #(vector (str a "/" (get-in % [:manifest ::manifest/Bundle-SymbolicName])) %))
       (into {})))

;;
;; ----------------------------------------------------------------------------------------------
;; Public CLI
;;
;; Commands the user invokes directly.
;; Convenience commands for navigating to specific directories.
;; ----------------------------------------------------------------------------------------------
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
         (map #(create-artifact-map-bundles-only (:g %) (:a %) (:v %)))
         (apply merge)
         (artifacts->edges)
         (filter (query/selection->predicate select))
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
       (map #(create-artifact-map-bundles-only (:g %) (:a %) (:v %)))
       (apply merge)
       (artifacts->edges)
       (filter (query/selection->predicate select))
       (export/gen-html-from-edges)
       (export/!write-html)
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
       (map #(create-artifact-map-bundles-only (:g %) (:a %) (:v %)))
       (apply merge)
       #_(artifacts->edges)
       (create-graph-with-attrs)
       ;; Fix filtering later TODO
       #_(filter (query/selection->predicate select))
       (export/gen-graphml-from-graph)
       (export/!write-graphml)
       (#(str "Exported to " % (System/lineSeparator) "Call (open-tmp-dir) to navigate there."))))

(comment
  (export-graph :select [:node "ddf/.*"])
  (list-edges)
  (draw-graph)
  (open-tmp-dir))

;;
;; ----------------------------------------------------------------------------------------------
;; Namespace execution samples & support functions.
;;
;; Pre-defined evaluation samples for the above.
;; ----------------------------------------------------------------------------------------------
;;

(comment
  ;; Defaults
  default-gather
  default-select

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
  (create-artifact-map-bundles-only "ddf" "ddf" "2.19.5"))