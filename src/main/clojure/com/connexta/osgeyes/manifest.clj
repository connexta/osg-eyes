(ns com.connexta.osgeyes.manifest

  "Support for parsing manifest files with OSGi metadata.

  The public API supports specifying a path to a manifest file or supplying the file's content
  as a string directly. The result is a map representing the properties in the manifest. Most
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
  data. "

  (:require [clojure.string :as string]
            [com.connexta.osgeyes.env :as env])
  (:import (java.io StringReader BufferedReader)))

(def ^:private namespace-name (.toString *ns*))

(def ^:private manifest-attrs
  (set [::Manifest-Version
        ::Bnd-LastModified
        ::Build-Jdk
        ::Built-By
        ::Bundle-Blueprint
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
        ::Embed-Dependency
        ::Embedded-Artifacts
        ::Export-Package
        ::Export-Service
        ::Import-Package
        ::Import-Service
        ::Require-Capability
        ::Tool]))

;; ----------------------------------------------------------------------
;; # Manifest Attribute Parsing

(comment (parse-content "Manifest-Version: 1.0"))

(def ^:private osgi-comma-splitter
  (re-pattern
    (str
      ;; match comma symbols such that...
      ","
      ;; the characters following the comma conform to... (begin positive lookahead)
      "(?="
      ;; any character, except closing parens / brackets, zero or more times
      "[^\\]\\)]*"
      ;; terminating with either the next comma, opening form, or end of input
      "(,|\\[|\\(|$)"
      ;; end positive lookahead block
      ")")))

(def ^:private osgi-package-splitter
  (re-pattern
    (str
      ;; match some root package at the beginning of the line
      "^([a-zA-Z]+)"
      ;; followed by one or more subpackages
      "(\\.[a-zA-Z0-9]+)+"
      ;; and the entire matched package is either followed by attributes or nothing
      "(?=;|$)")))

(defn- attr-package-like
  [[k v]]
  [k (->> (string/split v osgi-comma-splitter)
          (map #(first (first (re-seq osgi-package-splitter %))))
          (filter #(not (nil? %))))])

(defmulti ^:private parse-attr
          "Parses the manfiest attribute value according to its name. Expected
          input is a size-2 vector representing the key-value pair. Returns a
          size-2 vector with the value transformed appropriately."
          #(first %))

(defmethod ^:private parse-attr :default [attr] attr)

(defmethod ^:private parse-attr ::Embed-Dependency [[k v]] [k (apply list (string/split v #","))])
(defmethod ^:private parse-attr ::Embedded-Artifacts [[k v]] [k (apply list (string/split v #","))])

(defmethod ^:private parse-attr ::Import-Package [attr] (attr-package-like attr))
(defmethod ^:private parse-attr ::Export-Package [attr] (attr-package-like attr))
(defmethod ^:private parse-attr ::Import-Service [attr] (attr-package-like attr))
(defmethod ^:private parse-attr ::Export-Service [attr] (attr-package-like attr))

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
    [k v]))

(defn- valid-keys?
  "Ensures all parsed manifest keys are valid."
  [path pairs]
  (let [check
        (fn [[k v]]
          (if (contains? manifest-attrs k)
            [k v]
            (throw (IllegalArgumentException.
                     (str "Unexpected manifest attribute " k " in " path)))))]
    (map check pairs)))

(defn -sp-comma "Not for public consumption, do not call"
  [text]
  (string/split text osgi-comma-splitter))

(defn -sp-package "Not for public consumption, do not call"
  [text]
  (first (first (re-seq osgi-package-splitter text))))

(defn parse-file
  "Parses the text of a jar's manifest from a file pointed to by the path, passed as a string."
  [path]
  (->> path
       parse-path-to-lines
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
       (valid-manifest? "memory")
       (reduce reduce-lines [])
       (map line->pair)
       (valid-keys? "memory")
       (map parse-attr)
       (into {})))