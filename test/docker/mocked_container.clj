(ns docker.mocked-container
  (:require [midje.sweet :refer :all]
            [midje.config :as config]
            [org.httpkit.client :as http]
            [org.httpkit.fake :refer [with-fake-http]]
            [clojure.java.io :as io]
            [cheshire.core :refer [generate-string generate-stream]]
            [docker.client :refer [make-client]]
            [docker.container :refer :all]))

(def default-host "http://10.0.1.2:4243")

(facts "show-all"
  (let [client (make-client default-host)
        con1 {:Id "abc1", :Image "test:latest"}
        con2 {:Id "abc2", :Image "test:12334"}]
    (facts "shows correct containers with default params"
      (with-fake-http [#"/containers/json" (generate-string [con1 con2])]
        (let [containers (show-all client)]
          (count containers) => 2
          (first containers) => con1
          (second containers) => con2)))
    (facts "shows only 1 and latest container"
      (with-fake-http [#"/containers/json" (generate-string [con1])]
        (first (show-all client :limit 1)) => con1))
    (facts "raises exceptions when using not valid value for param."
      (with-fake-http [#"/containers/json" {:status 400}]
        (show-all client :since 1001) => (throws Exception)))))
