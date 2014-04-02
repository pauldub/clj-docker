(ns docker.client
  (:require [org.httpkit.client :as http]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [slingshot.slingshot :refer [throw+ try+]]
            [taoensso.timbre :refer (debug warn error)])
  (:import [org.httpkit.ws WebSocketClient]))

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
    content
    (io/file file-path))
  file-path)

;; -- CLIENT PROTOCOLS

(defprotocol URLBuilder
  (to-url [this path] [this path query-params])
  (to-ws-url [this path] [this path query-params]))

(defprotocol RPC
  (rpc-get  [this path] [this path params] "makes HTTP/GET request")
  (rpc-post [this path params] "makes HTTP/POST request to the API endpoint.")
  (rpc-delete [this path] [this path params] "makes HTTP/DELETE request to the API endpoint"))

(defprotocol StreamedRPC
  (stream-get [this path] [this url params])
  (stream-post [this path params]))

(defprotocol WebsocketStream
  (stream-ws [this path handler] [this path handler params]))

;; -- CLIENT implementation
;; TODO: remove stringy-options and add more robust version for URL builder
;; TODO: it has to handle unix urls too

(defn- stringify-options
  "turns query map into query string"
  [query-options]
  (->> query-options
    seq
    (map (fn [[k,v]] (format "%s=%s" (name k) v)))
    (interpose "&")
    (apply str)))

(defn- build-request-map
  "builds request map by combining default settings and user's request"
  [client method url user-request-map]
  (let [headers-map (merge {}
                      (get-in client [:client-options :headers])
                      (get user-request-map :headers {}))]
    (merge
      {:method method, :url url}
      (:client-options client) ;; a default configs
      (dissoc user-request-map :headers)
      {:headers headers-map})))

(defrecord HTTPKitClient [host version client-options index-url]
  URLBuilder
  (to-url [this path]
    (format "http://%s/%s%s" (:host this) (:version this) path))
  (to-ws-url [this path]
    (to-ws-url this path nil))
  (to-ws-url [this path query-options]
    (let [params (stringify-options query-options)]
      (str "ws://" (:host this) "/" (:version this)
           (when-not (empty? params) (str "?" params)))))
  RPC
  (rpc-get [this path]
    (rpc-get this path nil))
  (rpc-get [this path request-map]
    @(http/request
      (build-request-map this :get (to-url this path) request-map)))
  (rpc-post [this path request-map]
    @(http/request
      (build-request-map this :post (to-url this path) request-map)))
  (rpc-delete [this path]
    (rpc-delete this path nil))
  (rpc-delete [this path request-map]
    @(http/request
      (build-request-map this :delete (to-url this path) request-map)))
  StreamedRPC
  (stream-get [this path]
    (stream-get this path nil))
  (stream-get [this path request-map]
    (http/request
      (build-request-map this
                         :get (to-url this path)
                         (assoc request-map :as :stream))))
  (stream-post [this path request-map]
    (http/request
      (build-request-map this
                         :post (to-url this path)
                         (assoc request-map :as :stream))))
  WebsocketStream
  (stream-ws [this path handler] (stream-ws this path handler {}))
  (stream-ws [this path handler params]
    (let [ws-url (to-ws-url this path)
          ws-client (WebSocketClient. ws-url)])))

(def default-index-url "https://index.docker.io/v1/")
(def default-client-options {:host "10.0.100.2:4243"
                             :version "v1.10"
                             :user-agent "clj-docker (httpkit 2.1.17)"
                             :keep-alive 30000
                             :timeout 1000 ;; use -1 for long running request
                             :headers {"Accept" "application/json"
                                       "Content-Type" "application/json"
                                       "X-Docker-Registry-Version" "v1"}})


(defn make-response-handler [exceptions]
  (fn response-handler [{:keys [status body error]} callback-fn]
    (if (< 199 status 300)
      (callback-fn body)
      (throw+
        (merge
          (get exceptions status (:uf exceptions))
          {:content body :error error :status status})))))

(defn make-client
  "Creates new client to access Docker agent.
  Arguments:
    host - url of the docker agent
  Optional arguments
    client-opts   - a clojure map, which includes client options
    :index-url    - a url where docker images are hosted
    :version      - a version string of remote api, default v1.10
  Usage:
    (make-client \"10.0.10.2:4243\")
    (make-client \"10.0.10.2:4243\" {:timeout 10000})
    (make-client \"10.0.10.2:4242\" nil :version \"1.10\")"
  ([host] (make-client host {}))
  ([host client-opts & {:keys [index-url version]
                        :or {index-url default-index-url
                             version "v1.10"}}]
    (HTTPKitClient. host
                    version
                    (merge default-client-options client-opts)
                    index-url)))

