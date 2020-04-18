(ns com.connexta.osgeyes.main
  "Entry point for the customized REPLy instance and general app bootstrapping tasks."
  (:require [reply.main :as reply])
  (:gen-class))

(defn -main [& args]
  (let [[options banner]
        (try (reply/parse-args args)
           (catch Exception e
              (println (.getMessage e))
              (reply/parse-args ["--help"])))]
    (if (:help options)
      (println banner)
      (reply/launch options))
    (shutdown-agents)))