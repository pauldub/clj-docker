(ns docker.core
  (:require [docker.client :as dc]
            [slingshot.slingshot :refer [throw+ try+]]
            [taoensso.timbre :as log]
            [cheshire.core :refer [generate-string]]
            [clojure.data.codec.base64 :as base64]))


(def exceptions
  {500 {:type ::server_error
        :message "Fatal error in Docker agent."}
   :uf {:type ::unspecified_error
        :message "Unspecified error. Probably timeout exception due wrong URl."}})

(def make-client dc/make-client)
(def response-handler (dc/make-response-handler exceptions))

(defn version
  "Shows the docker version used on host.
  Usage:
    (def docker (make-client))
    (version docker)"
  [client]
  (response-handler
    (dc/rpc-get client "/version")
    dc/parse-json))

(defn events
  "Gets events from dockers - when since is unspecified then returns events for last 10s
  New! Works with docker(>0.9)
  usage:
    (events docker) ;; returns events from last 10 seconds
    (events docker 10030300) ;; returns events since unix time;
    (def now (-> (System/currentTimeMillis) (/ 1000) int)
    (events docker (- now 10))"
  ([client]
    (let [timeago 10 ;; 10 seconds
          current-epoch (-> (System/currentTimeMillis) (/ 1000) int)
          since (- current-epoch timeago)]
      (events client (- current-epoch since))))
  ([client since]
    (response-handler
      (dc/rpc-get client "/events" {:query-params {:since since}})
      dc/parse-json)))

(defn info
  "displays system-wide information
  Usage:
    (info docker)"
  [client]
  (response-handler
    (dc/rpc-get client "/info")
    dc/parse-json))

(defn encode-auth-config [auth-config]
  "encodes auth-config map into token"
  (-> auth-config generate-string (.getBytes) base64/encode String.))

(defn authorize
  "check auth configuration
  Returns client which have authorization info."
  [client username password email]
  (let [auth-config {:username username
                     :password password
                     :email email
                     :serveraddress (:index-url client)}
        auth-token (encode-auth-config auth-config)]
    (response-handler
      (dc/rpc-post
        client "/auth"
        {:body (generate-string auth-config)
         :debug true})
      ;; when authorization data was correct, add it to client
      (fn [body]
        (assoc client :auth-token auth-token)))))

(comment
  ;; which part are not refactored
  ;; i didnt see any docs where these methods are required
  ;; or why client should care about compressing and does docker agent accepts it
  (:import  (org.apache.commons.compress.archivers.tar TarArchiveInputStream
                                                       TarArchiveOutputStream
                                                       TarArchiveEntry))
  (defn rpc-post-tar [url file params]
    (let [resp (http/post (str *docker-host* url) {:headers {"Content-Type" "application/tar"}
                                                  :body (slurp file)})]
      (if (or (= 200 (:status @resp)) (= 201 (:status @resp)))
        (:body @resp)
        (println (:status @resp) (:reason @resp)))))

  (defn build [{:keys [path tag quiet dockerfile]
                :or {:tag nil :quite false}}]
    (let [tmpfile (java.io.File/createTempFile "build" "tar")
          file (clojure.java.io/file tmpfile)
          entry (TarArchiveEntry. "Dockerfile")
          out (TarArchiveOutputStream. (clojure.java.io/output-stream file))
          buf (byte-array (map byte dockerfile))]
      (.setSize entry (count buf))
      (.putArchiveEntry out entry)
      (.write out buf 0 (count buf))
      (.closeArchiveEntry out)
      (.finish out)
      (.close out)
      (second (re-find #"Successfully built ([\w\d]+)" (rpc-post-tar "/build" file {:tag tag :remote nil :q quiet}))))
  )) ;; end of comment
