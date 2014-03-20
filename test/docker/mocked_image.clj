(ns docker.mocked-image
  (:require [midje.sweet :refer :all]
            [midje.config :as config]
            [org.httpkit.client :as http]
            [org.httpkit.fake :refer [with-fake-http]]
            [clojure.java.io :as io]
            [cheshire.core :refer [generate-string generate-stream]]
            [docker.client :refer [make-client]]
            [docker.image :refer :all]))

(def default-host "http://10.0.1.2:4243")

(facts "show-all"
  (let [docker (make-client default-host)
        image1 {:Id "x1"}
        image2 {:Id "x2"}]
    (fact "shows all images on docker's host."
      (with-fake-http [#"/images/json$" (generate-string [image1 image2])]
        (let [images (show-all docker)]
          (count images) => 2
          (-> images first :Id) => "x1"
          (-> images second :Id) => "x2")))
    (fact "shows only n latest items"
      (with-fake-http [#"/images/json" (generate-string [image1])]
        (let [images (show-all docker :all 1)]
          (count images) => 1
          (-> images first :Id) => "x1")))))

(facts "create new image"
  (let [docker (make-client default-host)
        test-file "/tmp/docker-create-image"
        resp1 (generate-stream [{:status "Pulling"}
                                {:status "Pulling"}
                                {:status "Done"}]
                               (io/writer test-file))]
    (fact "using plain settings imports correct data"
      (with-fake-http [#"/images/create$" {:status 200
                                           :body (.getBytes (slurp test-file) "utf8")}]
        (let [msgs (create docker "test/image")]
          (println msgs)
          (first msgs)  => {:status "Pulling"}
          (second msgs) => {:status "Pulling"}
          (nth msgs 2)  => {:status "Done"})))

    (fact "raises correct exception when server fails"
      (with-fake-http [#"/images/create$" {:status 500
                                           :body "server down"}]
        (create docker "test/kaputt") => (throws Exception)))))


