"Tests for docker.client."

(ns docker.mocks.client-test
  (:require [midje.sweet :refer :all]
            [midje.config :as config]
            [docker.client :refer :all]
            [org.httpkit.fake :refer (with-fake-http)]))

(def default-host "http://httpbin.org")

(facts "HTTPKitClient constructor"
  (fact "creates constructor for given host"
    (let [tor (make-client default-host)]
      (:host tor) => default-host
      (-> tor :client-options :timeout) => 1000))
  (fact "creates constructor and sets client options"
    (let [tor (make-client default-host {:timeout 2000})]
      (:host tor) => default-host
      (get-in tor [:client-options :timeout]) => 2000)))

(facts "HTTPKitClient and JSONParser"
  (fact "parses correct string correctly"
    (let [tor (make-client default-host)]
      (parse-json "[1,2,3]") => [1,2,3]
      (parse-json "{\n  \"origin\": \"84.248.1.138\"\n}") => {:origin "84.248.1.138"})))

(facts "HTTPKitClient get method"
  (let [tor (make-client default-host)]
    (fact "rpc-get gets correct data from API path"
      (with-fake-http [#"/ip" "10.0.0.1"]
        (let [{:keys [status body error]} (rpc-get tor "/ip")]
          status => 200
          body => "10.0.0.1"
          error => nil)))
    (fact "rpc-get get correct data from API path and supplied params"
      (with-fake-http [#"/ip" "10.0.0.2"]
        (let [{:keys [status body error] :as resp} (rpc-get tor "/ip" {:query-params {"a" 1}})]
          status => 200
          body => "10.0.0.2"
          error => nil
          (-> resp :opts :query-params) => {"a" 1})))))


