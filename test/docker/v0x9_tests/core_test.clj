(ns docker.v0x9-tests.core-test
  (:require [midje.config :as config]
            [org.httpkit.client :as http]
            [org.httpkit.fake :refer [with-fake-http]]
            [cheshire.core :refer [generate-string parse-string]]
            [docker.core :refer :all])
  (:use midje.sweet))

;; ----------------------------------------------------------------------------
;; NB! This spec expects that you had started test-machine before executing those
;; specs: check scripts folder for bootstrapping scripts to set up test machines.
;;

(def default-url "http://10.0.100.2:4243")

;;TODO: test does it throws exceptions for edge cases

(facts "accessing /version endpoint"
  (fact "returns correct document when service is running"
    (let [docker (make-client default-url)
          item {:Arch "amd64", :Os "linux", :Version "0.9.0"}
          resp1 (version docker)]
      (:Arch resp1) => "amd64"
      (:Os resp1) => "linux"
      (:Version) => "0.9.0")))


;;TODO: fails when no events
(facts "/events endpoint"
  (fact "it returns right data when using without args"
    (let [docker (make-client default-url)
          resp1 (events docker)]
         (coll? resp1) => true))

  (fact "it returns correct events item with the since argument"
    (let [docker (make-client default-url)
          since (-> (System/currentTimeMillis) (/ 1000) int (- 10))]
        (map? (first (events docker since))) => true)))

(facts "/info endpoint"
  (fact "it gets proper response from docker agent."
    (let [docker (make-client default-url)
          resp1 (info docker)]
        (contains? resp1 :Containers) => true)
        (contains? resp1 :KernelVersion) => true))

(facts "/auth endpoint"
  (fact "it authorizes users correctly"
    (let [docker (make-client default-url)]
      (authorize docker "tauhotest" "qwerty_test" "info@tauho.com") => true)))


