(ns com.connexta.osgeyes.env

  "Env only exists to deal with environment-specific things. It provides functions for
  interacting with system configuration and resolving relative paths to repository files.
  It also helps keep comment blocks and code literals free of absolute path details when
  experimenting with local files in the REPL."

  (:require [clojure.string :as string])
  (:import (java.io File)))

(def ^:private tmp-dir (System/getProperty "java.io.tmpdir"))
(def ^:private app-dir (System/getProperty "user.dir"))
(def ^:private repos-dir (System/getProperty "repos.home"))
(def ^:private repos-root (if repos-dir repos-dir app-dir))

(defn- resolver-fn [target-root]
  (fn [rel-path]
    (let [root
          (if (string/ends-with? target-root "/")
            target-root
            (str target-root "/"))
          ;; Make this OS agnostic later
          rest-of-path
          (if (string/starts-with? rel-path "/")
            (throw (IllegalArgumentException.
                     (str "Path is absolute but should be relative: " rel-path)))
            rel-path)]
      (str root rest-of-path))))

(defn list-subdirs []
  (let [^String dir app-dir
        ^File dir-file (File. dir)]
    (->> dir-file
         (.listFiles)
         (vec)
         (map #(.getName %))
         (map #(first (string/split % #"\\.")))
         (filter #(not (string/starts-with? % "."))))))

(defn resolve-tmp "Resolves rel against tmp." [rel] ((resolver-fn tmp-dir) rel))
(defn resolve-subdir "Resolves rel against current working dir." [rel] ((resolver-fn app-dir) rel))
(defn resolve-repo "Resolves rel specifically for repos." [rel] ((resolver-fn repos-root) rel))