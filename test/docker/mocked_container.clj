(ns docker.mocked-container
  (:refer-clojure :exclude [remove])
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

(facts "exports a container"
  (let [client (make-client default-host)]
    (fact "downloads content into the file"
      (with-fake-http [#"/containers/abc123/export" {:status 200
                                                     :body (.getBytes "mockingbird")}]
        (export client "abc123" "/tmp") => (str "/tmp/abc123")
        (slurp "/tmp/abc123") => "mockingbird"))
    (fact "raises exception when container doesnt exist"
      (with-fake-http [#"/containers/abc123/export" {:status 400}]
        (export client "abc123" "/tmp") => (throws Exception)))
    (fact "raises exception when encounters internal server error"
      (with-fake-http [#"/containers/abc123/export" {:status 500}]
        (export client "abc123" "/tmp") => (throws Exception)))
    (fact "raises exception when encounter unspecified error"
      (with-fake-http [#"/containers/abc123/export" {:status 418}]
        (export client "abc123" "/tmp") => (throws Exception)))))

(facts "starts a container"
  (let [client (make-client default-host)]
    (fact "works with default settings"
      (with-fake-http [#"/containers/abc123/start" {:status 204}]
        (start client "abc123") => true))
    (fact "can add containers configuration"
      (with-fake-http [#"/containers/abc123/start" {:status 204}]
        (start client "abc123" {:Memory 2048}) => true))
    (fact "can add containers configuration and host-config"
      (with-fake-http [#"/containers/abc123/start" {:status 204}]
        (start client "abc123"
               {:Memory 2048}
               :host-config {:SomeVal 123}) => true))
    (fact "raises exception when container doesnt exist"
      (with-fake-http [#"/containers/abc123/start" {:status 404}]
        (start client "abc123") => (throws Exception)))
    (fact "raises exception when encounters internal server error"
      (with-fake-http [#"/containers/abc123/start" {:status 500}]
        (start client "abc123") => (throws Exception)))
    (fact "raises exception when gets unspecified error-code"
      (with-fake-http [#"/containers/abc123/start" {:status 418}]
        (start client "abc123") => (throws Exception)))))

(facts "stops a container"
  (let [client (make-client default-host)]
    (fact "works with default arguments"
      (with-fake-http [#"/containers/abc123/stop" {:status 204}]
        (stop client "abc123") => true))
    (fact "works also with the additional timeout argument"
      (with-fake-http [#"/containers/abc123/stop" {:status 204}]
        (stop client "abc123" 10) => true
        (stop client "abc123" 0) => true))
    (fact "raises exception when the container doesnt exist"
      (with-fake-http [#"/containers/abc123/stop" {:status 404}]
        (stop client "abc123") => (throws Exception)))
    (fact "raises exception when encounters internal server error"
      (with-fake-http [#"/containers/abc123/stop" {:status 500}]
        (stop client "abc123") => (throws Exception)))
    (fact "raises exception when got unspecified status code"
      (with-fake-http [#"/containers/abc123/stop" {:status 418}]
        (stop client "abc123") => (throws Exception)))))

(facts "restarts a container"
  (let [client (make-client default-host)]
    (fact "works with default arguments"
      (with-fake-http [#"/containers/abc123/restart" {:status 204}]
        (restart client "abc123") => true))
    (fact "works also with the additional timeout argument"
      (with-fake-http [#"/containers/abc123/restart" {:status 204}]
        (restart client "abc123" 10) => true
        (restart client "abc123" 0) => true))
    (fact "raises exception when the container doesnt exist"
      (with-fake-http [#"/containers/abc123/restart" {:status 404}]
        (restart client "abc123") => (throws Exception)))
    (fact "raises exception when encounters internal server error"
      (with-fake-http [#"/containers/abc123/restart" {:status 500}]
        (restart client "abc123") => (throws Exception)))
    (fact "raises exception when got unspecified status code"
      (with-fake-http [#"/containers/abc123/restart" {:status 418}]
        (restart client "abc123") => (throws Exception)))))

(facts "kills a container"
  (let [client (make-client default-host)]
    (fact "works with default arguments"
      (with-fake-http [#"/containers/abc123/kill" {:status 204}]
        (kill client "abc123") => true))
    (fact "raises exception when the container doesnt exist"
      (with-fake-http [#"/containers/abc123/kill" {:status 404}]
        (kill client "abc123") => (throws Exception)))
    (fact "raises exception when encounters internal server error"
      (with-fake-http [#"/containers/abc123/kill" {:status 500}]
        (kill client "abc123") => (throws Exception)))
    (fact "raises exception when got unspecified status code"
      (with-fake-http [#"/containers/abc123/kill" {:status 418}]
        (kill client "abc123") => (throws Exception)))))

(facts "waits for a container"
  (let [client (make-client default-host)]
    (fact "works with default arguments"
      (with-fake-http [#"/containers/abc123/wait"
                       {:status 200
                        :body (generate-string {:StatusCode 0})}]
        (wait client "abc123") => {:StatusCode 0}))
    (fact "raises exception when the container doesnt exist"
      (with-fake-http [#"/containers/abc123/wait" {:status 404}]
        (wait client "abc123") => (throws Exception)))
    (fact "raises exception when encounters internal server error"
      (with-fake-http [#"/containers/abc123/wait" {:status 500}]
        (wait client "abc123") => (throws Exception)))
    (fact "raises exception when got unspecified status code"
      (with-fake-http [#"/containers/abc123/wait" {:status 418}]
        (wait client "abc123") => (throws Exception)))))

(facts "removes a container"
  (let [client (make-client default-host)]
    (fact "returns success"
      (with-fake-http [#"/containers/abc123/remove" {:status 204}]
        (remove client "abc123") => true
        (remove client "abc123" :force false) => true
        (remove client "abc123" :volumes true) => true
        (remove client "abc123" :force true :volumes true) => true))
    (fact "raises exception if got bad arguments"
      (with-fake-http [#"/containers/abc123/remove" {:status 400}]
        (remove client "abc123") => (throws Exception)))
    (fact "raises exception if the container doesn exist"
      (with-fake-http [#"/containers/abc123/remove" {:status 404}]
        (remove client "abc123") => (throws Exception)))
    (fact "raises exception if the encountered internal server error"
      (with-fake-http [#"/containers/abc123/remove" {:status 500}]
        (remove client "abc123") => (throws Exception)))
    (fact "raises exception if got unspecified status code"
      (with-fake-http [#"/containers/abc123/remove" {:status 418}]
        (remove client "abc123") => (throws Exception)))))


(facts "copies file from the container"
  (let [client (make-client default-host)
        target-folder "/tmp"]
    (fact "creates file into target folder"
      (with-fake-http [#"/containers/abc123/copy" {:status 200
                                                   :body (.getBytes "kikka kukka")}]
        (copy-file client "abc123"
                   "/tmp/docker-container-copy.txt"
                   target-folder) => "/tmp/docker-container-copy.txt"
        (slurp "/tmp/docker-container-copy.txt") => "kikka kukka"))
    (fact "raises exception when the container doesnt exists"
      (with-fake-http [#"/containers/abc123/copy" {:status 404}]
        (copy-file client "abc123" "/tmp/d.x" "/tmp") => (throws Exception)))
    (fact "raises exception after server raised exceptions"
      (with-fake-http [#"/containers/abc123/copy" {:status 500}]
        (copy-file client "abc123" "/tmp/d.x" "/tmp") => (throws Exception)))
    (fact "raises exception when encountered unspecified status code"
      (with-fake-http [#"/containers/abc123/copy" {:status 418}]
        (copy-file client "abc123" "/tmp/d.x" "/tmp") => (throws Exception)))))



(facts "attach a container")



