(ns com.connexta.osgeyes.manifest-test
  (:require [clojure.test :refer :all]
            [com.connexta.osgeyes.manifest :as mf]))

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

(deftest parse-manifest-version
  (is (= {::mf/Manifest-Version "1.0"}
         (mf/parse-content "Manifest-Version: 1.0"))))

(deftest parse-bnd-last-modified
  (is (= {::mf/Bnd-LastModified "1586379298756"}
         (mf/parse-content "Bnd-LastModified: 1586379298756"))))

(deftest parse-build-jdk
  (is (= {::mf/Build-Jdk "1.8.0_131"}
         (mf/parse-content "Build-Jdk: 1.8.0_131"))))

(deftest parse-multiple-attributes
  (is (= {::mf/Manifest-Version "1.0"
          ::mf/Bnd-LastModified "1586379298756"
          ::mf/Build-Jdk        "1.8.0_131"}
         (mf/parse-content
           "Manifest-Version: 1.0\nBnd-LastModified: 1586379298756\nBuild-Jdk: 1.8.0_131"))))

(deftest parse-multiple-attributes-from-file
  (is (= {::mf/Manifest-Version "1.0"
          ::mf/Bnd-LastModified "1586379298756"
          ::mf/Build-Jdk        "1.8.0_131"}
         (mf/parse-file "src/test/resources/TEST_MANIFEST.MF"))))

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

(deftest parse-bundle-classpath
  (is (= {::mf/Bundle-ClassPath (str ".,spatial-ogc-common-2.24.0-SNAPSHOT.jar,spatial-csw-c"
                                     "ommon-2.24.0-SNAP"
                                     "SHOT.jar,spatial-csw-transformer-2.24.0-SNAPSHOT.jar,c"
                                     "atalog-core-api-i"
                                     "mpl-2.24.0-SNAPSHOT.jar,geospatial-2.24.0-SNAPSHOT.jar"
                                     ",commons-lang3-3.9.jar")}
         (mf/parse-content
           (str "Bundle-ClassPath: .,spatial-ogc-common-2.24.0-SNAPSHOT.jar,spatial-csw-c\n"
                " ommon-2.24.0-SNAPSHOT.jar,spatial-csw-transformer-2.24.0-SNAPSHOT.jar,c\n"
                " atalog-core-api-impl-2.24.0-SNAPSHOT.jar,geospatial-2.24.0-SNAPSHOT.jar\n"
                " ,commons-lang3-3.9.jar")))))

(deftest parse-embed-dependency
  (is (= {::mf/Embed-Dependency '("spatial-ogc-common"
                                   "spatial-csw-common"
                                   "spatial-csw-transformer"
                                   "catalog-core-api-impl"
                                   "geospatial"
                                   "commons-lang3")}
         (mf/parse-content (str "Embed-Dependency: spatial-ogc-common,spatial-csw-common,"
                                "spatial-csw-tran\n sformer,catalog-core-api-impl,geospatial"
                                ",commons-lang3")))))

(deftest parse-embedded-artifacts
  (is (= {::mf/Embedded-Artifacts
          '("spatial-ogc-common-2.24.0-SNAPSHOT.jar;g=\"org.codice.ddf.spatial\";a=\"spatial-ogc-common\";v=\"2.24.0-SNAPSHOT\""
             "spatial-csw-common-2.24.0-SNAPSHOT.jar;g=\"org.codice.ddf.spatial\";a=\"spatial-csw-common\";v=\"2.24.0-SNAPSHOT\""
             "spatial-csw-transformer-2.24.0-SNAPSHOT.jar;g=\"org.codice.ddf.spatial\";a=\"spatial-csw-transformer\";v=\"2.24.0-SNAPSHOT\""
             "catalog-core-api-impl-2.24.0-SNAPSHOT.jar;g=\"ddf.catalog.core\";a=\"catalog-core-api-impl\";v=\"2.24.0-SNAPSHOT\""
             "geospatial-2.24.0-SNAPSHOT.jar;g=\"org.codice.ddf\";a=\"geospatial\";v=\"2.24.0-SNAPSHOT\""
             "commons-lang3-3.9.jar;g=\"org.apache.commons\";a=\"commons-lang3\";v=\"3.9\"")}
         (mf/parse-content (str "Embedded-Artifacts: spatial-ogc-common-2.24.0-SNAPSHOT.jar;g=\"o"
                                "rg.codice\n .ddf.spatial\";a=\"spatial-ogc-common\";v=\"2.24.0-SN"
                                "APSHOT\",spatial-csw-co\n mmon-2.24.0-SNAPSHOT.jar;g=\"org.codice"
                                ".ddf.spatial\";a=\"spatial-csw-comm\n on\";v=\"2.24.0-SNAPSHOT\","
                                "spatial-csw-transformer-2.24.0-SNAPSHOT.jar;g=\"\n org.codice.ddf"
                                ".spatial\";a=\"spatial-csw-transformer\";v=\"2.24.0-SNAPSHOT\"\n "
                                ",catalog-core-api-impl-2.24.0-SNAPSHOT.jar;g=\"ddf.catalog.core\""
                                ";a=\"cata\n log-core-api-impl\";v=\"2.24.0-SNAPSHOT\",geospatial-"
                                "2.24.0-SNAPSHOT.jar;g\n =\"org.codice.ddf\";a=\"geospatial\";v=\""
                                "2.24.0-SNAPSHOT\",commons-lang3-3.9.\n jar;g=\"org.apache.commons"
                                "\";a=\"commons-lang3\";v=\"3.9\"")))))

