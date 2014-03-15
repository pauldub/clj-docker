(ns docker.mocked-image
  (:require [midje.sweet :refer :all]
            [midje.config :as config]
            [org.httpkit.client :as http]
            [org.httpkit.fake :refer [with-fake-http]]
            [cheshire.core :refer [generate-string parse-string]]
            [docker.core :refer [make-client]]
            [docker.image :refer :all]))

(def default-host "http://10.0.1.2:4243")

(facts "show-all"
  (let [docker (make-client default-host)
      image1 {:Id "x1"}
      image2 {:Id "x2"}]
    (fact "shows all images on docker's host."
        (with-fake-http ["/images" (generate-string [image1 image2])]
          (let [images (show-all docker)]
            (count images) => 2
            (-> images first :Id) => "x1"
            (-> images second :Id) => "x2")))
    (fact "shows only n latest items")))
