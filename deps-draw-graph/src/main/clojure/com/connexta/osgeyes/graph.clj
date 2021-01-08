(ns com.connexta.osgeyes.graph
  "Graph manipulation and rendering."
  (:require [loom.graph :as lm-gra]
            [loom.attr :as lm-attr]
            [com.connexta.osgeyes.env :as env]
            [clojure.xml :as xml]
            [clojure.string :as string]
            [clojure.data.json :as json]
            [clojure.java.io :as io]))

(def ^:private graphml-file (env/resolve-tmp "exported.graphml"))
(def ^:private viz-file (env/resolve-tmp "viz.html"))
(def ^:private viz-resource "templates/graph-output.html")
(def ^:private viz-template (->> viz-resource io/resource slurp))

;; ----------------------------------------------------------------------
;; # Development Notes
;;
;; Notes about data structures, new functions, and opportunities for planning
;; ahead future capability iterations.
;;

(comment
  "Notes regarding vis.js"
  "----------------------"

  "* Node Map"
  {:id 0 :label "node0" :group 0}

  "* Edge Map"
  {:from 1 :to 2 :shadow {:color "rgb(0,255,0)"}}
  {:from 1 :to 2 :shadow {:color "rgba(97,195,238,0.5)"}}
  {:from 1 :to 2 :shadow {:color "#7BE141"}}
  {:from 1 :to 2 :shadow {:color "lime"}}
  {:from 1 :to 2 :shadow {:color {:background "pink"
                                  :border     "purple"}}}
  {:from 1 :to 2 :shadow {:color {:background "#F03967"
                                  :border     "#713E7F"
                                  :highlight  {:background "red" :border "black"}
                                  :hover      {:background "red" :border "black"}}}}

  "* Configuration options"

  "** Shadows"
  {:nodes {:shape       "dot"
           :size        30
           :font        {:size 32}
           :borderWidth 2
           :shadow      true}
   :edges {:width  2
           :shadow true}}

  (comment))

;; ----------------------------------------------------------------------
;; # Graphs -> GraphML
;;
;; Call chain for transforming Loom graphs into XML graphs for importing.
;;

;; (.replace \' \")
;; (.replace "&" "&amp;")
;; (spit path xmlstr :create true)
(defn- graphml-write
  "Emits a graphml XML string given a root node."
  [root]
  (-> (with-out-str (xml/emit root))
      (string/replace #"'" "\"")))

;; <graphml xmlns="http://graphml.graphdrawing.org/xmlns">
(defn- create-graphml-root
  "Returns a populated <graphml/> XML node with the seq of nodes in 'content' as its children."
  [content]
  {:tag     :graphml
   :attrs   {:xmlns "http://graphml.graphdrawing.org/xmlns"}
   :content (vec content)})

;; <graph id="G" edgedefault="directed">
(defn- create-graphml-graph
  "Returns a populated <graph/> XML node with the seq of nodes in 'content' as its children."
  [content]
  {:tag     :graph
   :attrs   {:id "exported" :edgedefault "directed"}
   :content (vec content)})

;; <node id="n0"/>
(defn- create-graphml-node
  "Returns a populated <node/> XML node with the provided id."
  ([id]
   {:tag   :node
    :attrs {:id id}})
  ([id children]
   (let [xmlnode (create-graphml-node id)]
     (if (empty? children)
       xmlnode
       (assoc xmlnode :content (vec children))))))

;; <edge source="n0" target="n2"/>
(defn- create-graphml-edge
  "Returns a populated <edge/> XML node linking the provided node ids."
  ([from-id to-id]
   {:tag   :edge
    :attrs {:source from-id :target to-id}})
  ([from-id to-id children]
   (let [xmlnode (create-graphml-edge from-id to-id)]
     (if (empty? children)
       xmlnode
       (assoc xmlnode :content (vec children))))))

;; <key id="d1" for="edge" attr.name="weight" attr.type="double"/>
(defn- create-graphml-key
  "Returns a populated <key/> XML node with the provided attribute declaration details."
  [key-name for type]
  (comment for (any-of :graph :node :edge :all))
  (comment type (any-of :boolean :int :long :float :double :string))
  {:tag   :key
   :attrs {:id        (name key-name)
           :for       (name for)
           :attr.name (name key-name)
           :attr.type (name type)}})

;; <data key="d1">1.0</data>
(defn- create-graphml-data
  "Returns a populated <data/> XML node with the provided key and value."
  [key val]
  {:tag     :data
   :attrs   {:key (name key)}
   :content [(if (keyword? val) (name val) val)]})

(defn- graphml-node-mapper
  "Produces a mapping function for creating graphml xml nodes."
  [graph]
  (fn [nodeid]
    (create-graphml-node
      nodeid
      (->> nodeid
           (lm-attr/attrs graph)
           (seq)
           (map #(apply create-graphml-data %))))))

(defn- graphml-edge-mapper
  "Produces a mapping function for creating graphml xml edges."
  [graph]
  (fn [edgevec]
    (let [from (get edgevec 0)
          to (get edgevec 1)]
      (create-graphml-edge
        from
        to
        (->> (lm-attr/attrs graph from to)
             (seq)
             (map #(apply create-graphml-data %)))))))

(defn gen-graphml-from-graph
  "Transforms a Loom graph into a graphml XML string."
  [graph]
  (let [graphml-nodes (map (graphml-node-mapper graph) (lm-gra/nodes graph))
        graphml-edges (map (graphml-edge-mapper graph) (lm-gra/edges graph))
        graphml-graph (list (create-graphml-graph (concat graphml-nodes graphml-edges)))
        graphml-keys (list
                       ;; nodes
                       (create-graphml-key :group-id :node :string)
                       (create-graphml-key :artifact-id :node :string)
                       (create-graphml-key :version :node :string)
                       (create-graphml-key :packaging :node :string)
                       (create-graphml-key :category :node :string)
                       ;; edges
                       (create-graphml-key :type :edge :string)
                       (create-graphml-key :cause :edge :string))]
    (->> (concat graphml-keys graphml-graph) (create-graphml-root) (graphml-write))))

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
      #_(lm-attr/attrs :b)
      (gen-graphml-from-graph)))

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

(defn- gen-html-from-graph
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

(defn gen-graphml-from-edges
  "Takes a coll of edges and generates interactive HTML using the vis.js library."
  [edges]
  (->> edges
       (map #(vector (:from %) (:to %)))
       (apply lm-gra/digraph)
       (gen-graphml-from-graph)))

(defn gen-html-from-edges
  "Takes a coll of edges and generates interactive HTML using the vis.js library."
  [edges]
  (->> edges
       (map #(vector (:from %) (:to %)))
       (apply lm-gra/digraph)
       (gen-html-from-graph)))

(defn !write-graphml
  "Writes the given string to the app's temp GRAPHML file and returns the path to that file."
  [graphml]
  (do (spit graphml-file graphml :create true) graphml-file))

(defn !write-html
  "Writes the given string to the app's temp HTML file and returns the path to that file."
  [html]
  (do (spit viz-file html :create true) viz-file))