(deftest parse-single-export-package-no-attributes
  (is (= {::mf/Export-Package '("this.package.is.exported")}
         (mf/parse-content "Export-Package: this.package.is.exported;"))))

(deftest parse-single-export-package-with-newlines-and-two-attributes
  (is (= {::mf/Export-Package '("some.other.separate.package")}
         (mf/parse-content "Export-Package: some.other.separate.package;uses:=\"ddf.catalog.data,ddf\n .catalog.filter,ddf.catalog.operation,ddf.catalog.plugin,ddf.catalog.re\n source,ddf.catalog.source,org.codice.ddf.persistence\";version=\"15.8.0\""))))

(deftest parse-export-package
  (is (= {::mf/Export-Package '("this.package.exported"
                                 "also.this.one"
                                 "and.this.one")}
         (mf/parse-content "Export-Package: this.package.exported;also.this.one;and.this.one;"))))

(deftest parse-export-package-with-uses-attribute
  (is (= {::mf/Export-Package '("this.package.exported"
                                 "also.this.one"
                                 "and.this.one")}
         (mf/parse-content "Export-Package: this.package.exported;also.this.one;uses:=\"package.one,package.two,package.three\",and.this.one;"))))

(deftest parse-export-package-with-version-attribute
  (is (= {::mf/Export-Package '("this.package.exported"
                                 "also.this.one"
                                 "and.this.one")}
         (mf/parse-content "Export-Package: this.package.exported;also.this.one;version=\"2.24.0\",and.this.one;"))))

(deftest parse-export-package-with-newlines-and-both-uses-and-version
  (is (= {::mf/Export-Package '("org.codice.ddf.spatial.ogc.csw.catalog.endpoint.transformer"
                                 "org.codice.ddf.spatial.ogc.csw.catalog.endpoint.mappings")}
         (mf/parse-content "Export-Package: org.codice.ddf.spatial.ogc.csw.catalog.endpoint.transfor\n mer;uses:=\"ddf.catalog.data,ddf.catalog.operation,ddf.catalog.transform\n ,javax.annotation,org.codice.ddf.spatial.ogc.csw.catalog.actions,org.ge\n otools.filter.visitor,org.opengis.filter,org.opengis.filter.expression,\n org.opengis.filter.spatial,org.xml.sax.helpers\";version=\"2.24.0\",org.co\n dice.ddf.spatial.ogc.csw.catalog.endpoint.mappings;uses:=\"org.codice.dd\n f.spatial.ogc.csw.catalog.endpoint.transformer,org.geotools.filter.visi\n tor,org.opengis.filter,org.xml.sax.helpers\";version=\"2.24.0\""))))

(deftest parse-export-package-with-newlines-and-both-uses-and-version-then-just-version
  (is (= {::mf/Export-Package '("org.codice.ddf.migration"
                                 "org.codice.ddf.util.function")}
         (mf/parse-content "Export-Package: org.codice.ddf.migration;uses:=\"org.codice.ddf.platform.\n services.common,org.codice.ddf.util.function\";version=\"2.24.0\",org.codi\n ce.ddf.util.function;version=\"2.24.0\""))))

(deftest parse-export-service
  (is (= {::mf/Export-Service '("ddf.catalog.transform.QueryFilterTransformer"
                                 "ddf.catalog.endpoint.CatalogEndpoint"
                                 "ddf.catalog.event.Subscriber")}
         (mf/parse-content "Export-Service: ddf.catalog.transform.QueryFilterTransformer;id:List<Str\n ing>=\"{http://www.opengis.net/cat/csw/2.0.2}Record,{http://www.isotc211\n .org/2005/gmd}MD_Metadata,{urn:oasis:names:tc:ebxml-regrep:xsd:rim:3.0}\n RegistryPackage\";typeNames:List<String>=\"csw:Record,gmd:MD_Metadata,rim\n :RegistryPackage\";osgi.service.blueprint.compname=cswQueryFilterTransfo\n rmer,ddf.catalog.endpoint.CatalogEndpoint;osgi.service.blueprint.compna\n me=ddf.catalog.endpoint.csw,ddf.catalog.event.Subscriber;osgi.service.b\n lueprint.compname=CswSubscriptionSvc"))))

