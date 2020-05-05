(ns com.connexta.osgeyes.graph
  "Graph manipulation and rendering."
  (:require [loom.graph :as lm-gra]
            [loom.attr :as lm-attr]
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
(comment
  (-> (lm-gra/digraph [:a :b] [:b :c] [:a :c])
      (lm-attr/add-attr-to-nodes :color "lightblue" [:b :c])
      (lm-attr/add-attr-to-edges :color "red" [[:a :b] [:b :c]])
      (lm-attr/add-attr :a :color "purple")
      (lm-attr/add-attr [:a :c] :color "black")
      (lm-attr/add-attr :b :type "bundle")
      (lm-attr/attrs :b)))

(defn- json-for-nodes [graph]
  (->> graph
       (lm-gra/nodes)
       (map #(merge
               (hash-map :id % :label %)
               (lm-attr/attrs graph %)))
       vec
       json/write-str))

(defn- json-for-edges [graph]
  (->> graph
       (lm-gra/edges)
       (map #(merge
               (hash-map :from (get % 0) :to (get % 1))
               (lm-attr/attrs graph %)))
       vec
       json/write-str))

;;
;; The following functions should take a graph and return a graph
;; They allow the inference of attributes from nodes and edges
;;

(defn- color-graph-by-qualstring [graph]
  (let [colors {"dd" "lightblue" "al" "wheat" "gs" "lightsalmon" "au" "lavender"}
        default "lightgray"]
    (->> graph
         lm-gra/nodes
         (reduce
           #(let [c (colors (subs %2 0 2))]
              (lm-attr/add-attr %1 %2 :color (if (nil? c) default c)))
           graph))))

(defn- enhance-graph [graph]
  (-> graph
      color-graph-by-qualstring))

(defn gen-html-from-graph
  "Takes a loom graph and generates interactive HTML using the vis.js library."
  [graph]
  (let [g (enhance-graph graph)]
    (-> viz-template
        (string/replace
          #"\"REPLACE_NODES\""
          (json-for-nodes g))
        (string/replace
          #"\"REPLACE_EDGES\""
          (json-for-edges g)))))

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