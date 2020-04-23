(ns com.connexta.osgeyes.cmds

  "This is the CLI interface of the application, literally speaking. This is the namespace that
  is pre-loaded into the REPLy instance. Public functions that live here will be discoverable by
  end users as part of the application.")

(defn say-hello [& args]
  (let [chain (reduce #(str %1 " and " %2) (map str args))]
    (str "Hello " chain "!")))