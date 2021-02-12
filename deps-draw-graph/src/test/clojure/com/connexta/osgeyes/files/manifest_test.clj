(ns com.connexta.osgeyes.files.manifest-test
  "Manifest parsing unit tests and sample data. There are several types of test cases detailed
  in this namespace:
  - Edge Assembly, verifies correct graph edge generation given an artifact map.
  - Manifest Parsing, verifies *.MF documents can correctly get converted to Clojure maps.
    - Invalid Input Cases, focus on function behavior for input that should never occur.
    - Base Attribute Cases, base truth for individual attributes.
    - Base Multi-valued Attribute Cases, base truth for the more complicated attributes.
    - Document Cases, which represent realistic input encountered in the wild.

  In general, keep the base truths simple and limited. For verifying against format quirks or other
  difficult issues, use document cases that target the attribute in question.
  "
  (:require [clojure.test :refer :all]
            [com.connexta.osgeyes.files.manifest :as mf]))

(def manifests-home "src/test/resources/manifests/")

;; Note: Embedded-Artifacts (ea), Export-Package/Service (ep/es), Import-Package/Service (ip/is)
;;   full manifest samples prioritize attribute variety in a doc vs individual attribute complexity.

;; @formatter:off
(def ea-basic             (str manifests-home "embedded-artifacts/BASIC.MF"))
(def ea-basic-carriage    (str manifests-home "embedded-artifacts/BASIC_CARRIAGE.MF"))
(def ep-attrs-all         (str manifests-home "export-package/ATTRS_ALL.MF"))
(def es-attrs-all-typed   (str manifests-home "export-service/ATTRS_ALL_TYPED.MF"))
(def es-attrs-all-spaces  (str manifests-home "export-service/ATTRS_ALL_TYPED_SPACES.MF"))
(def es-no-attrs          (str manifests-home "export-service/NO_ATTRS.MF"))
(def es-no-attrs-carriage (str manifests-home "export-service/NO_ATTRS_CARRIAGE.MF"))
(def full-basic           (str manifests-home "full/BASIC.MF"))
(def full-basic-attrs     (str manifests-home "full/BASIC_ATTRS.MF"))
(def full-minimal         (str manifests-home "full/MINIMAL.MF"))
(def ip-attrs-all         (str manifests-home "import-package/ATTRS_ALL.MF"))
(def ip-no-attrs          (str manifests-home "import-package/NO_ATTRS.MF"))
(def ip-no-attrs-carriage (str manifests-home "import-package/NO_ATTRS_CARRIAGE.MF"))
(def is-basic             (str manifests-home "import-service/BASIC.MF"))
(def is-basic-carriage    (str manifests-home "import-service/BASIC_CARRIAGE.MF"))
;; @formatter:on

