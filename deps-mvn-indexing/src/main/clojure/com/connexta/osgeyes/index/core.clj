(ns com.connexta.osgeyes.index.core
  "Clojure wrapper code that changes with the Java code."
  (:require [clojure.string :as str])
  (:import
    (com.connexta.osgeyes.index IndexingApp Criteria MvnOntology Criteria$Options)
    (org.apache.maven.index MAVEN ArtifactInfo)
    (org.apache.lucene.search BooleanClause$Occur BooleanClause)))

;;
;; ----------------------------------------------------------------------------------------------
;; Global accessors
;; ----------------------------------------------------------------------------------------------
;;

(defn- get-indexing-app [] (IndexingApp/getInstance))
(defn- get-criteria [] (-> (get-indexing-app) (.getCriteria)))

;;
;; ----------------------------------------------------------------------------------------------
;; Clojure <-> Java transformations
;; ----------------------------------------------------------------------------------------------
;;

(def ^:private keyword->field
  {:group-id      MAVEN/GROUP_ID
   :artifact-id   MAVEN/ARTIFACT_ID
   :version       MAVEN/VERSION
   :packaging     MAVEN/PACKAGING
   :classifier    MAVEN/CLASSIFIER
   :last-modified MAVEN/LAST_MODIFIED
   :name          MAVEN/NAME
   :description   MAVEN/DESCRIPTION
   :file-ext      MAVEN/EXTENSION
   :sha1          MAVEN/SHA1
   ;; Note that repository ID is a core field but no index creator populates it
   ;;   It is dynamically added to the artifact info when query results are returned
   ;;   Using it in a query will likely fail or produce zero results
   :repository-id MAVEN/REPOSITORY_ID
   ;; Custom queryable fields
   ;;   :pom-modules excluded because it has very low filtering utility
   ;;   :jar-manifest excluded because it's not indexed so cannot be searched on
   :pom-parent    MvnOntology/POM_PARENT
   :jar-packages  MvnOntology/JAR_PACKAGES})