(deftest parse-import-package
  (is (= {::mf/Import-Package '("net.opengis.cat.csw.v_2_0_2"
                                 "net.opengis.cat.csw.v_2_0_2.dc.elements"
                                 "net.opengis.filter.v_1_1_0"
                                 "net.opengis.gml.v_3_1_1"
                                 "net.opengis.ows.v_1_0_0"
                                 "org.codice.ddf.spatial.ogc.csw.catalog.actions"
                                 "com.google.common.base"
                                 "com.google.common.collect"
                                 "com.google.common.io"
                                 "com.google.common.net"
                                 "com.sun.xml.bind.marshaller"
                                 "com.thoughtworks.xstream"
                                 "com.thoughtworks.xstream.converters"
                                 "com.thoughtworks.xstream.core")}
         (mf/parse-content "Import-Package: net.opengis.cat.csw.v_2_0_2;version=\"[2.8,3)\",net.opengi\n s.cat.csw.v_2_0_2.dc.elements;version=\"[2.8,3)\",net.opengis.filter.v_1_\n 1_0;version=\"[2.8,3)\",net.opengis.gml.v_3_1_1;version=\"[2.8,3)\",net.ope\n ngis.ows.v_1_0_0;version=\"[2.8,3)\",org.codice.ddf.spatial.ogc.csw.catal\n og.actions;version=\"[2.24,3)\",com.google.common.base;version=\"[25.1,26)\n \",com.google.common.collect;version=\"[25.1,26)\",com.google.common.io;ve\n rsion=\"[25.1,26)\",com.google.common.net;version=\"[25.1,26)\",com.sun.xml\n .bind.marshaller;version=\"[2.2,3)\",com.thoughtworks.xstream;version=\"[1\n .4,2)\",com.thoughtworks.xstream.converters;version=\"[1.4,2)\",com.though\n tworks.xstream.core;version=\"[1.4,2)\""))))

(deftest parse-import-service
  (is (= {::mf/Import-Service '("org.codice.ddf.cxf.client.ClientFactoryFactory"
                                 "ddf.catalog.filter.FilterAdapter"
                                 "ddf.catalog.data.MetacardType"
                                 "com.thoughtworks.xstream.converters.Converter"
                                 "ddf.catalog.event.EventProcessor"
                                 "ddf.catalog.transform.MetacardTransformer"
                                 "ddf.catalog.transform.QueryFilterTransformerProvider"
                                 "ddf.security.service.SecurityManager"
                                 "org.codice.ddf.security.Security"
                                 "ddf.catalog.transform.QueryResponseTransformer"
                                 "ddf.catalog.transform.InputTransformer"
                                 "ddf.catalog.filter.FilterBuilder"
                                 "ddf.catalog.CatalogFramework"
                                 "org.codice.ddf.spatial.ogc.csw.catalog.endpoint.transformer.CswActionTransformer"
                                 "ddf.catalog.data.AttributeRegistry")}
         (mf/parse-content "Import-Service: org.codice.ddf.cxf.client.ClientFactoryFactory;multiple:\n =false,ddf.catalog.filter.FilterAdapter;multiple:=false,ddf.catalog.dat\n a.MetacardType;multiple:=false;filter=(name=csw:Record),com.thoughtwork\n s.xstream.converters.Converter;multiple:=false;filter=(id=csw),ddf.cata\n log.event.EventProcessor;multiple:=false,ddf.catalog.transform.Metacard\n Transformer;multiple:=true,ddf.catalog.transform.QueryFilterTransformer\n Provider;multiple:=false,ddf.security.service.SecurityManager;multiple:\n =false,org.codice.ddf.security.Security;multiple:=false,ddf.catalog.tra\n nsform.QueryResponseTransformer;multiple:=true,ddf.catalog.transform.In\n putTransformer;multiple:=true;filter=(|(mime-type=application/xml)(mime\n -type=text/xml)),ddf.catalog.filter.FilterBuilder;multiple:=false,ddf.c\n atalog.CatalogFramework;multiple:=false,org.codice.ddf.spatial.ogc.csw.\n catalog.endpoint.transformer.CswActionTransformer;multiple:=true;availa\n bility:=optional,ddf.catalog.data.AttributeRegistry;multiple:=false"))))

#_(deftest package-lines-split-delim-only
    (is (= [] (mf/-sp-comma ",")) "Splitting on delimiter alone should yield no results")
    (is (= [] (mf/-sp-comma ",,,,")) "Splitting on delimiter alone should yield no results"))

