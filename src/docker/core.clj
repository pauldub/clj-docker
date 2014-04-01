(ns docker.core
  (:require [docker.client :as dc]
            [slingshot.slingshot :refer [throw+ try+]]
            [taoensso.timbre :refer [debug warn error] :as log]
            [cheshire.core :refer [generate-string]])
  (:import  (org.apache.commons.compress.archivers.tar TarArchiveInputStream
                                                       TarArchiveOutputStream
                                                       TarArchiveEntry)))

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

;;TODO: finish it
;;after successful authorization it should keep authorization info
(defn authorize
  "check auth configuration
  Returns true if logins are correct."
  [client username password email]
  (response-handler
    (dc/rpc-post
      client "/auth"
      {:body (generate-string
                {:username username
                 :password password
                 :email email
                 :serveraddress (:index-url client)})})
    (fn [body] true)))

;; aka which part is not yet refactored
(comment

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
    ;; (let [dir (str "/" (clojure.string/trim-newline (mktemp "-d")))]
    ;;   (echo dockerfile {:out (java.io.File. (str dir "/Dockerfile"))})
    ;;   (println (tar (str dir))))
  )) ;; end of comment
