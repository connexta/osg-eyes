(ns com.connexta.osgeyes.graph.env

  "Env only exists to deal with environment-specific things. It provides functions for
  interacting with system configuration and resolving relative paths to repository files.
  It also helps keep comment blocks and code literals free of absolute path details when
  experimenting with local files in the REPL."

  (:require [clojure.string :as str])
  (:import (java.io File)))

(def ^:private tmp-dir (System/getProperty "java.io.tmpdir"))
(def ^:private app-dir (System/getProperty "user.dir"))
(def ^:private repos-dir (System/getProperty "repos.home"))
(def ^:private repos-root (if repos-dir repos-dir app-dir))

(defn- resolve-as [target-root rel-path]
  (let [root
        (if (str/ends-with? target-root "/")
          target-root
          (str target-root "/"))
        ;; Make this OS agnostic later
        rest-of-path
        (if (str/starts-with? rel-path "/")
          (throw (IllegalArgumentException.
                   (str "Path is absolute but should be relative: " rel-path)))
          rel-path)]
    (str root rest-of-path)))

(defn list-subdirs
  "Lists subdirectories in the repos root directory. Directories cannot contain whitespace
  or a dot separator."
  []
  (let [^String dir repos-root
        ^File dir-file (File. dir)]
    (->> dir-file
         (.listFiles)
         (vec)
         (filter #(.isDirectory %))
         (map #(.getName %))
         ;; Make sure results can be used in defs
         (filter #(not (.contains % " ")))
         (filter #(not (str/starts-with? % "."))))))

(defn resolve-tmp "Resolves rel against tmp." [rel] (resolve-as tmp-dir rel))
(defn resolve-subdir "Resolves rel against current working dir." [rel] (resolve-as app-dir rel))
(defn resolve-repo "Resolves rel specifically for repos." [rel] (resolve-as repos-root rel))