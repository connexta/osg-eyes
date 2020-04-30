(ns com.connexta.osgeyes.graph
  "Graph manipulation and rendering."
  (:require [loom.graph :as lm-gra]
            [com.connexta.osgeyes.env :as env]
            [clojure.string :as string]
            [clojure.data.json :as json]
            [clojure.java.io :as io]))

(def ^:private viz-file (env/resolve-tmp "viz.html"))
(def ^:private viz-resource "templates/graph-output.html")
(def ^:private viz-template (->> viz-resource io/resource slurp))

;; ----------------------------------------------------------------------
;; # Graphs -> HTML
;;
;; Call chain for transforming Loom graphs into vis.js graphs for rendering.
;;

(defn- json-for-nodes [graph]
  (->> graph
       (lm-gra/nodes)
       (map #(hash-map :id % :label %))
       vec
       json/write-str))

(defn- json-for-edges [graph]
  (->> graph
       (lm-gra/edges)
       (map #(hash-map :from (get % 0) :to (get % 1)))
       vec
       json/write-str))

(defn gen-html-from-graph
  "Takes a loom graph and generates interactive HTML using the vis.js library."
  [graph]
  (-> viz-template
      (string/replace
        #"\"REPLACE_NODES\""
        (json-for-nodes graph))
      (string/replace
        #"\"REPLACE_EDGES\""
        (json-for-edges graph))))

(defn gen-html-from-edges
  "Takes a coll of edges and generates interactive HTML using the vis.js library."
  [edges]
  (->> edges
       (map #(vector (:from %) (:to %)))
       (apply lm-gra/digraph)
       (gen-html-from-graph)))

(defn !write-html
  "Writes the given string to the app's temp HTML file and returns the path to that file."
  [html]
  (do (spit viz-file html :create true) viz-file))