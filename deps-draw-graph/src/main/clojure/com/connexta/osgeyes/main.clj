(ns com.connexta.osgeyes.main

  "Entry point for the customized REPLy instance and general app bootstrapping tasks. Extension
  is provided by supplying code via REPLy's :custom-eval option."

  (:require [reply.main :as reply]
            [com.connexta.osgeyes.env :as env]
            [com.connexta.osgeyes.index.core :as index]))

;; ----------------------------------------------------------------------
;; # Init & Main
;;

(def ^:private application-help
  "Custom help text."
  '(do
     (println)
     (println "  ------------------------------------------------------------------------------")
     (println "  Welcome to OSG-Eyes, the dependency visualization toolkit")
     (println)
     (println "        Exit: Control+D or (exit) or (quit)")
     (println "  ------------------------------------------------------------------------------")
     (println "       Props: GATHER - vector of mvn coords pointing to project hierarchy root poms")
     (println "                        [\"mvn:groupId/artifactId/version\" ...]")
     (println "                        [(mvn \"GROUPID == ARTIFACTID\" \"VERSION\") ... ]")
     (println "              SELECT - vector of alternating keyword-regex or other selections")
     (println "                        [:node \"artifactIdFromGather/.*\" :node \".*/.*catalog.*\"]")
     (println "                        [:node \"artifactIdFromGather/.*\" [:node \".*/.*catalog.*\"]]")
     (println "  ------------------------------------------------------------------------------")
     (println "    Commands: (help)")
     (println "              (list-edges :gather GATHER :select SELECT")
     (println "                          :max 100 :cause? false :type? false)")
     (println "              (draw-graph :gather GATHER :select SELECT)")
     (println "              (export-graph :gather GATHER :select SELECT)")
     (println "  ------------------------------------------------------------------------------")
     (println "  Management: (load-file PATH)")
     (println "              (open-repos-dir)")
     (println "              (open-tmp-dir)")
     (println "              (open-working-dir)")
     (println "  ------------------------------------------------------------------------------")
     ;; Eventually support dynamic docs from the Clojure Fn docstrings
     #_(println "        Docs: (doc function-name-here)")
     #_(println "              (find-doc \"part-of-name-here\")")
     #_(println "Find by Name: (find-name \"part-of-name-here\")")))

(def ^:private application-init
  "The following snippet evals to bootstrapping tasks for the app's REPL ns. This code creates
  the 'osgeyes' ns on-the-fly and switches the REPL to its scope. All functions in
  'com.connexta.osgeyes.cmds' are pulled in so they needn't be qualified for use. Any immediate
  child directories of 'user.dir' are bound to the 'osgeyes' ns as symbols for use in the CLI
  along with autocomplete."
  (concat
    (list 'do
      '(ns osgeyes
         (:require [com.connexta.osgeyes.env :as env])
         (:use [com.connexta.osgeyes.cmds]))
      (list 'defn 'help "Displays the help text." [] application-help))
    (map #(list 'def (symbol %) %) (env/list-subdirs))))

(defn -main
  "Entry point for the customized REPLy instance."
  [& args]
  (let [[options banner]
        (try (reply/parse-args args)
             (catch Exception e
               (println (.getMessage e))
               (reply/parse-args ["--help"])))]
    (try
      (index/open-indexer!)
      (if (:help options)
        (println banner)
        (reply/launch (into options
                            {:custom-eval application-init
                             :custom-help application-help})))
      (finally
        (index/close-indexer!)
        (shutdown-agents)))))