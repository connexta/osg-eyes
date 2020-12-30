(ns com.connexta.osgeyes.options-test
  (:require [clojure.test :refer :all]
            [com.connexta.osgeyes.options :as opts]))

(deftest selection-from-empty-list
  (is (thrown? IllegalArgumentException (opts/selection->predicate '()))
      "Lists should not be allowed for use as selections"))

(deftest selection-from-empty-map
  (is (thrown? IllegalArgumentException (opts/selection->predicate {}))
      "Maps should not be allowed for use as selections"))

(deftest selection-from-empty-vector
  (is (thrown? IllegalArgumentException (opts/selection->predicate []))
      "Empty vectors should not be allowed for use as selections"))

(deftest selection-from-empty-nested-vector
  (is (thrown? IllegalArgumentException (opts/selection->predicate [[]]))
      "Empty nested vectors should not be allowed for use as selections"))

(deftest selection-from-size-one-vector
  (is (thrown? IllegalArgumentException (opts/selection->predicate [:term]))
      "Odd arity vectors should not be allowed for use as selections"))

(deftest selection-from-size-one-nested-vector
  (is (thrown? IllegalArgumentException (opts/selection->predicate [[:term]]))
      "Odd arity flattened vectors should not be allowed for use as selections"))

(deftest selection-from-size-three-vector
  (is (thrown? IllegalArgumentException (opts/selection->predicate [:term "term" "hi"]))
      "Odd arity vectors should not be allowed for use as selections"))

(deftest selection-from-size-three-nested-vector
  (is (thrown? IllegalArgumentException (opts/selection->predicate [[:term] ["term"] [:term]]))
      "Odd arity flattened vectors should not be allowed for use as selections"))

(deftest selection-from-various-nested-vectors-with-odd-arity-when-flat
  (is (thrown? IllegalArgumentException (opts/selection->predicate [[:term "one"] ["term"] [:term "two"]]))
      "Odd arity flattened vectors should not be allowed for use as selections"))

(deftest selection-with-even-final-arity-produces-fn
  (is (fn? (opts/selection->predicate [[:term] ["term"]]))
      "Any selection with an even arity should at least produce a function"))

(deftest selection-evaled-with-keyword-keyword-pair
  (is (thrown? IllegalArgumentException ((opts/selection->predicate [:term :term]) {}))
      "Keyword-keyword pairs should not be the correct format for a selection"))

(deftest selection-evaled-with-string-keyword-pair
  (is (thrown? IllegalArgumentException ((opts/selection->predicate ["term" :term]) {}))
      "String-keyword pairs should not be the correct format for a selection"))

(deftest selection-evaled-with-unsupported-key
  (is (thrown? IllegalArgumentException ((opts/selection->predicate [:term "term"]) {}))
      "Unsupported keywords should not be allowed in a selection"))

(deftest selection-evaled-from
  (is (= true ((opts/selection->predicate [:from "term"]) {:from "term"}))
      "Selection term :from should be supported"))

(deftest selection-evaled-from-mismatch
  (is (= false ((opts/selection->predicate [:from "term"]) {:from ""}))
      "Selection term :from should be supported"))

(deftest selection-evaled-to
  (is (= true ((opts/selection->predicate [:to "term"]) {:to "term"}))
      "Selection term :to should be supported"))

(deftest selection-evaled-to-mismatch
  (is (= false ((opts/selection->predicate [:to "term"]) {:to ""}))
      "Selection term :to should be supported"))

(deftest selection-evaled-type
  (is (= true ((opts/selection->predicate [:type "term"]) {:type "term"}))
      "Selection term :type should be supported"))

(deftest selection-evaled-type-mismatch
  (is (= false ((opts/selection->predicate [:type "term"]) {:type ""}))
      "Selection term :type should be supported"))

(deftest selection-evaled-cause
  (is (= true ((opts/selection->predicate [:cause "term"]) {:cause "term"}))
      "Selection term :cause should be supported"))

(deftest selection-evaled-cause-mismatch
  (is (= false ((opts/selection->predicate [:cause "term"]) {:cause ""}))
      "Selection term :cause should be supported"))

(deftest selection-evaled-node
  (is (= true ((opts/selection->predicate [:node "term"]) {:from "term" :to "term"}))
      "Selection term :node should only eval to true when both :from and :to eval to true"))

(deftest selection-evaled-node-mismatch-both
  (is (= false ((opts/selection->predicate [:node "term"]) {:from "my-term" :to "your-term"}))
      "Selection term :node should only eval to true when both :from and :to eval to true"))

(deftest selection-evaled-node-mismatch-to
  (is (= false ((opts/selection->predicate [:node "term"]) {:from "term" :to "your-term"}))
      "Selection term :node should only eval to true when both :from and :to eval to true"))

(deftest selection-evaled-node-mismatch-from
  (is (= false ((opts/selection->predicate [:node "term"]) {:from "my-term" :to "term"}))
      "Selection term :node should only eval to true when both :from and :to eval to true"))

(deftest selection-evaled-compound
  (is (= true
         ((opts/selection->predicate [[:node "one.*" :node ".*two.*"] :cause ".*package.*"])
          {:from "one-two-three" :to "one-three-two" :cause "some.package"}))
      "Compound test has failed which means atomic tests are missing"))