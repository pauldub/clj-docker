(ns docker.client
  (:require [org.httpkit.client :as http]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [slingshot.slingshot :refer [throw+ try+]]
            [taoensso.timbre :refer (debug warn error)]))

;; -- JSON PARSERS
(defn parse-json
  "parses json string into Clojure data object.
  NB! All keys are clojure keywords!"
  [content]
  (try+
    (json/parse-string content true)
    (catch Object _
      (error (:throwable &throw-context)))))

(defn parse-stream
  "parses json-stream into collection of Clojure datacollection.
  NB! All keys are turned into keywords."
  [body]
  (try+
    (json/parse-stream (io/reader body) true)
    (catch Object _
      (error (:throwable &throw-context)))))

(defn save-stream
  "Saves byte stream into file."
  [content file-path]
  (io/copy
    content ;; (io/reader content)
    (io/file file-path))
  file-path)

;; -- CLIENT PROTOCOLS

(defprotocol URLBuilder
  (to-url [this path] [this path query-params]))

(defprotocol RPC
  (rpc-get  [this path] [this path params] "makes HTTP/GET request")
  (rpc-post [this path params] "makes HTTP/POST request to the API endpoint.")
  (rpc-delete [this path] [this path params] "makes HTTP/DELETE request to the API endpoint"))

(defprotocol StreamedRPC
  (stream-get [this path] [this url params])
  (stream-post [this path params]))

;; -- CLIENT implementation

(defrecord HTTPKitClient [host client-options index-url]
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
  (rpc-post [this path request-map]
    @(http/request
       (merge {:method :post, :url (to-url this path)}
              (:client-options this)
              request-map)))
  (rpc-delete [this path]
    (rpc-delete this path nil))
  (rpc-delete [this path request-map]
    @(http/request
       (merge {:method :delete, :url (to-url this path)}
              (:client-options this)
              request-map)))
  StreamedRPC
  (stream-get [this path]
    (stream-get this path nil))
  (stream-get [this path request-map]
    (http/request
       (merge {:method :get, :url (to-url this path), :as :stream}
              (:client-options this)
              request-map)))
  (stream-post [this path request-map]
    (http/request
      (merge {:method :post, :url (to-url this path), :as :stream}
              (:client-options this)
              request-map))))



(def default-index-url "https://index.docker.io/v1")
(def default-client-options {:user-agent "clj-docker (httpkit 2.1.17)"
                             :keep-alive 30000
                             :timeout 1000
                             :headers {"Accept" "application/json"
                                       "Content-Type" "application/json"
                                       "X-Docker-Registry-Version" "v1"}})

(defn make-client
  "Creates new client to access Docker agent."
  ([host] (make-client host nil nil))
  ([host client-opts] (make-client host client-opts nil))
  ([host client-opts index-url]
    (HTTPKitClient. host
                    (merge default-client-options client-opts)
                    (if (nil? (seq index-url))
                      default-index-url
                      index-url))))

