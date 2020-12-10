(ns com.connexta.osgeyes.index.core
  (:import com.connexta.osgeyes.index.IndexingApp))

(defn- open-indexer! []
  (-> (IndexingApp/getInstance) (.open (IndexingApp/getRepoLocation))))

(defn- close-indexer! []
  (-> (IndexingApp/getInstance) (.close)))

(defn- gather-hierarchy [g a v]
  (-> (IndexingApp/getInstance) (.gatherHierarchy g a v)))

(comment
  (open-indexer!)
  (map #(.toString %) (gather-hierarchy "ddf" "ddf" "2.19.5"))
  (count (gather-hierarchy "ddf" "ddf" "2.19.5"))
  (close-indexer!))