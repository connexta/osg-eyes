(ns com.connexta.osgeyes.env

  "Env only exists to deal with environment-specific things. It provides functions for
  interacting with system configuration and resolving relative paths to repository files.
  It also helps keep comment blocks and code literals free of absolute path details when
  experimenting with local files in the REPL."

  (:require [clojure.string :as string])
  (:import (java.io File)))

(def ^:private repos-root
  (let [repos-home (System/getProperty "repos.home")
        user-dir (System/getProperty "user.dir")]
    (if repos-home repos-home user-dir)))

(defn list-subdirs []
  (let [^String user-dir repos-root
        ^File dir-file (File. user-dir)]
    (->> dir-file
         (.listFiles)
         (vec)
         (map #(.getName %))
         (map #(first (string/split % #"\\.")))
         (filter #(not (string/starts-with? % "."))))))

(defn resolve-repo [rel-path]
  (let [root (if (string/ends-with? repos-root "/")
               repos-root
               (str repos-root "/"))
        ;; Make this OS agnostic later
        repo (if (string/starts-with? rel-path "/")
               (throw (IllegalArgumentException.
                        (str "Path is absolute but should be relative: " rel-path)))
               rel-path)]
    (str root repo)))