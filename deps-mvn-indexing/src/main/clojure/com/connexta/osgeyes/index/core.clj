(ns com.connexta.osgeyes.index.core
  "Clojure wrapper code that changes with the Java code."
  (:require [clojure.string :as str])
  (:import
    com.connexta.osgeyes.index.IndexingApp
    org.apache.maven.index.ArtifactInfo))

(defn- artifact-info->map
  "Converts an org.apache.maven.index.ArtifactInfo into a map
  with keywords. Exclude keys with (artifact-info->map info #{:attrs})
  for previewing contents."
  ([^ArtifactInfo info]
   (artifact-info->map info #{}))
  ([^ArtifactInfo info exclusions]
   (let [mappings
         {:group-id      (-> info .getGroupId)
          :artifact-id   (-> info .getArtifactId)
          :version       (-> info .getVersion)
          :packaging     (-> info .getPackaging)
          :classifier    (-> info .getClassifier)
          :last-modified (-> info .getLastModified)
          :file-name     (-> info .getFileName)
          :file-ext      (-> info .getFileExtension)
          :path          (-> info .getPath)
          :attrs         (into {} (-> info .getAttributes))
          :size          (-> info .getSize)}]
     (->> mappings
          (filter (complement #(contains? exclusions (first %))))
          (merge (sorted-map))))))

(defn open-indexer!
  "Wrapper for IndexingApp#open."
  []
  (-> (IndexingApp/getInstance) (.open (IndexingApp/getRepoLocation))))

(defn close-indexer!
  "Wrapper for IndexingApp#close."
  []
  (-> (IndexingApp/getInstance) (.close)))

(defn- do-gather-hierarchy
  "Wrapper for IndexingApp#gatherHierarchy."
  [g a v]
  (-> (IndexingApp/getInstance) (.gatherHierarchy g a v)))

(defn gather-hierarchy
  "Returns a coll of maven artifacts that make up the tree defined
  by the given root. Tree membership is determined by the pom parent
  links. Returns all intermediate members. Filter on packaging if
  only interested in leaf nodes. Invoke:
  (gather-hierarchy NAME VERSION)
  if the group and artifact IDs are the same. Otherwise, use:
  (gather-hierarchy GROUP ARTIFACT VERSION)"
  ([ga v]
   (gather-hierarchy ga ga v))
  ([g a v]
   (map artifact-info->map (do-gather-hierarchy g a v))))

(comment
  (filter #(and (= "bundle" (:packaging %))
                (.contains (:artifact-id %) "core")
                (.contains (:artifact-id %) "security"))
          (gather-hierarchy "ddf" "2.19.5"))
  (open-indexer!)
  (gather-hierarchy "ddf" "2.23.1")
  (count (gather-hierarchy "ddf" "2.23.1"))
  (filter #(not (or (:classifier %) (:file-name %) (:path %))) (gather-hierarchy "ddf" "2.19.5"))
  (count (gather-hierarchy "ddf" "2.19.5"))
  (map #(artifact-info->map % #{:attrs}) (do-gather-hierarchy "ddf" "ddf" "2.19.5"))
  (map #(.toString %) (do-gather-hierarchy "ddf" "ddf" "2.19.5"))
  (close-indexer!))

(defn extract-packages
  "Extract packages from artifact map as sequence of vectors."
  [artifact-map]
  (as-> (get-in artifact-map [:jar-packages]) results
        (map #(vector % (dissoc artifact-map :jar-packages)) results)))

(defn reduce-packages
  "Combines like artifacts by package"
  [package-map next-package]
  (if (not (contains? package-map (first next-package)))
        (assoc package-map (first next-package) (list (last next-package)))
        (update package-map (first next-package) #(cons (last next-package) %))))

(defn do-package-search [package-name]
  (.searchPackages (IndexingApp/getInstance) package-name))

(defn match-package-search? [package-search item]
  (let [search-parts (.split package-search "\\*")]
    (every? #(.contains item %) search-parts)))

(defn package-search
  ;; Investigate non-trailing wildcards TODO
  "Search for packages. Supports ending * wildcards only."
  [package-search]
  (as-> (map artifact-info->map (do-package-search package-search)) results
        (filter #(contains? (:attrs %) "JAR_PACKAGES") results)
        (map #(into
                {:jar-packages (str/split (get-in % [:attrs "JAR_PACKAGES"]) #",")
                 :pom-parent   (get-in % [:attrs "POM_PARENT"])}
                (dissoc % :attrs))
             results)
        (mapcat extract-packages results)
        (reduce reduce-packages {} results)
        (filter #(match-package-search? package-search (first %)) results)))

(comment
  (count (map artifact-info->map (do-package-search "ddf.catalog*impl")))
  (count (map first (package-search "ddf.catalog*impl")))
  (reduce + (map #(count (second %)) (package-search "ddf.catalog*impl")))
  (reduce + (map #(count (second %)) (gather-hierarchy "ddf" "ddf" "2.19.5")))
  (comment))