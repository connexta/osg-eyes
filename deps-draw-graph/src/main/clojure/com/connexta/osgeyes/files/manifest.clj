(ns com.connexta.osgeyes.files.manifest

  "Support for parsing manifest files with OSGi metadata. The manifest is an example of a
  single 'layer' of dependency information. Another fitting term would be 'connector' since
  these types of functions produce collections of edges, effectively linking nodes together.

  Note: While in alpha, the terminology to describe things is still subject to iteration.

  The public API supports specifying a path to a manifest file or supplying the file's content
  as a string directly. The result is a map representing the attributes in the manifest. Most
  values are either a standalone string or a coll of strings. The strings in colls tend to be
  fully qualified Java packages/classes/interfaces or Maven artifact names. One noteworthy
  exception is ::Embedded-Artfacts which still retains its properties from the manifest value.

  Regarding the OSGi-specific metadata, all properties describing packages/classes/interfaces
  have been filtered out and are not currently supported. This includes:
  * Version ranges
  * Blueprint properties
  * Service filters
  * Schema information

  The current capabilities are sufficient that a crude bundle graph could be generated from the
  data."

  (:require [clojure.string :as string]
            [com.connexta.osgeyes.env :as env])
  (:import (java.io StringReader BufferedReader)))

(def ^:private namespace-name (.toString *ns*))

(def ^:private manifest-attrs
  (set [::Manifest-Version
        ::Bnd-LastModified
        ::Build-Jdk
        ::Build-Jdk-Spec
        ::Built-By
        ::Bundle-Activator
        ::Bundle-Blueprint
        ::Bundle-Category
        ::Bundle-ClassPath
        ::Bundle-Description
        ::Bundle-DocURL
        ::Bundle-License
        ::Bundle-ManifestVersion
        ::Bundle-Name
        ::Bundle-SymbolicName
        ::Bundle-Vendor
        ::Bundle-Version
        ::Created-By
        ::Originally-Created-By
        ::Fragment-Host
        ::Embed-Transitive
        ::Embed-Dependency
        ::Embedded-Artifacts
        ::Conditional-Package
        ::DynamicImport-Package
        ::Export-Package
        ::Export-Service
        ::Import-Package
        ::Import-Service
        ::Provide-Capability
        ::Require-Capability
        ::Tool
        ::Service-Version
        ::Karaf-Commands
        ::Web-ContextPath
        ::Webapp-Context
        ::Main-Class
        ;; Temporary, should not couple to specific projects
        ::DDF-Mime-Type]))

;; ----------------------------------------------------------------------
;; # Manifest Graph Assembly
;;
;; Call chain for transforming parsed entities into a collection of edges.
;;

(defn- multimap-invert
  "Inverts a multi-value map.
  Turns a map whose values are colls into the reverse view where each value in each coll
  becomes a key mapped to its original key. For non-unique values in colls across different
  keys, the last one in wins.

  (collmap-inverted {:a [1 2] :b [2 3]}) => {1 :a, 2 :b, 3 :b}
  "
  [m]
  (->> m
       (map (fn [[k vs]] (map vector vs (repeat k))))
       (apply concat)
       (into {})))

(defn- multimap-collapse
  "Collapses a multi-value map into vector pairs.
  Turns a map whose values are colls into a coll of size-2 vectors representing the entire
  mapping, akin to a list of edges in a graph. Values in the vectors are not colls.

  (collmap-collapsed {:a [:x :y], :b [:s :t]}) => ([:a :x] [:a :y] [:b :s] [:b :t])
  "
  [m]
  (mapcat (fn [[k v]] (map vector (repeat k) v)) m))

(defn- extract-attr
  ;; Note! Locale currently assumed to be manifest exclusively, not iteration of data sources
  "Returns a simplified, unnested node->attribute map for the specified locale attribute."
  [attr locale]
  (into {} (map (fn [[k v]] [k (get v attr)]) locale)))

