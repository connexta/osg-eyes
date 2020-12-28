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
     (println "Welcome to OSG-Eyes, the dependency visualization toolkit")
     (println)
     (println "        Exit: Control+D or (exit) or (quit)")
     (println "    Commands: (help)")
     (println "              (list-edges SELECTION OPTIONS)")
     (println "              (list-edges [:node \".*\"] :max 50 :cause? false :type? false)")
     (println "              (draw-graph SELECTION)")
     (println "              (draw-graph [:node \".*\"])")
     (println "  Management: (load-file PATH)")
     (println "              (index-load PATH)")
     (println "              (index-repos REPO_PATH_1 REPO_PATH_2 ...)")
     (println "              (open-repos-dir)")
     (println "              (open-tmp-dir)")
     (println "              (open-working-dir)")
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
  `(do
     ~'(ns osgeyes
         (:require [com.connexta.osgeyes.env :as env])
         (:use [com.connexta.osgeyes.cmds]))
     ~`(def ~'help "Displays the help text." (fn [] ~application-help))
     ~@(map #(list 'def (symbol %) %) (env/list-subdirs))))

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

;; ----------------------------------------------------------------------
;; # Commentary
;;

(comment
  application-init
  "You might think that the below should work for an appropriate application-init value, but
  it doesn't. Why not? Hint: (def ...) is not what you think it is."
  (do
    (ns osgeyes
      (:require [com.connexta.osgeyes.env :as env])
      (:use [com.connexta.osgeyes.cmds]))
    (map #(def (symbol %) %) (env/list-subdirs))))

(comment
  "The result of the first eval of the application-init data structure defined above. This is
  actually what the code looks like during the 2nd evaluation when REPLy loads it."
  (do
    (ns osgeyes
      (:require [com.connexta.osgeyes.env :as env])
      (:use [com.connexta.osgeyes.cmds]))
    (def subdir1 "subdir1")
    (def subdir2 "subdir2")
    (def subdir3 "subdir3")
    ;; ...
    (def subdirN "subdirN")))