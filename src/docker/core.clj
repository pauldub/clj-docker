(ns docker.core
  (:require [docker.client :as dc]
            [slingshot.slingshot :refer [throw+ try+]]
            [taoensso.timbre :refer [debug warn error] :as log]
            [cheshire.core :refer [generate-string]])
  (:import  (org.apache.commons.compress.archivers.tar TarArchiveInputStream
                                                       TarArchiveOutputStream
                                                       TarArchiveEntry)))

(def exceptions {:server-error {:type ::server_error
                                :status 500
                                :message "Fatal error in Docker agent."}
                 :unspecified  {:type ::unspecified_error
                                :status 418
                                :message "Unspecified error. Probably timeout exception due wrong URl."}})

(def make-client dc/make-client)

(defn version
  "Shows the docker version used on host.
  Usage:
    (def docker (make-client))
    (version docker)"
  [client]
  (let [{:keys [status body error]} (dc/rpc-get client "/version")]
    (case status
      200 (dc/parse-json body)
      500 (throw+ (:server-error exceptions))
      (throw+ (:unspecified exceptions)))))

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
    (debug "reading events of docker from" (:url client))
    (let [{:keys [status body error]} (dc/rpc-get client "/events"
                                                         {:query-params {:since since}})]
      (case status
        200 (dc/parse-json body)
        500 (throw+ (:server-error exceptions))
        (throw+ (:unspecified exceptions))))))

(defn info
  "displays system-wide information
  Usage:
    (info docker)"
  [client]
  (let [{:keys [status body error]} (dc/rpc-get client "/info")]
    (case status
      200 (dc/parse-json body)
      500 (throw+ (:server-error exceptions))
      (throw+ (:unspecified exceptions)))))


;;TODO: doesnt work with --> requires header even header exists, wrong value?
;;Tested with curl:
;;curl --data "{\"username\":\"tauhotest\",\"password\":\"qwerty_test\",\"serveraddress\":\"https://index.docker.io/v1\"}" http://10.0.1.2:4243/auth -i -v -H "X-Docker-Registry-Version:\"1\""
;;

(defn authorize
  "authorizes client's session with docker's index auth service"
  [client username password email]
  (let [{:keys [status body error]
         :as resp} (dc/rpc-post
                      client "/auth"
                      {:body (generate-string
                                {:username username
                                 :password password
                                 :email email
                                 :serveraddress (:index-url client)})})]
    (if (= 200 status)
      true
      (do
        (log/error error " : " body)
        false))))

#_(
   (def docker (make-client "http://10.0.1.2:4243"))
   (version docker)
   (events docker)
   (def since-epoch (int (/ (System/currentTimeMillis) 100)))
   (events docker since-epoch)
   )

;; OLDSHIT ---------------------------------------
;; aka which part is not yet refactored

(comment

(defn rpc-get [url & [params]]
  (let [resp (http/get (str *docker-host* url) {:query-params params})]
    (if (= 200 (:status @resp))
      (parse-string (:body @resp) true))))

(defn rpc-get-stream [url & [params]]
  (let [resp (http/get (str *docker-host* url) {:as :stream :query-params params})]
    (if (= 200 (:status @resp))
      (parse-stream (io/reader (:body @resp)) true))))

(defn rpc-post [url & [params]]
  (let [resp (http/post (str *docker-host* url) {:form-params params})]
    (if (= 200 (:status @resp))
      (parse-string (:body @resp) true))))

(defn rpc-post-json [url params]
  (println (generate-string (into {} (remove (comp nil? val) params))))
  (let [resp (http/post (str *docker-host* url) {:headers {"Content-Type" "application/json"}
                                                 :body (generate-string (into {} (remove (comp nil? val) params)))})]
    (if (or (= 200 (:status @resp)) (= 201 (:status @resp)))
      (parse-string (:body @resp) true)
      (println (:status @resp) (:reason @resp)))))

(defn rpc-post-tar [url file params]
  (let [resp (http/post (str *docker-host* url) {:headers {"Content-Type" "application/tar"}
                                                 :body (slurp file)})]
    (if (or (= 200 (:status @resp)) (= 201 (:status @resp)))
      (:body @resp)
      (println (:status @resp) (:reason @resp)))))

(defn rpc-post-stream [url & [params]]
  (let [resp (http/post (str *docker-host* url) {:as :stream :form-params params})]
    (if (= 200 (:status @resp))
      (parse-stream (io/reader (:body @resp)) true))))

(defn rpc-delete [url & params]
  (let [resp (http/delete (str *docker-host* url) {:query-params params})]
    (if (= 200 (:status @resp))
      (parse-string (:body @resp) true))))

(defn attach [container]
  (let [resp (rpc-post-stream (str "/containers/" container "/attach")
                              {"stdout" 1
                               "stderr" 1
                               "stream" 1})]
    (println resp)))

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

  )
(defn export [container]
  (rpc-get-stream (str "/containers/" container "/export")))

(defn kill [containers]
  (let [urls (map #(str "/containers/" %1 "/kill") containers)
        futures (doall (map rpc-post urls))]
    (doseq [resp futures]
      (println resp))))

) ;; end of comment
