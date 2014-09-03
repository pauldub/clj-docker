(defproject docker "0.2.0"
  :description "A work in progress docket API client"
  :url "https://github.com/pauldub/clj-docker"
  :license {:name "MIT License"
            :url "http://choosealicense.com/licenses/mit"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [http-kit "2.1.19"]
                 [com.taoensso/timbre "3.1.2"]
                 [com.cemerick/url "0.1.1"]
                 [slingshot "0.10.3"]
                 [cheshire "5.3.1"]
                 [org.clojure/data.codec "0.1.0"]]
  :plugins [[lein-shell "0.4.0"]
            [lein-midje "3.1.3"]
            [codox "0.6.7"]]
  :java-source-paths ["src/java"]
  :profiles {:dev {:dependencies [[midje "1.6.3"]
                                  [http-kit.fake "0.2.2"]]}}
  :codox {:output-dir "doc/codox"
          :writer codox.writer.html/write-docs
          :src-dir-uri "http://github.com/tauho/clj-docker/blob/master"
          :src-linenum-anchor-prefix "L"})