(defn- add-carriage-returns
  "Given a path to a text file, loads the content of the file as a string with a carriage
  return (\r) added to the end of each line. Defined here for managing test resources."
  [path]
  (let [lines (with-open [rdr (clojure.java.io/reader path)]
                (doall (line-seq rdr)))]
    (->> lines
         (map #(str % "\r"))
         (interpose (System/lineSeparator))
         (apply str))))

(comment
  ;; Use the following to verify the contents, carriage returns won't be visible in std text
  ;; editors but the Clojure REPL will reveal them if the text is loaded.
  (slurp is-basic)
  (slurp is-basic-carriage)

  ;; Sample usage - will overwrite the content of is-basic-carriage with the resulting text of
  ;; (add-carriage-returns ...)
  (spit is-basic-carriage
        (add-carriage-returns is-basic)
        :create false
        :append false))

;;
;; ----------------------------------------------------------------------------------------------
;; # Manifest Edge Assembly
;; ----------------------------------------------------------------------------------------------
;;

(def test-manifests
  {"sample/dir1" {:manifest {::mf/Import-Package '("pkg.1" "pkg.2")
                             ::mf/Export-Package '("pkg.9" "pkg.8")
                             ::mf/Import-Service '("svc.1" "svc.2")
                             ::mf/Export-Service '("svc.9" "svc.8")}}
   "sample/dir2" {:manifest {::mf/Import-Package '()
                             ::mf/Export-Package '("pkg.2" "pkg.x")
                             ::mf/Import-Service '()
                             ::mf/Export-Service '("svc.2" "svc.x")}}
   "sample/dir3" {:manifest {::mf/Import-Package '()
                             ::mf/Export-Package '("pkg.1" "pkg.y")
                             ::mf/Import-Service '()
                             ::mf/Export-Service '("svc.1" "svc.y")}}
   "sample/dir4" {:manifest {::mf/Import-Package '("pkg.9" "pkg.2" "pkg.x" "pkg.y")
                             ::mf/Export-Package '("pkg.z")
                             ::mf/Import-Service '("svc.9" "svc.2" "svc.x" "svc.y")
                             ::mf/Export-Service '("svc.z")}}
   "sample/dir5" {:manifest {::mf/Import-Package '("pkg.z" "pkg.8")
                             ::mf/Export-Package '()
                             ::mf/Import-Service '("svc.z" "svc.8")
                             ::mf/Export-Service '()}}})

(deftest gen-edges-from-manifests
  (is (= (mf/artifacts->edges test-manifests)
         '({:cause "pkg.1", :type "bundle/package", :from "sample/dir1", :to "sample/dir3"}
           {:cause "pkg.2", :type "bundle/package", :from "sample/dir1", :to "sample/dir2"}
           {:cause "pkg.9", :type "bundle/package", :from "sample/dir4", :to "sample/dir1"}
           {:cause "pkg.2", :type "bundle/package", :from "sample/dir4", :to "sample/dir2"}
           {:cause "pkg.x", :type "bundle/package", :from "sample/dir4", :to "sample/dir2"}
           {:cause "pkg.y", :type "bundle/package", :from "sample/dir4", :to "sample/dir3"}
           {:cause "pkg.z", :type "bundle/package", :from "sample/dir5", :to "sample/dir4"}
           {:cause "pkg.8", :type "bundle/package", :from "sample/dir5", :to "sample/dir1"}
           {:cause "svc.1", :type "bundle/service", :from "sample/dir1", :to "sample/dir3"}
           {:cause "svc.2", :type "bundle/service", :from "sample/dir1", :to "sample/dir2"}
           {:cause "svc.9", :type "bundle/service", :from "sample/dir4", :to "sample/dir1"}
           {:cause "svc.2", :type "bundle/service", :from "sample/dir4", :to "sample/dir2"}
           {:cause "svc.x", :type "bundle/service", :from "sample/dir4", :to "sample/dir2"}
           {:cause "svc.y", :type "bundle/service", :from "sample/dir4", :to "sample/dir3"}
           {:cause "svc.z", :type "bundle/service", :from "sample/dir5", :to "sample/dir4"}
           {:cause "svc.8", :type "bundle/service", :from "sample/dir5", :to "sample/dir1"}))))

;;
;; ----------------------------------------------------------------------------------------------
;; # Manifest Parsing - Invalid Input Cases
;; ----------------------------------------------------------------------------------------------
;;

(deftest parse-invalid-manifest
  ;; thrown? cannot be resolved
  ;; https://github.com/cursive-ide/cursive/issues/238
  (is (thrown? IllegalArgumentException
               (mf/parse-content " Manifest-Version: 1.0"))))

(deftest parse-unrecognized-attribute
  ;; thrown? cannot be resolved
  ;; https://github.com/cursive-ide/cursive/issues/238
  (is (thrown? IllegalArgumentException
               (mf/parse-content "Unrecognized: 1.0"))))

(deftest parse-empty-manifest
  ;; thrown? cannot be resolved
  ;; https://github.com/cursive-ide/cursive/issues/238
  (is (thrown? IllegalArgumentException
               (mf/parse-content ""))))

;;
;; ----------------------------------------------------------------------------------------------
;; # Manifest Parsing - Base Attribute Cases
;; ----------------------------------------------------------------------------------------------
;;

(deftest parse-manifest-version
  (is (= {::mf/Manifest-Version "1.0"}
         (mf/parse-content "Manifest-Version: 1.0"))))

(deftest parse-bnd-last-modified
  (is (= {::mf/Bnd-LastModified "1586379298756"}
         (mf/parse-content "Bnd-LastModified: 1586379298756"))))

(deftest parse-build-jdk
  (is (= {::mf/Build-Jdk "1.8.0_131"}
         (mf/parse-content "Build-Jdk: 1.8.0_131"))))

(deftest parse-built-by
  (is (= {::mf/Built-By "hrogers"}
         (mf/parse-content "Built-By: hrogers"))))

(deftest parse-bundle-blueprint
  (is (= {::mf/Bundle-Blueprint '("OSGI-INF/blueprint/blueprint.xml")}
         (mf/parse-content "Bundle-Blueprint: OSGI-INF/blueprint/blueprint.xml"))))

(deftest parse-bundle-description
  (is (= {::mf/Bundle-Description "Distributed Data Framework (DDF) Parent"}
         (mf/parse-content "Bundle-Description: Distributed Data Framework (DDF) Parent"))))

(deftest parse-bundle-doc-url
  (is (= {::mf/Bundle-DocURL "http://codice.org"}
         (mf/parse-content "Bundle-DocURL: http://codice.org"))))

(deftest parse-bundle-license
  (is (= {::mf/Bundle-License "http://www.gnu.org/licenses/lgpl.html"}
         (mf/parse-content "Bundle-License: http://www.gnu.org/licenses/lgpl.html"))))

(deftest parse-bundle-manifest-version
  (is (= {::mf/Bundle-ManifestVersion "2"}
         (mf/parse-content "Bundle-ManifestVersion: 2"))))

(deftest parse-bundle-name
  (is (= {::mf/Bundle-Name "DDF :: Spatial :: CSW :: Endpoint"}
         (mf/parse-content "Bundle-Name: DDF :: Spatial :: CSW :: Endpoint"))))

(deftest parse-bundle-symbolicname
  (is (= {::mf/Bundle-SymbolicName "spatial-csw-endpoint"}
         (mf/parse-content "Bundle-SymbolicName: spatial-csw-endpoint"))))

(deftest parse-bundle-vendor
  (is (= {::mf/Bundle-Vendor "Codice Foundation"}
         (mf/parse-content "Bundle-Vendor: Codice Foundation"))))

(deftest parse-bundle-version
  (is (= {::mf/Bundle-Version "2.24.0.SNAPSHOT"}
         (mf/parse-content "Bundle-Version: 2.24.0.SNAPSHOT"))))

(deftest parse-created-by
  (is (= {::mf/Created-By "Apache Maven Bundle Plugin"}
         (mf/parse-content "Created-By: Apache Maven Bundle Plugin"))))

(deftest parse-require-capability
  (is (= {::mf/Require-Capability "osgi.ee;filter:=\"(&(osgi.ee=JavaSE)(version=1.8))\""}
         (mf/parse-content
           "Require-Capability: osgi.ee;filter:=\"(&(osgi.ee=JavaSE)(version=1.8))\""))))

(deftest parse-tool
  (is (= {::mf/Tool "Bnd-4.2.0.201903051501"}
         (mf/parse-content "Tool: Bnd-4.2.0.201903051501"))))

;;
;; ----------------------------------------------------------------------------------------------
;; # Manifest Parsing - Base Multi-valued Attribute Cases
;; ----------------------------------------------------------------------------------------------
;;

(deftest parse-bundle-classpath
  (is (= {::mf/Bundle-ClassPath
          (str ".,"
               "spatial-ogc-common-2.24.0-SNAPSHOT.jar,"
               "spatial-csw-common-2.24.0-SNAPSHOT.jar,"
               "spatial-csw-transformer-2.24.0-SNAPSHOT.jar,"
               "catalog-core-api-impl-2.24.0-SNAPSHOT.jar,"
               "geospatial-2.24.0-SNAPSHOT.jar,"
               "commons-lang3-3.9.jar")}
         (mf/parse-content
           (str "Bundle-ClassPath: .,spatial-ogc-common-2.24.0-SNAPSHOT.jar,spatial-csw-c"
                (System/lineSeparator)
                " ommon-2.24.0-SNAPSHOT.jar,spatial-csw-transformer-2.24.0-SNAPSHOT.jar,c"
                (System/lineSeparator)
                " atalog-core-api-impl-2.24.0-SNAPSHOT.jar,geospatial-2.24.0-SNAPSHOT.jar"
                (System/lineSeparator)
                " ,commons-lang3-3.9.jar")))))

(deftest parse-embed-dependency
  (is (= {::mf/Embed-Dependency
          '("spatial-ogc-common"
             "spatial-csw-common"
             "spatial-csw-transformer"
             "catalog-core-api-impl"
             "geospatial"
             "commons-lang3")}
         (mf/parse-content
           (str "Embed-Dependency: spatial-ogc-common,spatial-csw-common,"
                "spatial-csw-transformer,catalog-core-api-impl,geospatial"
                ",commons-lang3")))))

;;
;; ----------------------------------------------------------------------------------------------
;; # Manifest Parsing - Multi-valued Attribute Cases - Import/Export Package/Service
;; ----------------------------------------------------------------------------------------------
;;

(deftest parse-single-item-no-attrs
  ;; Major problem is (as of now) the most basic case only works preceding a ';' - TODO
  ;; Hypothetically there may be singular cases w/o attrs getting missed in prod (rarely)
  (are [kw] (= {kw '()}
               (mf/parse-content
                 (str (name kw)
                      ": "
                      "this.package.exported")))
            ::mf/Export-Package
            ::mf/Export-Service
            ::mf/Import-Package
            ::mf/Import-Service))

(deftest parse-double-item-no-attrs
  (are [kw] (= {kw '()}
               (mf/parse-content
                 (str (name kw)
                      ": "
                      "this.package.exported,also.this.one")))
            ::mf/Export-Package
            ::mf/Export-Service
            ::mf/Import-Package
            ::mf/Import-Service))

(deftest parse-triple-item-no-attrs
  (are [kw] (= {kw '()}
               (mf/parse-content
                 (str (name kw)
                      ": "
                      "this.package.exported,also.this.one,and.this.one")))
            ::mf/Export-Package
            ::mf/Export-Service
            ::mf/Import-Package
            ::mf/Import-Service))

(deftest parse-single-item-one-attr
  (are [kw] (= {kw '("this.package.is.exported")}
               (mf/parse-content
                 (str (name kw)
                      ": "
                      "this.package.is.exported;attr=val")))
            ::mf/Export-Package
            ::mf/Export-Service
            ::mf/Import-Package
            ::mf/Import-Service))

(deftest parse-single-item-one-attr-colon-equals-delimiter
  (are [kw] (= {kw '("this.package.is.exported")}
               (mf/parse-content
                 (str (name kw)
                      ": "
                      "this.package.is.exported;attr:=val")))
            ::mf/Export-Package
            ::mf/Export-Service
            ::mf/Import-Package
            ::mf/Import-Service))

(deftest parse-single-item-capitalized-one-attr
  (are [kw] (= {kw '("this.package.is.Exported")}
               (mf/parse-content
                 (str (name kw)
                      ": "
                      "this.package.is.Exported;attr:=val")))
            ::mf/Export-Package
            ::mf/Export-Service
            ::mf/Import-Package
            ::mf/Import-Service))

(deftest parse-single-item-one-attr-boolean
  (are [kw] (= {kw '("this.package.is.exported")}
               (mf/parse-content
                 (str (name kw)
                      ": "
                      "this.package.is.exported;multiple:=false")))
            ::mf/Export-Package
            ::mf/Export-Service
            ::mf/Import-Package
            ::mf/Import-Service))

(deftest parse-single-item-underscored-numerics-one-attr
  (are [kw] (= {kw '("net.opengis.cat.csw.v_2_0_2")}
               (mf/parse-content
                 (str (name kw)
                      ": "
                      "net.opengis.cat.csw.v_2_0_2;attr:=val")))
            ::mf/Export-Package
            ::mf/Export-Service
            ::mf/Import-Package
            ::mf/Import-Service))

(deftest parse-single-item-one-attr-version-number
  (are [kw] (= {kw '("this.package.is.exported")}
               (mf/parse-content
                 (str (name kw)
                      ": "
                      "this.package.is.exported;version=\"1.1\"")))
            ::mf/Export-Package
            ::mf/Export-Service
            ::mf/Import-Package
            ::mf/Import-Service))

(deftest parse-single-item-one-attr-version-range
  (are [kw] (= {kw '("this.package.is.exported")}
               (mf/parse-content
                 (str (name kw)
                      ": "
                      "this.package.is.exported;version=\"[2.8,3)\"")))
            ::mf/Export-Package
            ::mf/Export-Service
            ::mf/Import-Package
            ::mf/Import-Service))

(deftest parse-single-item-one-attr-typed-list
  (are [kw] (= {kw '("this.package.is.exported")}
               (mf/parse-content
                 (str (name kw)
                      ": "
                      "this.package.is.exported;id:List<String>="
                      "\"{http://www.opengis.net/cat/csw/2.0.2}Record,"
                      "{http://www.isotc211.org/2005/gmd}MD_Metadata\"")))
            ::mf/Export-Package
            ::mf/Export-Service
            ::mf/Import-Package
            ::mf/Import-Service))

(deftest parse-double-item-one-attr
  (are [kw] (= {kw '("this.package.is.exported"
                      "also.this.one")}
               (mf/parse-content
                 (str (name kw)
                      ": "
                      "this.package.is.exported;attr=val,also.this.one;attr=hi")))
            ::mf/Export-Package
            ::mf/Export-Service
            ::mf/Import-Package
            ::mf/Import-Service))

(deftest parse-double-item-one-multi-valued-attr
  (are [kw] (= {kw '("this.package.is.exported"
                      "also.this.one")}
               (mf/parse-content
                 (str (name kw)
                      ": "
                      "this.package.is.exported;multi=\"hi,sup,yo\",also.this.one;attr=hello")))
            ::mf/Export-Package
            ::mf/Export-Service
            ::mf/Import-Package
            ::mf/Import-Service))

(deftest parse-single-item-many-attrs
  (are [kw] (= {kw '("this.package.is.exported")}
               (mf/parse-content
                 (str (name kw)
                      ": "
                      "this.package.is.exported;attr=val;multi=\"hi,sup,yo\";note=test")))
            ::mf/Export-Package
            ::mf/Export-Service
            ::mf/Import-Package
            ::mf/Import-Service))

(deftest parse-triple-item-only-middle-package-has-attrs
  (are [kw] (= {kw '("also.this.one")}
               (mf/parse-content
                 (str (name kw)
                      ": "
                      "this.package.exported,also.this.one;version=\"1.1\";attr=val,and.this.one")))
            ::mf/Export-Package
            ::mf/Export-Service
            ::mf/Import-Package
            ::mf/Import-Service))

;;
;; ----------------------------------------------------------------------------------------------
;; # Manifest Parsing - Document Cases - Full
;; ----------------------------------------------------------------------------------------------
;;

(deftest parse-doc-full-minimal-singleton-lines-from-file
  (is (= #::mf{:Manifest-Version "1.0"
               :Bnd-LastModified "1586379298756"
               :Build-Jdk        "1.8.0_131"}
         (mf/parse-file full-minimal))))

(deftest parse-doc-full-multiple-single-lists-from-file
  (is (= #::mf{:Bundle-Blueprint '("OSGI-INF/blueprint/cxf-sts.xml" "OSGI-INF/blueprint/tokenstore.xml"),
               :Export-Service '(),
               :Import-Package '(),
               :Require-Capability "osgi.ee;filter:=\"(&(osgi.ee=JavaSE)(version=1.8))\"",
               :Bundle-Name "DDF :: Security :: STS :: Server",
               :Bundle-DocURL "http://codice.org",
               :Manifest-Version "1.0",
               :Tool "Bnd-3.5.0.201709291849",
               :Bundle-Version "2.19.14",
               :Bundle-ClassPath ".,security-sts-x509delegationhandler-2.19.14.jar",
               :Bundle-SymbolicName "security-sts-server",
               :Build-Jdk "1.8.0_191",
               :Bundle-License "http://www.gnu.org/licenses/lgpl.html",
               :Bundle-Description "Distributed Data Framework (DDF) Parent",
               :Import-Service '(),
               :Embedded-Artifacts '("security-sts-x509delegationhandler-2.19.14.jar"),
               :Built-By "root",
               :Bundle-ManifestVersion "2",
               :Bundle-Vendor "Codice Foundation",
               :Export-Package '(),
               :Bnd-LastModified "1601586379376",
               :Embed-Dependency '("security-sts-x509delegationhandler"),
               :Created-By "Apache Maven Bundle Plugin"}
         (mf/parse-file full-basic))))

(deftest parse-doc-full-multiple-single-lists-with-attrs-from-file
  (is (= #::mf{:Bundle-Blueprint '("OSGI-INF/blueprint/cxf-sts.xml"
                                    "OSGI-INF/blueprint/tokenstore.xml"),
               :Export-Service '(),
               :Import-Package '("net.sf.ehcache"
                                  "net.sf.ehcache.config"
                                  "com.google.common.cache"
                                  "com.hazelcast.core"),
               :Require-Capability "osgi.ee;filter:=\"(&(osgi.ee=JavaSE)(version=1.8))\"",
               :Bundle-Name "DDF :: Security :: STS :: Server",
               :Bundle-DocURL "http://codice.org",
               :Manifest-Version "1.0",
               :Tool "Bnd-3.5.0.201709291849",
               :Bundle-Version "2.19.14",
               :Bundle-ClassPath ".,security-sts-x509delegationhandler-2.19.14.jar,httpclient-4.5.6.jar,httpcore-4.4.10.jar,platform-util-unavailableurls-2.19.14.jar,ddf-security-common-2.19.14.jar,platform-util-2.19.14.jar",
               :Bundle-SymbolicName "security-sts-server",
               :Build-Jdk "1.8.0_191",
               :Bundle-License "http://www.gnu.org/licenses/lgpl.html",
               :Bundle-Description "Distributed Data Framework (DDF) Parent",
               :Import-Service '("org.apache.cxf.sts.claims.ClaimsHandler"
                                  "org.apache.cxf.sts.token.validator.TokenValidator"),
               :Embedded-Artifacts '("security-sts-x509delegationhandler-2.19.14.jar;g=\"ddf.security.sts\";a=\"security-sts-x509delegationhandler\";v=\"2.19.14\""),
               :Built-By "root",
               :Bundle-ManifestVersion "2",
               :Bundle-Vendor "Codice Foundation",
               :Export-Package '("org.codice.ddf.security.common"),
               :Bnd-LastModified "1601586379376",
               :Embed-Dependency '("security-sts-x509delegationhandler"),
               :Created-By "Apache Maven Bundle Plugin"}
         (mf/parse-file full-basic-attrs))))

;;
;; ----------------------------------------------------------------------------------------------
;; # Manifest Parsing - Document Cases - Embedded Artifacts
;; ----------------------------------------------------------------------------------------------
;;

(deftest parse-doc-embedded-artifacts-basic
  (is (= {::mf/Embedded-Artifacts
          '("spatial-ogc-common-2.19.14.jar;g=\"org.codice.ddf.spatial\";a=\"spatial-ogc-common\";v=\"2.19.14\""
             "spatial-csw-common-2.19.14.jar;g=\"org.codice.ddf.spatial\";a=\"spatial-csw-common\";v=\"2.19.14\""
             "spatial-csw-transformer-2.19.14.jar;g=\"org.codice.ddf.spatial\";a=\"spatial-csw-transformer\";v=\"2.19.14\""
             "catalog-core-api-impl-2.19.14.jar;g=\"ddf.catalog.core\";a=\"catalog-core-api-impl\";v=\"2.19.14\""
             "platform-util-2.19.14.jar;g=\"ddf.platform.util\";a=\"platform-util\";v=\"2.19.14\""
             "ddf-security-common-2.19.14.jar;g=\"ddf.security\";a=\"ddf-security-common\";v=\"2.19.14\""
             "geospatial-2.19.14.jar;g=\"org.codice.ddf\";a=\"geospatial\";v=\"2.19.14\""
             "platform-util-unavailableurls-2.19.14.jar;g=\"ddf.platform.util\";a=\"platform-util-unavailableurls\";v=\"2.19.14\""
             "commons-lang3-3.8.1.jar;g=\"org.apache.commons\";a=\"commons-lang3\";v=\"3.8.1\"")}
         (mf/parse-file ea-basic))))

(deftest parse-doc-embedded-artifacts-basic-with-carriage-returns
  (is (= {::mf/Embedded-Artifacts
          '("spatial-ogc-common-2.19.14.jar;g=\"org.codice.ddf.spatial\";a=\"spatial-ogc-common\";v=\"2.19.14\""
             "spatial-csw-common-2.19.14.jar;g=\"org.codice.ddf.spatial\";a=\"spatial-csw-common\";v=\"2.19.14\""
             "spatial-csw-transformer-2.19.14.jar;g=\"org.codice.ddf.spatial\";a=\"spatial-csw-transformer\";v=\"2.19.14\""
             "catalog-core-api-impl-2.19.14.jar;g=\"ddf.catalog.core\";a=\"catalog-core-api-impl\";v=\"2.19.14\""
             "platform-util-2.19.14.jar;g=\"ddf.platform.util\";a=\"platform-util\";v=\"2.19.14\""
             "ddf-security-common-2.19.14.jar;g=\"ddf.security\";a=\"ddf-security-common\";v=\"2.19.14\""
             "geospatial-2.19.14.jar;g=\"org.codice.ddf\";a=\"geospatial\";v=\"2.19.14\""
             "platform-util-unavailableurls-2.19.14.jar;g=\"ddf.platform.util\";a=\"platform-util-unavailableurls\";v=\"2.19.14\""
             "commons-lang3-3.8.1.jar;g=\"org.apache.commons\";a=\"commons-lang3\";v=\"3.8.1\"")}
         (mf/parse-file ea-basic-carriage))))

;;
;; ----------------------------------------------------------------------------------------------
;; # Manifest Parsing - Document Cases - Export Package
;; ----------------------------------------------------------------------------------------------
;;

(deftest parse-doc-export-package-all-entries-have-attrs
  (is (= {::mf/Export-Package
          '("net.opengis.cat.csw.v_2_0_2.dc.elements"
             "net.opengis.cat.csw.v_2_0_2.dc.terms"
             "net.opengis.cat.csw.v_2_0_2"
             "net.opengis.filter.v_1_1_0"
             "net.opengis.ows.v_1_0_0"
             "net.opengis.gml.v_3_1_1")}
         (mf/parse-file ep-attrs-all))))

;;
;; ----------------------------------------------------------------------------------------------
;; # Manifest Parsing - Document Cases - Export Service
;; ----------------------------------------------------------------------------------------------
;;

(deftest parse-doc-export-service-all-entries-have-attrs-typed
  (is (= {::mf/Export-Service
          '("ddf.catalog.endpoint.CatalogEndpoint"
             "ddf.catalog.event.Subscriber"
             "ddf.catalog.transform.QueryFilterTransformer")}
         (mf/parse-file es-attrs-all-typed))))

(deftest parse-doc-export-service-all-entries-have-attrs-typed-spaced
  (is (= {::mf/Export-Service
          '("ddf.catalog.transform.MetacardTransformer"
             "record.xsd"
             "ddf.catalog.data.MetacardType"
             "ddf.catalog.transform.MetacardTransformer"
             "gmd.xsd"
             "ddf.catalog.data.MetacardType"
             "ddf.catalog.transform.QueryFilterTransformerProvider"
             "ddf.catalog.transform.QueryResponseTransformer"
             "com.thoughtworks.xstream.converters.Converter"
             "ddf.catalog.transform.InputTransformer"
             "ddf.catalog.transform.InputTransformer")}
         (mf/parse-file es-attrs-all-spaces))))

(deftest parse-doc-export-service-no-entries-have-attrs
  (is (= {::mf/Export-Service '()}
         (mf/parse-file es-no-attrs))))

(deftest parse-doc-export-service-no-entries-have-attrs-with-carriage-returns
  (is (= {::mf/Export-Service '()}
         (mf/parse-file es-no-attrs-carriage))))

;;
;; ----------------------------------------------------------------------------------------------
;; # Manifest Parsing - Document Cases - Import Package
;; ----------------------------------------------------------------------------------------------
;;

(deftest parse-doc-import-package-all-entries-have-attrs
  (is (= {::mf/Import-Package
          '("com.google.common.collect"
             "com.google.common.escape"
             "com.google.common.html"
             "com.google.common.io"
             "com.google.gson"
             "com.google.gson.reflect"
             "com.google.gson.stream"
             "ddf.security"
             "ddf.security.permission"
             "javax.annotation"
             "org.apache.commons.collections"
             "org.apache.commons.collections4.map"
             "org.apache.commons.io"
             "org.apache.commons.lang"
             "org.apache.commons.lang.text"
             "org.apache.shiro"
             "org.apache.shiro.authz"
             "org.apache.shiro.subject"
             "org.bouncycastle.crypto"
             "org.bouncycastle.crypto.digests"
             "org.bouncycastle.crypto.prng"
             "org.bouncycastle.crypto.prng.drbg"
             "org.codice.ddf.admin.core.api"
             "org.codice.ddf.admin.core.api.jmx"
             "org.codice.ddf.configuration"
             "org.codice.ddf.persistence"
             "org.codice.ddf.ui.admin.api.module"
             "org.codice.ddf.ui.admin.api.plugin"
             "org.json.simple"
             "org.json.simple.parser"
             "org.osgi.framework"
             "org.osgi.resource"
             "org.osgi.service.blueprint"
             "org.osgi.service.cm"
             "org.osgi.service.event"
             "org.osgi.service.log"
             "org.osgi.service.metatype"
             "org.osgi.service.repository"
             "org.osgi.util.tracker"
             "org.slf4j")}
         (mf/parse-file ip-attrs-all))))

(deftest parse-doc-import-package-no-entries-have-attrs
  (is (= {::mf/Import-Package '()}
         (mf/parse-file ip-no-attrs))))

(deftest parse-doc-import-package-no-entries-have-attrs-with-carriage-returns
  (is (= {::mf/Import-Package '()}
         (mf/parse-file ip-no-attrs-carriage))))

;;
;; ----------------------------------------------------------------------------------------------
;; # Manifest Parsing - Document Cases - Import Service
;; ----------------------------------------------------------------------------------------------
;;

(deftest parse-doc-import-service-basic
  (is (= {::mf/Import-Service
          '("ddf.catalog.transform.MetacardTransformer"
             "ddf.catalog.transform.InputTransformer"
             "ddf.catalog.transform.QueryFilterTransformer"
             "ddf.action.ActionProvider"
             "ddf.catalog.transformer.api.PrintWriterProvider")}
         (mf/parse-file is-basic))))

(deftest parse-doc-import-service-basic-with-carriage-returns
  (is (= {::mf/Import-Service
          '("ddf.catalog.transform.MetacardTransformer"
             "ddf.catalog.transform.InputTransformer"
             "ddf.catalog.transform.QueryFilterTransformer"
             "ddf.action.ActionProvider"
             "ddf.catalog.transformer.api.PrintWriterProvider")}
         (mf/parse-file is-basic-carriage))))