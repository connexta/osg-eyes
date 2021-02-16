(ns com.connexta.osgeyes.dev.notes
  "This namespace is just a scratchpad for notes about data structures or potential CLI fns.
  Keeping this info with the actual code was making it harder to navigate."
  (:require [com.connexta.osgeyes.graph.connectors.manifest :as manifest]))

;;
;; ----------------------------------------------------------------------------------------------
;; Development Notes
;;
;; Notes about data structures, new functions, and opportunities for planning
;; ahead future capability iterations. Not all are necessarily executable as-is.
;;
;; ----------------------------------------------------------------------------------------------
;;

(comment

  ;;
  ;; --------------------------------------------------------------------------------------------
  ;; Diagnostics / data exploration
  ;; --------------------------------------------------------------------------------------------
  ;;

  (defn invoke-with
    "Takes a function f that requires named args and invokes it using the
    provided named-args map."
    [f named-args]
    (->> (seq named-args) (apply concat) (apply f)))

  (->> default-gather
       (map gav)
       (map #(create-artifact-map (:g %) (:a %) (:v %)))
       (apply merge)
       ;; catalog-core-api
       #_(and (.contains m "Export-Service:")
              (> ecount 3)
              (not (.contains m ";"))
              (not (.contains m "uses:="))
              (not (.contains m "version=")))
       #_(filter #(let [m (get-in (val %) [:manifest ::manifest/Export-Service])]
                    (if (nil? m) false (not (.contains m ";")))))
       #_(into {}))

  ;;
  ;; --------------------------------------------------------------------------------------------
  ;; Data structure reference
  ;; --------------------------------------------------------------------------------------------
  ;;

  ;; Old representation
  {"ddf/symbolic-name"
   {::manifest/Manifest-Version    "1.0"
    ::manifest/Bundle-SymbolicName "symbolic-name"
    #_etc}}

  ;; Current variable names used in code:
  ;; - 'artifact-map' (plural artifacts) each containing all facets, or just 'artifacts'
  ;; - the key in the below map referred to as 'qualname' (i.e. 'qualified name')
  ;; Other potential variable names:
  ;; - 'artifact-facets' (referring to singular map)
  ;; - facet-map (referring to singular map)
  ;; - facet-maps (plural) would be 'artifacts'
  {"<artifact-id-of-root>/<artifact-id-of-bundle>"
   {:manifest  {::manifest/Manifest-Version    "1.0"
                ::manifest/Bundle-SymbolicName "symbolic-name"
                #_etc}
    :pom       {}
    :blueprint {}
    :feature   {}
    #_etc}}

  ;; Potential to use a proper mvn:* string in the future instead of a 'qualname'?
  {"mvn:group/art/ver" {#_etc}}

  ;; Transform to get back to "old" way of doing it
  (into {} (map #(vector %1 (get %2 :manifest)) artifact-maps))

  artifact
  ;; any metadata, original or processed, associated with an artifact
  {:maven    {#_"artifact-info from indexer module"}
   :manifest {#_"manifest attributes from manifest parser"}}

  qualname
  ;; qualified name of an artifact
  "~qualifier~/~artifact-name~"
  ;; currently it's safe to assume...
  "artifactId-of-root/bundle-symbolic-name"

  artifact-map
  ;; see above example
  {qualname1 artifact1
   qualname2 artifact2}

  edge
  {:to    "<qualname-of-dest>"
   :from  "<qualname-of-src>"
   :cause "some.package.or.Interface.like.string"
   :type  "bundle/package" or "bundle/service"}

  ;;
  ;; --------------------------------------------------------------------------------------------
  ;; New commands / capabilities
  ;; --------------------------------------------------------------------------------------------
  ;;

  "Considering a way to manage facets so you needn't always make requests to the mvn-indexer module.
  This could probably be done by simple import/export but the data needs to be more or less stable."

  ;; Consider keeping import/export of 'facet' data
  ;; Could also support JSON pretty easily
  (defn facet-export [] ())
  (defn facet-import [] ())

  "Considering a way to change preferences or default options (gathering, selections, etc) during
  runtime and then just re-dump the raw EDN each time."

  ;; Consider adding a way to read, change, and clear options; such as the maven roots you want to
  ;; gather for analysis.
  (defn mvn-roots-show)
  (defn mvn-roots-add)
  (defn mvn-roots-clear)
  ;; The maven roots, along with other stuff like selections, would be part of one big document of
  ;; options that you manage.
  (defn options-show)
  ;; Maybe we add named configurations, which would be easy to reference on a CLI
  (defn profile-add)
  (defn profile-clear)

  "Not actually sure how I feel about the below; might as well just use git at this point."

  ;; Using the above as a foundation, what if we provide one level of backup in case we bork our
  ;; options? Keeping options means we're stable and won't rollback; what we have now becomes our
  ;; safety point. Rolling back options resets us to our last safety point or system defaults.
  (defn options-keep)
  (defn options-rollback)

  ;;
  ;; --------------------------------------------------------------------------------------------
  ;; Global config per command
  ;; --------------------------------------------------------------------------------------------
  ;;

  "But we are definitely moving toward every command getting fed a full document (map) of options
  which just get merged from varying levels of control (global defaults, user options, and anything
  in the CLI input)."

  ;; Need a more comprehensive 'config' definition which targets precisely what to do.
  {:gather-from ["mvn:ddf/ddf/${VERSION}"
                 "mvn:other/other/${VERSION}"]
   :select      [:node "ddf/.*" :node ".*/catalog.*"]}

  "Some kind of global config map like this? - 'ds' being a downstream project of some sort."

  {;; Manually specify named gathering configurations used to query maven and generate an initial
   ;; seq of artifacts to process.
   :gatherings  {:gather/ddf-2.19.5 ["mvn:ddf/ddf/2.19.5"]
                 :gather/ddf-2.23.9 ["mvn:ddf/ddf/2.23.9"]
                 :ds-1.0.5          ["mvn:ddf/ddf/2.19.5" "mvn:ds/ds/1.0.0"]
                 :ds-1.8.2          ["mvn:ddf/ddf/2.23.9" "mvn:ds/ds/1.8.2"]}

   ;; But depending on how the 'names' are referenced (i.e. a namespace is dynamically generated
   ;; with keywords or symbols) why could we not just grab this info from GitHub?
   :gatherings2 (poll-from "https:github.com/blah/blah" any other args)

   ;; Selections by name, which can be composed on the CLI
   :selections  {:select/ddf-catalog-only [:node "ddf/.*" :node ".*/catalog.*"]
                 :select/ds-catalog-only  [:node "ds/.*" :node ".*/catalog.*"]}

   ;; Presentation options by name, the fn's don't exist they're just examples
   :present     {:present/example [(export/clustering :gen 3)
                                   (export/coloring "ddf" :blue)]}

   ;; Defaults that fire when omitted on the CLI
   :defaults    {:gathering :gather/ddf-2.19.5
                 :selection :select/ddf-catalog-only}

   ;; Groups of named defaults that fire when omitted on the CLI
   :profiles    {:my-profile {:gathering :gather/ddf-2.19.5
                              :selection :select/ddf-catalog-only}}}

  "Could qualify some special keywords just for the CLI REPL context so we get autocomplete. Since
  all production code is qualified using 'com.connexta.osgeyes' it's probably not a big deal."

  {:gather/ddf-2.19.5       ["mvn:ddf/ddf/2.19.5"]
   :gather/ddf-2.23.9       ["mvn:ddf/ddf/2.23.9"]
   :select/ddf-catalog-only [:node "ddf/.*" :node ".*/catalog.*"]
   :select/ds-catalog-only  [:node "ds/.*" :node ".*/catalog.*"]}

  "Eventually, with some pre/post-processing macros, the CLI could get very close to a flat,
  traditional experience; it would be hard to tell the app was a Clojure REPL at all."

  (draw-graph :gather :ddf-2.19.5 :select :ddf-catalog-only)
  (draw-graph :profile :my-profile :select [])
  (diff-graph :from :gather/ddf-2.19.5 :to :gather/ddf-2.23.9 :select/ddf-catalog-only)
  (diff-graph from gather/ddf-2.19.5 to gather/ddf-2.23.9 select/ddf-catalog-only)

  "Ultimately it might be worth (down the road) looking into a simple CLI wrapper that delegates to
  the Clojure REPL so all the namespace dynamics are no longer necessary but that means portability
  between environments might get sacrificed (if it isn't already). Those implications are unknown."

  "Update: The namespace dynamics are easier than originally thought."

  (comment))