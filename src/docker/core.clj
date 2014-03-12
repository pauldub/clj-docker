(ns docker.core
  (:require [org.httpkit.client :as http]
            [clojure.java.io :as io]
            [cheshire.core :refer :all]
            [slingshot.slingshot :refer [throw+ try+]]
            [taoensso.timbre :refer [debug warn error]])
  (:import  (org.apache.commons.compress.archivers.tar TarArchiveInputStream
                                                       TarArchiveOutputStream
                                                       TarArchiveEntry)))
(def ^:dynamic *docker-host* "http://127.0.0.1:4243")

(defn set-docker-host!
  [^java.lang.String host]
  (alter-var-root (var *docker-host*) (fn [_] host)))


(defn make-client
  ([url] (make-client url nil))
  ([url client-opts]
    (let [url_ (if (nil? (seq url))
                 *docker-host*
                 url)
          default-client-opts {:user-agent "clj-docker (httpkit 2.1.17)"
                               :keep-alive 30000
                               :timeout 1000}]
      {:url url_
       :client-opts (merge default-client-opts client-opts)})))

(defn do-request 
  [client method path
   & {:keys [query-params form-params as]
     :or [query-params {}
          form-params {}
          as :text
          callback #(%1)]}]
  (let [user-request {:url (str (:url client) path)
                      :method method
                      :query-params query-params
                      :form-params form-params}]
    @(http/request 
      (merge (:client-opts client) user-request))))

(defn version [client]
  (debug "reading docker version from " (:url client))
  (let [{:keys [status body error]} (do-request client :get "/version")]
    (case status
      200 (parse-string body true)
      500 (throw+ {:type ::server_error
                   :status status
                   :error error
                   :message "Server didnt respond."})
      (throw+ {:type ::unspecified_error
               :status status
               :message "Unspecified error. Probably got timeout exception."
               :error error}))))

#_(
   (def docker (make-client "http://10.0.1.2:4243"))
   (version docker)
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

(defn commit [{:keys [container repository tag message author conf]}]
  "TODO")

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

(defn info []
  (rpc-get "/info"))

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

(defn version []
  (rpc-get "/version"))

(defn wait [] "TODO")

) ;; end of comment
