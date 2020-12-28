(ns com.connexta.osgeyes.index.core
  "Clojure wrapper code that changes with the Java code."
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
          :attrs         (-> info .getAttributes)
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