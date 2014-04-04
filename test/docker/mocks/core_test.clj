(ns docker.mocks.core-test
  (:require [midje.config :as config]
            [org.httpkit.client :as http]
            [org.httpkit.fake :refer [with-fake-http]]
            [cheshire.core :refer [generate-string parse-string]]
            [docker.core :refer :all])
  (:use midje.sweet))


(def default-url "http://10.0.1.2:4243")

;;TODO: test does it throws exceptions for edge cases

(facts "accessing /version endpoint"
  (fact "returns correct document when service is running"
    (with-fake-http [#"\/version$" (generate-string {:Version "0.8.1"})]
        (let [docker (make-client default-url)]
          (version docker) => {:Version "0.8.1"}))))

(facts "/events endpoint"
  (fact "it returns right data when using without args"
    (let [docker (make-client default-url)
          event_item {:status "start"
                      :id "abc342"
                      :from "base:latest"
                      :time "1340012340"}]
      (with-fake-http [#"\/events$" (generate-string [event_item])]
        (first (events docker)) => event_item)))

  (fact "it returns correct events item with the since argument"
    (let [docker (make-client default-url)
          event_item {:status "start"
                      :id "abc342"
                      :from "base:latest"
                      :time "1340012340"}
          since (-> (System/currentTimeMillis) (/ 1000) int (- 10))]
      (with-fake-http [#"\/events$" (generate-string [event_item])]
        (first (events docker since)) => event_item))))

(facts "/info endpoint"
  (fact "it gets proper response from docker agent."
    (let [docker (make-client default-url)
          info_item {:Debug 0
                     :Driver "aufs"
                     :KernelVersion "3.13.5+"}]
      (with-fake-http [#"\/info" (generate-string info_item)]
        (info docker) => info_item))))

(facts "/auth endpoint"
  (fact "it authorizes users correctly"
    (let [docker (make-client default-url)]
      (with-fake-http [#"\/auth" "OK"]
        (let [authorized-client (authorize docker "tauhotest"
                                           "qwerty_test" "info@tauho.com")]
          (contains? authorized-client :auth-token) => true)))))


