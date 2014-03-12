(ns docker.mocked-core
  (:require [midje.config :as config])
  (:use midje.sweet))


(facts "test docker core functions"
  (fact "dummy test"
        (+ 1 1) => 3))

