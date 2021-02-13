(ns com.connexta.osgeyes.graph.query
  "Transforms, validation, and support for managing the complete data structure
  that details how to select data and assemble an end result, often a graph.")

;; ----------------------------------------------------------------------
;; # Dev Notes
;;

(comment
  "Still need tests for this section"
  ;; ---
  "Potentially faster to use subvec instead of partition; refer to Clojure docs"
  (partition 2 [:a :b :c :d :e :f])
  "vs"
  (let [input [:a :b :c :d :e :f]
        pars (range 2 (inc (count input)) 2)]
    (map #(subvec input (- % 2) %) pars))
  ;; ---
  "Ensure these functions yield predicates that match correctly"
  (let [p (selection->predicate [:node ".*catalog.*|.*spatial.*"])]
    (p {:from "A" :to "B" :cause "some.package" :type "misc"})))

;; ----------------------------------------------------------------------
;; # Selections
;;
;; Concerns mapping a [:node ".*text.*" ...] filter vector to a predicate.
;;

(def ^:private selection-terms
  (set [;; not actually on the data structure - convenience used to imply ":from AND :to"
        :node
        ;; actual data structure fields
        :type
        :cause
        :from
        :to]))

(defn- ^:private selection-valid-shape?
  "Validate the shape of a selection sel and provide useful error messages."
  [sel]
  (cond (empty? sel)
        (throw
          (IllegalArgumentException.
            (str "Selection vector cannot be empty")))
        (odd? (count sel))
        (throw
          (IllegalArgumentException.
            (str "Expected even selection vector arity, but got vector with arity " (count sel))))
        :default
        sel))

(defn- ^:private selection-pair-valid-types?
  "Validate the semantics of a selection, now reduced to keyword-string pairs. If valid, will
  attempt to remap the pairs but with the string compiled into a pattern (regex) object."
  [[kw re]]
  (cond (or (not (keyword? kw)) (not (string? re)))
        (throw (IllegalArgumentException.
                 (str "Expected keyword-string pairs but got " [kw re])))
        (not (contains? selection-terms kw))
        (throw (IllegalArgumentException.
                 (str "Invalid search term '" kw "', supported terms are " selection-terms)))
        :default
        `(~kw ~(re-pattern re))))

(defn- ^:private term-match-fn
  "Given an edge, return a mapper predicate fn for keyword-pattern (regex) pairs. The intent of
  the returned function is to evaluate an edge to get a mapping function for use on filtering
  criteria, like so: (map (term-match-fn edge) pairs)"
  [edge]
  (fn [[kw pattern]]
    (if (= :node kw)
      (let [{term-from :from term-to :to} edge]
        (and (= term-from (re-matches pattern term-from))
             (= term-to (re-matches pattern term-to))))
      (let [term (kw edge)]
        (= term (re-matches pattern term))))))

(defn- ^:private pred-fn
  "Given a coll of keyword-pattern (regex) pairs, returns a function that can
  filter an edge. All regex pairs are AND'd together for the final result."
  [pairs]
  (fn [edge]
    (let [matched? (term-match-fn edge)]
      (reduce #(and %1 %2) (map matched? pairs)))))

(defn selection->predicate
  "Transforms sel, a selection, to a predicate function that can be used to filter edges:
  (filter (filter->predicate [:node \"regex\" ...]) edges)"
  [sel]
  (->> sel
       (#(if (vector? %)
           %
           (throw (IllegalArgumentException.
                    (str "Argument filter must be a vector, but was " %)))))
       ;; allow nesting for convenience
       (flatten)
       (apply vector)
       ;; validation of final form
       (selection-valid-shape?)
       (partition 2)
       (map selection-pair-valid-types?)
       (pred-fn)))