(defn- import-export-maps->edges
  "Generate dependency graph edges by matching imports->exports."
  [type artifact->imports artifact->exports]
  (let [artifact->import (multimap-collapse artifact->imports)
        export->artifact (multimap-invert artifact->exports)
        ;; keep this off for now, not all imports have a matching export (i.e. bundle zero exports)
        fail-on-no-exporter false]
    (->> artifact->import
         ;; the following approach assumes imports are the source of truth
         ;; handling this the opposite way shouldn't provide new, undetected edges
         (map #(let [node-import (first %)
                     node-export (export->artifact (second %))
                     cause (second %)]
                 (if (and fail-on-no-exporter (nil? node-export))
                   (throw (IllegalStateException.
                            (str "No export found for import " cause " in bundle " node-import)))
                   [node-import node-export cause])))
         (filter #(second %))
         ;; the importer depends on the exporter
         (map #(hash-map :from (get % 0) :to (get % 1) :cause (get % 2) :type type)))))

(defn locale->edges
  "Given a full scan of a locale instance, pull out the pieces of data the manifest can
  operate on and generate a normalized list of dependency graph edges. The definition
  of an edge is:
  {:from \"qual/node\" :to \"qual/node\" :cause \"thing.that.caused.connection\" :type \"type\"}."
  [locale]
  (flatten
    [(import-export-maps->edges
       "bundle/package"
       (extract-attr ::Import-Package locale)
       (extract-attr ::Export-Package locale))
     (import-export-maps->edges
       "bundle/service"
       (extract-attr ::Import-Service locale)
       (extract-attr ::Export-Service locale))]))

;; ----------------------------------------------------------------------
;; # Manifest Attribute Parsing

(def ^:private package-or-class-matcher
  (re-pattern
    (str
      ;; match some root package
      "([a-zA-Z]+)"
      ;; followed by one or more subpackages
      "(\\.[a-zA-Z0-9_]+)+"
      ;; and the entire matched package must be followed by a semicolon
      "(?=;)")))

(defn- handle-basic-csv [[k v]]
  [k (apply list (string/split v #","))])

(defn- handle-osgi-packages-and-classes [[k v]]
  [k (map first (re-seq package-or-class-matcher v))])

(defn- handle-bundle-symbolic-name [[k v]]
  [k (first (string/split v #";"))])

(defmulti ^:private parse-attr
          "Parses the manfiest attribute value according to its name. Expected
          input is a size-2 vector representing the key-value pair. Returns a
          size-2 vector with the value transformed appropriately."
          #(first %))

(defmethod ^:private parse-attr :default [attr] attr)
(defmethod ^:private parse-attr ::Bundle-SymbolicName [attr] (handle-bundle-symbolic-name attr))

(defmethod ^:private parse-attr ::Bundle-Blueprint [attr] (handle-basic-csv attr))
(defmethod ^:private parse-attr ::Embed-Dependency [attr] (handle-basic-csv attr))
(defmethod ^:private parse-attr ::Embedded-Artifacts [attr] (handle-basic-csv attr))
(defmethod ^:private parse-attr ::Conditional-Package [attr] (handle-basic-csv attr))

(defmethod ^:private parse-attr ::Import-Package [attr] (handle-osgi-packages-and-classes attr))
(defmethod ^:private parse-attr ::Export-Package [attr] (handle-osgi-packages-and-classes attr))
(defmethod ^:private parse-attr ::Import-Service [attr] (handle-osgi-packages-and-classes attr))
(defmethod ^:private parse-attr ::Export-Service [attr] (handle-osgi-packages-and-classes attr))

;; ----------------------------------------------------------------------
;; # Manifest File Parsing
;;
;; Call chain for turning a manifest file or content into a map. Handles two potential sources
;; of data, transform, some validation, and eventually dispatches to the above attribute-specific
;; parsing logic. Refer to the following REPL samples.
;;

(comment
  (->> (str "Manifest-Version: 1.0\nBuild-Jdk: 1.8.0_131\n"
            "Bundle-DocURL: http://c\n odice.org\nInvalid: haha I snuck in ;) how did I do that?")
       parse-string-to-lines
       (valid-manifest? "REPL test")
       (reduce reduce-lines [])
       (map line->pair))
  (->> "ddf/catalog/spatial/csw/spatial-csw-endpoint/target/classes/META-INF/MANIFEST.MF"
       env/resolve-repo
       parse-path-to-lines)
  (->> "ddf/catalog/spatial/csw/spatial-csw-endpoint/target/classes/META-INF/MANIFEST.MF"
       env/resolve-repo
       parse-file))

(defn- parse-path-to-lines
  [path]
  (with-open [rdr (clojure.java.io/reader path)]
    (doall (line-seq rdr))))

(defn- parse-string-to-lines
  [raw-string]
  (with-open [rdr (BufferedReader. (StringReader. raw-string))]
    (doall (line-seq rdr))))

(defn- valid-manifest?
  "Ensures the first line of the manifest does not begin with whitespace."
  [path lines]
  (if (string/starts-with? (first lines) " ")
    (throw (IllegalArgumentException.
             (str "Manifest file " path " should not start with whitespace")))
    lines))

(defn- reduce-lines
  "Aggregates manifest lines according to attribute, where val is expected to be the target
  vector of strings for the aggregation, and next is the next line from the manifest file."
  [val next]
  (if (string/starts-with? next " ")
    (update val
            (- (count val) 1)
            #(str % (string/trim next)))
    (conj val next)))

(defn- line->pair
  "Converts a single line of the manifest into a key-value pair, in this case a size-2 vector."
  [line]
  (let [kv-split (string/split line #": " 2)
        k (keyword namespace-name (first kv-split))
        v (last kv-split)]
    (when (or (nil? line) (.isEmpty line))
      (throw (IllegalArgumentException. (str "Bad manifest attribute for line '" line "'"))))
    [k v]))

(defn valid-keys?
  "Ensures all parsed manifest keys are valid."
  [path pairs]
  (let [check
        (fn [[k v]]
          (if (contains? manifest-attrs k)
            [k v]
            (throw (IllegalArgumentException.
                     (str "Unexpected manifest attribute '" k "' in " path)))))]
    (map check pairs)))

(defn parse-file
  "Parses the text of a jar's manifest from a file pointed to by the path, passed as a string."
  [path]
  (->> path
       parse-path-to-lines
       (filter #(not= "" %))
       (valid-manifest? path)
       (reduce reduce-lines [])
       (map line->pair)
       (valid-keys? path)
       (map parse-attr)
       (into {})))

(defn parse-content
  "Parses the text of a jar's manifest from the already loaded string content of the file."
  [content]
  (->> content
       parse-string-to-lines
       (filter #(not= "" %))
       (valid-manifest? "memory")
       (reduce reduce-lines [])
       (map line->pair)
       (valid-keys? "memory")
       (map parse-attr)
       (into {})))