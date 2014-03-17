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

(defn version [client]
  "Shows the docker version used on host.
  Usage:
    (def docker (make-client))
    (version docker)"
  (debug "reading docker version from " (:host client))
  (let [{:keys [status body error]} (dc/rpc-get client "/version")]
    (case status
      200 (dc/parse-json client body)
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
        200 (dc/parse-json client body)
        500 (throw+ (:server-error exceptions))
        (throw+ (:unspecified exceptions))))))

(defn info [client]
  "displays system-wide information
  Usage:
    (info docker)"
  (let [{:keys [status body error]} (dc/rpc-get client "/info")]
    (case status
      200 (dc/parse-json client body)
      500 (throw+ (:server-error exceptions))
      (throw+ (:unspecified exceptions)))))


;;TODO: doesnt work with < v.0.9 --> fix?
;;Tested with curl:
;;curl --data "{\"username\":\"tauhotest\",\"password\":\"qwerty_test\",\"serveraddress\":\"https://index.docker.io/v1\"}" http://10.0.1.2:4243/auth -i -v -H "X-Docker-Registry-Version:\"1\""
;;

(defn authorize [client username password email]
  (let [{:keys [status body error]
         :as resp} (dc/rpc-post
                      client "/auth"
                      {:body (generate-string
                                {:username username
                                 :password password
                                 :email email
                                 :serveraddress (:index-url client)})})]
    (cond status
      200 true
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

(defn containers []
  (rpc-get "/containers/json"))

(defn create-container [{:keys [image command hostname user detach stdin-open tty mem-limit
                                ports environment dns volumes volumes-from]
                         :or {:hostname nil :user nil :detach false :stdin-open false
                              :tty false :mem-limit 0 :ports [] :environment []
                              :dns [] :volumes nil :volumes-from nil}}]
  (let [container {:Hostname hostname
                   :PortSpecs ports
                   :User user
                   :Tty tty
                   :OpenStdin stdin-open
                   :Memory mem-limit
                   :AttachStdin false
                   :AttachStdout false
                   :AttachStderr false                  
                   :Env [environment]
                   :Cmd command
                   :Dns [dns]
                   :Image image
                   :Volumes volumes
                   :VolumesFrom volumes-from}]
    (rpc-post-json "/containers/create" container)))

(defn diff [container]
  (rpc-get (str "/containers/" container "/changes")))

(defn export [container]
  (rpc-get-stream (str "/containers/" container "/export")))

(defn history [image]
  (rpc-get (str "/images/" image "/history")))

(defn images [& {:keys [name quiet viz]
                 :or {:name nil :quiet false :viz false}}]
  (if (true? viz)
    (rpc-get "/images/viz")
    (rpc-get "/images/json" {:filter name :only_ids 0 :all 1})))


(defn import-image [{:keys [src repository tag]
                     :or {:repository nil :tag nil}
                     :as params}]
  (rpc-post "/images/create" {:fromSrc src :repo repository :tag tag}))

(defn insert [image url path]
  (rpc-post (str "/images/" image "/insert") {:url url :path path}))

(defn inspect-container [container]
  (rpc-get (str "/containers/" container "/json")))

(defn inspect-image [image]
  (rpc-get (str "/images/" image "/json")))

(defn kill [containers]
  (let [urls (map #(str "/containers/" %1 "/kill") containers)
        futures (doall (map rpc-post urls))]
    (doseq [resp futures]
      (println resp))))

(defn login [{:keys [username password email]
              :or {:password nil :email nil}
              :as params}]
  (rpc-post "/auth" params))

(defn remove-container [container]
  (rpc-delete (str "/containers/" container)))

(defn remove-image [image]
  (rpc-delete (str "/images/" image)))

(defn search [term]
  (rpc-get "/images/search" {:term term}))

(defn start [container & ports]
  (rpc-post (str "/containers/" container "/start") {:Binds ports}))

(defn stop [container]
  (rpc-post (str "/containers/" container "/stop")))

(defn tag [] "TODO")
(defn wait [] "TODO")

) ;; end of comment
