(ns docker.v0x9-tests.image-test
  (:require [midje.sweet :refer :all]
            [midje.config :as config]
            [org.httpkit.client :as http]
            [cheshire.core :refer [generate-string parse-string]]
            [docker.core :refer [make-client]]
            [docker.image :refer :all]))

(def default-host "http://10.0.100.2:4243")

(fact "show-all returns no items when docker has no images yet"
  (let [docker (make-client default-host)]
    (count (show-all docker)) => 0))

(facts "creating new docker image"
  (fact "creates new image by pulling it from index"
    (let [docker (make-client default-host)
          resp1 (create docker "lapax/tiny-haproxy")]
      (count (show-all docker)) => 1)))


