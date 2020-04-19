(ns com.connexta.osgeyes.env
  "Docstring"
  (:require [clojure.string :as string]))

(def ^:private repos-root
  (let [repos-home (System/getProperty "repos.home")
        user-dir (System/getProperty "user.dir")]
    (if repos-home repos-home user-dir)))

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