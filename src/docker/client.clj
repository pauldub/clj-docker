(ns docker.client
  (:require [org.httpkit.client :as http]
            [cheshire.core :refer [parse-string]]
            [slingshot.slingshot :refer [throw+ try+]]
            [taoensso.timbre :refer (debug warn error)]))


(defprotocol URLBuilder
  (to-url [this path] [this path query-params]))

(defprotocol ResponseParser
  (parse-json [this response]))

(defprotocol RPC
  (rpc-get [this path] [this path params] "makes HTTP/GET request")
  (rpc-post [this path] [this path params]))

(defprotocol StreamedRPC
  (stream-get [this url params]))

(defrecord HTTPKitClient [host client-options]
  URLBuilder
  (to-url [this path]
    (str (:host this) "" path))
  RPC
  (rpc-get [this path]
    (rpc-get this path nil))
  (rpc-get [this path request-map]
    @(http/request
      (merge {:method :get, :url (to-url this path)}
             (:client-options this)
             request-map)))
  ResponseParser
  (parse-json [this response]
    (try+
      (parse-string response true)
      (catch Object _
        (error (:throwable &throw-context))))))


(def default-client-options {:user-agent "clj-docker (httpkit 2.1.17)"
                             :keep-alive 30000
                             :timeout 1000})

(defn make-client
  ""
  ([host] (HTTPKitClient. host default-client-options))
  ([host client-opts] (HTTPKitClient. host (merge default-client-options client-opts))))


