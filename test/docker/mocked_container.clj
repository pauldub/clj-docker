(ns docker.mocked-container
  (:require [midje.sweet :refer :all]
            [midje.config :as config]
            [org.httpkit.client :as http]
            [org.httpkit.fake :refer [with-fake-http]]
            [clojure.java.io :as io]
            [cheshire.core :refer [generate-string generate-stream]]
            [docker.client :refer [make-client]]
            [docker.container :refer :all]))

;;TODO: check exception content & type

(def default-host "http://10.0.1.2:4243")

(facts "show-all"
  (let [client (make-client default-host)
        con1 {:Id "abc1", :Image "test:latest"}
        con2 {:Id "abc2", :Image "test:12334"}]
    (fact "shows correct containers with default params"
      (with-fake-http [#"/containers/json" (generate-string [con1 con2])]
        (let [containers (show-all client)]
          (count containers) => 2
          (first containers) => con1
          (second containers) => con2)))
    (fact "shows only 1 and latest container"
      (with-fake-http [#"/containers/json" (generate-string [con1])]
        (first (show-all client :limit 1)) => con1))
    (fact "raises exceptions when using not valid value for param."
      (with-fake-http [#"/containers/json" {:status 400}]
        (show-all client :since 1001) => (throws Exception)))))


(facts "create new container"
  (let [client (make-client default-host)
        resp1 {:Id "e90d80", :Warnings []}]
    (fact "works with default params"
      (with-fake-http [#"/containers/create" {:status 201
                                              :body (generate-string resp1)}]
        (create client "container1") => resp1))
    (fact "works with user configration"
      (with-fake-http [#"/containers/create" {:status 201
                                              :body (generate-string resp1)}]
        (create client "container1" {:Memory 0 :Cmd ["echo 1"]}) => resp1))
    (fact "raises exception when container doesnt exists"
      (with-fake-http [#"/containers/create" {:status 404}]
        (create client "container1") => (throws Exception)))
    (fact "raises exception when cant attach the container"
      (with-fake-http [#"/containers/create" {:status 406}]
        (create client "container2") => (throws Exception)))
    (fact "raises exception when catches server error"
      (with-fake-http [#"/containers/create" {:status 500}]
        (create client "container3") => (throws Exception)))
    (fact "raises exception when catches unspecified error"
      (with-fake-http [#"/containers/create" {:status 418}]
        (create client "container4") => (throws Exception)))))

(facts "show container's information"
  (let [client (make-client default-host)
        resp1 {:Id "abc123", :State {:Running false}}]
    (fact "shows correct containers information"
      (with-fake-http [#"/containers/abc123/json" (generate-string resp1)]
        (inspect client "abc123") => resp1))
    (fact "raises exception when base image doesnt exist"
      (with-fake-http [#"/containers/abc123/json" {:status 404}]
        (inspect client "abc123") => (throws Exception)))
    (fact "raises exception when docker's agent has internal exception"
      (with-fake-http [#"/containers/abc123/json" {:status 500}]
        (inspect client "abc123") => (throws Exception)))
    (fact "raises exception when server responses with unspecified status code"
      (with-fake-http [#"/containers/abc123/json" {:status 418}]
        (inspect client "abc123") => (throws Exception)))))

(facts "show top processes"
  (let [client (make-client default-host)
        resp1 {:Titles ["USER", "PID"]
               :Process [["root", "101"]]}]
    (fact "shows a correct information of the container"
      (with-fake-http [#"/containers/abc123/top" (generate-string resp1)]
        (top client "abc123") => resp1))
    (fact "raises exception when the `id` doesnt exists"
      (with-fake-http [#"/containers/abc123/top" {:status 404}]
        (top client "abc123") => (throws Exception)))
    (fact "raises exception when server encounter internal error"
      (with-fake-http [#"/containers/abc123/top" {:status 500}]
        (top client "abc123") => (throws Exception)))
    (fact "raises exception when encounters unspecified error"
      (with-fake-http [#"/containers/abc123/top" {:status 418}]
        (top client "abc123") => (throws Exception)))))

(facts "show changes on container"
  (let [client (make-client default-host)
        change1 {:Path "/dev" :Kind 0}
        change2 {:Path "/dev/kmesg" :Kind 1}]
    (fact "shows a correct response"
      (with-fake-http [#"/containers/abc123/changes" (generate-string [change1 change2])]
        (let [resp (changes client "abc123")]
          (first resp) => change1
          (second resp) => change2)))
    (fact "raises exception when the `id` doesnt exist"
      (with-fake-http [#"/containers/abc124/changes" {:status 404}]
        (changes client "abc124") => (throws Exception)))
    (fact "raises exception when encounters internal server error"
      (with-fake-http [#"/containers/abc124/changes" {:status 500}]
        (changes client "abc124") => (throws Exception)))
    (fact "raises exception when encounters unspecified error"
      (with-fake-http [#"/containers/abc124/changes" {:status 418}]
        (changes client "abc124") => (throws Exception)))))

(facts "exports a container")

