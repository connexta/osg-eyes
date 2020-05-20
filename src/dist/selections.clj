;; Convenience for negating a regex string, use like so: 
;; (def omit-ddf [:node (! "ddf/.*")])
(defn ! [regex] (str "(?!" regex "$).*"))

;; Repo selectors, bi-directional
(def only-ddf [:node "ddf/.*"])
(def only-alliance [:node "alliance/.*"])

;; Repo selectors, "satisfies"
(def only-to-ddf [:to "ddf/.*"])
(def only-to-alliance [:to "alliance/.*"])

;; Repo selectors, "needs"
(def only-from-ddf [:from "ddf/.*"])
(def only-from-alliance [:from "alliance/.*"])

;; Type selectors
(def only-package-deps [:type "bundle/package"])
(def only-service-deps [:type "bundle/service"])

;; Category selectors, "satisfies"
(def only-to-platform [:to ".*/.*platform.*" :to (! ".*/.*security.*")])
(def only-to-security [:to ".*/.*security.*"])
(def only-to-catalog [:to ".*/.*catalog.*"])
(def only-to-catalog-core [:to ".*/.*catalog.*" :to ".*/.*core.*"])
(def only-to-spatial [:to ".*/.*spatial.*"])
(def only-to-plugins [:to ".*/.*plugin.*"])

;; Category selectors, "needs"
(def only-from-platform [:from ".*/.*platform.*" :from (! ".*/.*security.*")])
(def only-from-security [:from ".*/.*security.*"])
(def only-from-catalog [:from ".*/.*catalog.*"])
(def only-from-catalog-core [:from ".*/.*catalog.*" :from ".*/.*core.*"])
(def only-from-spatial [:from ".*/.*spatial.*"])
(def only-from-plugins [:from ".*/.*plugin.*"])