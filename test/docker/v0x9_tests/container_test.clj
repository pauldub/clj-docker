(ns docker.v0x9-tests.container-test
  (:require [midje.sweet :refer :all]
            [taoensso.timbre :as log]
            [docker.client :as dc]
            [docker.image :as image]
            [docker.container :as container]))

(def default-url "10.0.100.2:4243")


;; tests for attach

(defn setup-container [client]
  (image/create client "lapax/tiny-haproxy")
  (log/debug (image/show-all client))
  (container/create client {:Image "lapax/tiny-haproxy"
                            :Tty true
                            :Cmd ["/bin/sh" "-l"]}))

(defn tear-down-container [client box]
  (container/remove client (:Id box)))


(facts "it attaches into the container"
  (let [client (dc/make-client default-url)
        box (setup-container client)]
    (log/debug box)
    (fact "it gets response from container"
      (slurp (container/attach client (:Id box))) => "")
    (tear-down-container client box)))
