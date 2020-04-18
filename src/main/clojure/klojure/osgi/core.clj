(ns klojure.osgi.core
  "
  Instead of running Clojure in the OSGi container itself, consumption of those APIs will be
  replaced with parsing of manifest files. The idea being future static analysis efforts can
  happen up front, near compilation, not at the end with the integration tests.
  ")

(defn bundles
  "Get all bundles installed in Karaf."
  []
  (throw (UnsupportedOperationException.
           "OSGi support is temporarily removed while it's being fixed - the function that
           was just invoked will need to be updated as part of the rewrite")))