#_(deftest package-lines-split-version-range-only
    (is (= ["(2.8,3)"] (mf/-sp-comma "(2.8,3)")) "Version range should be immune to splitting")
    (is (= ["[2.8,3)"] (mf/-sp-comma "[2.8,3)")) "Version range should be immune to splitting")
    (is (= ["(2.8,3]"] (mf/-sp-comma "(2.8,3]")) "Version range should be immune to splitting")
    (is (= ["[2.8,3]"] (mf/-sp-comma "[2.8,3]")) "Version range should be immune to splitting"))

#_(deftest package-simple-lines-split-correctly
    (is (= ["org.one"]
           (mf/-sp-comma "org.one"))
        "Simple packages should be getting split")

    (is (= ["org.one" "org.two"]
           (mf/-sp-comma "org.one,org.two"))
        "Simple packages should be getting split")

    (is (= ["org.one" "org.two" "org.three"]
           (mf/-sp-comma "org.one,org.two,org.three"))
        "Simple packages should be getting split"))

#_(deftest package-lines-with-versions-split-correctly
    (is (= ["my.test.thing" "my.other.thing;version=\"[2.8,3)\""]
           (mf/-sp-comma "my.test.thing,my.other.thing;version=\"[2.8,3)\""))
        "Packages with versions should be getting split")

    (is (= ["[2.8,3)\"" "net.opengis.cat.csw.v_2_0_2.dc.elements;version=\"[2.8,3)\"" "net."]
           (mf/-sp-comma "[2.8,3)\",net.opengis.cat.csw.v_2_0_2.dc.elements;version=\"[2.8,3)\",net."))
        "Packages between versions should be getting split"))

#_(deftest package-simple-names-detected
    (is (= "javax.activation" (mf/-sp-package "javax.activation"))
        "Simple package should be detected")
    (is (= "javax.xml.ws.handler" (mf/-sp-package "javax.xml.ws.handler"))
        "Simple package should be detected"))

#_(deftest package-numeric-names-detected
    (is (= "org.v1" (mf/-sp-package "org.v1"))
        "Numeric characters should be supported in packages after the root (org.v1)")
    (is (= "org.xmlpull.v1" (mf/-sp-package "org.xmlpull.v1"))
        "Numeric characters should be supported in packages after the root (org.xmlpull.v1)"))

#_(deftest package-names-with-version-detected
    (is (= "com.thoughtworks.xstream.security"
           (mf/-sp-package "com.thoughtworks.xstream.security;version=\"[1.4,2)\""))
        "Package with OSGi version info should be extractable"))

#_(deftest class-names-with-blueprint-info-detected
    (is (= "ddf.catalog.event.Subscriber"
           (mf/-sp-package
             "ddf.catalog.event.Subscriber;osgi.service.blueprint.compname=CswSubscriptionSvc"))
        "Class with blueprint info should be extractable"))

#_(deftest package-or-class-names-with-properties-detected
    (is (= "org.codice.ddf.spatial.ogc.csw.catalog.endpoint.mappings"
           (mf/-sp-package
             (str "org.codice.ddf.spatial.ogc.csw.catalog.endpoint.mappings;"
                  "uses:=\"org.codice.ddf.spatial.ogc.csw.catalog.endpoint.transformer")))
        "Package with uses:= metadata should be extractable")

    (is (= "ddf.catalog.transform.InputTransformer"
           (mf/-sp-package
             (str "ddf.catalog.transform.InputTransformer;"
                  "multiple:=true;filter=(|(mime-type=application/xml)(mime-type=text/xml))")))
        "Class name with multiple:= & filter= metadata should be extractable")

    (is (= "ddf.catalog.transform.QueryFilterTransformer"
           (mf/-sp-package
             (str "ddf.catalog.transform.QueryFilterTransformer;id:List<String>"
                  "=\"{http://www.opengis.net/cat/csw/2.0.2}Record")))
        "Interface name with type metadata should be extractable"))

#_(deftest package-or-class-noise-and-extra-data-ignored
    (is (= nil (mf/-sp-package "gmd:MD_Metadata"))
        "Noisy data should be omitted (gmd:MD_Metadata)")

    (is (= nil (mf/-sp-package (str "{urn:oasis:names:tc:ebxml-regrep:xsd:rim:3.0}"
                                    "RegistryPackage\";typeNames:List<String>=\"csw:Record")))
        "Noisy data should be omitted ({urn:oasis:names)")

    (is (= nil (mf/-sp-package "{http://www.isotc211.org/2005/gmd}MD_Metadata"))
        "Noisy data should be omitted ({http://www.isotc211.org/2005/gmd})")

    (is (= nil (mf/-sp-package
                 "rim:RegistryPackage\";osgi.service.blueprint.compname=cswQueryFilterTransformer"))
        "Noisy data should be omitted (rim:RegistryPackage)"))