(defn- artifact-info->map
  "Converts an org.apache.maven.index.ArtifactInfo into a map with keywords. Exclude keys
  with (artifact-info->map info #{:attrs}) for previewing contents."
  ([^ArtifactInfo info]
   (artifact-info->map info #{}))
  ([^ArtifactInfo info exclusions]
   (let [mappings
         ;; Core pom fields come first
         {:group-id         (-> info .getGroupId)
          :artifact-id      (-> info .getArtifactId)
          :version          (-> info .getVersion)
          :packaging        (-> info .getPackaging)
          :classifier       (-> info .getClassifier)
          :last-modified    (-> info .getLastModified)
          :name             (-> info .getName)
          :description      (-> info .getDescription)
          :file-ext         (-> info .getFileExtension)
          :sha1             (-> info .getSha1)
          ;; Followed by transient (w.r.t index), dynamic fields
          ;; ~ Dynamically added to artifact info when results are being returned
          :repository-id    (-> info .getRepository)
          :indexing-context (-> info .getContext)
          ;; ~ (note! score only populated on IteratorSearchRequests)
          :score            (-> info .getLuceneScore)
          :match-highlights (into {} (map #(vector (-> % .getField .getFieldName)
                                                   (-> % .getHighlightedMatch))
                                          (-> info .getMatchHighlights)))
          ;; ~ Dynamically added by reading the artifact directly
          :file-name        (-> info .getFileName)
          :path             (-> info .getPath)
          :size             (-> info .getSize)
          ;; Followed by custom attrs (pom-parent, pom-modules, jar-manifest, jar-packages)
          :attrs            (into {} (-> info .getAttributes))}]
     (->> mappings
          (filter (complement #(contains? exclusions (first %))))
          (merge (sorted-map))))))

;;
;; ----------------------------------------------------------------------------------------------
;; Artifact search (criteria conversions)
;; ----------------------------------------------------------------------------------------------
;;

(def ^:private keyword->criteria-opt-tform
  "Given a keyword, returns a function that will update a criteria options object in accordance
  with the semantics of the keyword."
  {:occur-must     (fn [^Criteria$Options opts] (-> opts (.with BooleanClause$Occur/MUST)))
   :occur-should   (fn [^Criteria$Options opts] (-> opts (.with BooleanClause$Occur/SHOULD)))
   :occur-must-not (fn [^Criteria$Options opts] (-> opts (.with BooleanClause$Occur/MUST_NOT)))
   :occur-filter   (fn [^Criteria$Options opts] (-> opts (.with BooleanClause$Occur/FILTER)))
   :partial-input  (fn [^Criteria$Options opts] (-> opts (.partialInput)))})

(defn- make-criteria-options [opts]
  (let [^Criteria criteria (get-criteria)
        ^Criteria$Options blank-options (-> criteria (.options))
        tforms (map keyword->criteria-opt-tform opts)]
    (if (every? identity tforms)
      ((apply comp tforms) blank-options)
      (throw (IllegalArgumentException. (str "Invalid options specified: " opts))))))

(defn- lookfor
  "Used like criteria.of(...) for key-value args or key-value-options args. In this case the
  options arg is a set of keywords that will flag search behavior for the predicate it's
  specified for."
  ([key val]
   (lookfor key val #{}))
  ([key val opts]
   (if (empty? opts)
     (-> (get-criteria) (.of (keyword->field key) val))
     (-> (get-criteria) (.of (keyword->field key) val (make-criteria-options opts))))))

(defn- lookfor-all
  "Used like criteria.of(...) for combining collections of other criteria."
  [& criteria]
  (-> (get-criteria) (.of (into-array criteria))))

(defn- query-mvn
  "Wrapper for IndexingApp#searchArtifacts."
  [criteria]
  (map artifact-info->map (-> (get-indexing-app) (.searchArtifacts criteria))))

(comment
  ;; It's possible that gather-hierarchy code might be part of the problem (dupes getting through)
  (count (query-mvn
           (lookfor-all
             (lookfor :artifact-id "proxy-camel-servlet")
             (lookfor :version "2.19.14"))))
  (count (query-mvn
           (lookfor-all
             (lookfor :artifact-id "proxy-camel-servlet")
             (lookfor :version "2.19.14")
             (lookfor :file-ext "jar"))))

  ;; All ddf reactor poms
  (query-mvn
    (lookfor-all
      (lookfor :artifact-id "ddf")
      (lookfor :packaging "pom")))

  ;; All reactor poms that are not ddf
  (query-mvn
    (lookfor-all
      (lookfor :artifact-id "ddf" #{:occur-must-not})
      (lookfor :packaging "pom")))

  (comment))

;;
;; ----------------------------------------------------------------------------------------------
;; Index state
;; ----------------------------------------------------------------------------------------------
;;

(defn open-indexer!
  "Wrapper for IndexingApp#open."
  []
  (-> (get-indexing-app) (.open (IndexingApp/getRepoLocation))))

(defn close-indexer!
  "Wrapper for IndexingApp#close."
  []
  (-> (get-indexing-app) (.close)))

;;
;; ----------------------------------------------------------------------------------------------
;; Hierarchies
;; ----------------------------------------------------------------------------------------------
;;

(defn- do-gather-hierarchy
  "Wrapper for IndexingApp#gatherHierarchy."
  [g a v]
  (-> (get-indexing-app) (.gatherHierarchy g a v)))

(defn gather-hierarchy
  "Returns a coll of maven artifacts that make up the tree defined by the given root. Tree
  membership is determined by the pom parent links. Returns all intermediate members. Filter on
  packaging if only interested in leaf nodes. Invoke:
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
          (gather-hierarchy "ddf" "2.19.14"))

  (open-indexer!)
  (gather-hierarchy "ddf" "2.23.1")
  (count (gather-hierarchy "ddf" "2.23.1"))
  (filter #(not (or (:classifier %) (:file-name %) (:path %))) (gather-hierarchy "ddf" "2.19.5"))

  ;;
  ;; Verify these on both sides of the fix
  (open-indexer!)
  ;; 7
  (count (query-mvn
           (lookfor-all
             (lookfor :artifact-id "pax-web*")
             (lookfor :file-ext "xml"))))
  ;; 31
  (count (query-mvn
           (lookfor :file-ext "cfg")))
  ;; 2
  (count (query-mvn
           (lookfor :file-ext "yml")))
  ;; 7
  (count (query-mvn
           (lookfor :file-ext "tar.gz")))
  ;; 1068 - these should be zero
  (count (query-mvn
           (lookfor :file-ext "pom.lastUpdated")))

  ;;
  ;; Why are so many duplicate artifacts getting returned for ddf 2.19.14? TODO
  ;; Using distinct works but can't tell how maven is treating them as separate
  ;; Check classifier or other properties might be an indexer-core bug
  (->> (gather-hierarchy "ddf" "2.19.14")
       (filter #(= (:file-ext %) "jar"))
       (filter #(= (:packaging %) "bundle"))
       (count))
  (->> (gather-hierarchy "ddf" "2.19.14")
       (filter #(= (:file-ext %) "jar"))
       (filter #(= (:packaging %) "bundle"))
       (distinct)
       (count))
  ;;
  ;; Cross-check, this one is fine, the presence of additional files (.lastUpdated) appear to be
  ;; mucking with the indexer somehow
  ;;
  ;; Refer to ~/.m2/repository/org/codice/httpproxy/proxy-camel-servlet for an example
  (->> (gather-hierarchy "ddf" "2.19.17-SNAPSHOT")
       (filter #(= (:file-ext %) "jar"))
       (filter #(= (:packaging %) "bundle"))
       (count))
  (->> (gather-hierarchy "ddf" "2.19.17-SNAPSHOT")
       (filter #(= (:file-ext %) "jar"))
       (filter #(= (:packaging %) "bundle"))
       (distinct)
       (count))
  ;;
  ;; Need to reinstall 2.19.5 to verify
  (->> (gather-hierarchy "ddf" "2.19.5")
       (filter #(= (:file-ext %) "jar"))
       (filter #(= (:packaging %) "bundle"))
       (count))

  (map #(artifact-info->map % #{:attrs}) (do-gather-hierarchy "ddf" "ddf" "2.19.5"))
  (map #(.toString %) (do-gather-hierarchy "ddf" "ddf" "2.19.5"))
  (close-indexer!))

;;
;; ----------------------------------------------------------------------------------------------
;; Package search
;; ----------------------------------------------------------------------------------------------
;;

(defn- extract-packages
  "Extract packages from artifact map as sequence of vectors."
  [artifact-map]
  (as-> (get-in artifact-map [:jar-packages]) results
        (map #(vector % (dissoc artifact-map :jar-packages)) results)))

(defn- reduce-packages
  "Combines like artifacts by package."
  [package-map next-package]
  (if (not (contains? package-map (first next-package)))
    (assoc package-map (first next-package) (list (last next-package)))
    (update package-map (first next-package) #(cons (last next-package) %))))

(defn do-package-search
  "Wrapper for IndexingApp#searchPackages."
  [package-name]
  (.searchPackages (get-indexing-app) package-name))

(defn match-package-search?
  "Predicate that can filter packages by matching them against the original wildcard search."
  [package-search item]
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