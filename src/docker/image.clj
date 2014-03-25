(ns docker.image
  (:require [docker.client :as dc]
            [slingshot.slingshot :refer [throw+ try+]]
            [taoensso.timbre :as log]
            [clojure.java.io :as io]))

(def exceptions {:server-error {:type ::server_error
                                :status 500
                                :message "Fatal error in Docker agent."}
                 :unspecified  {:type ::unspecified_error
                                :status 418
                                :message "Unspecified error. Probably timeout exception due wrong URl."}
                 :no-image {:type ::no_image
                            :status 404
                            :message "Image doesnt exist."}
                 :image-conflict {:type ::image_conflict
                                  :status 409
                                  :message "Conflicts while "}})

(defn show-all
  "Lists all images on Docker host.
  Arguments:
    client  - the initialized client for docker API
  Optional arguments:
    :all    - limit for size of response
  Usage:
    (show-all client)
    (show-all client :all 10)"
  [client & {:keys [all] :or {all 0}}]
  (let [params {:all all}
        {:keys [status body error]} (dc/rpc-get client "/images/json"
                                                {:query-params params})]
    (case status
      200 (dc/parse-json body)
      500 (throw+ (:server-error exceptions))
      (throw+ (:unspecified exceptions)))))


;;TODO: finish authorization
;;TODO: add support for from-src
(defn create
  "Creates an new image, either by pull it from the registry or importing it from file.
  Arguments:
    client - initialized docker client
    image - name of docker image
  Optional arguments:
    :from-src - import docker image from file
    :repo - name of repository
    :tag - image's tag
    :registry - to specify url
  Usage:
    (create client \"registry\")
    (create client \"registry\" :tag \"latest\")
  "
  [client image & {:keys [from-src repo tag registry]}]
  (let [params {:fromImage image
                :repo repo
                :tag tag
                :registry registry}
        {:keys [status body]
         :as resp} @(dc/stream-post client "/images/create"
                                   {:query-params params})]
    (case status
      200 (dc/parse-stream body)
      500 (throw+ (:server-error exceptions))
      (throw+ (merge (:unspecified exceptions)
                     {:response resp})))))

(defn delete
  "Remove image from filesystem
  Arguments:
    client - initialized docker client
    image-name - name of image
  Optional arguments:
    :force - ignore possible errors while removing it, default false.
  Usage:
    (delete client \"registry\")"
  [client image-name & {:keys [force] :or {force false}}]
  (let [{:keys [status body]
         :as resp} (dc/rpc-delete client (str "/images/" image-name)
                                         {:query-params {:force force}})]
    (case status
      200 true
      404 (throw+ (:no-image exceptions))
      409 (throw+ (:image-conflict exceptions))
      500 (throw+ (:server-error exceptions))
      (throw+ (merge (:unspecified exceptions) resp)))))


(defn insert-file
  "Inserts a file from the url in the image on the path.
  Arguments:
    client  - initialized client
    image   - a name of docker image
    url     - an url of the file
    path    - an path to the image
  Response:
    a lazy-seq of parsed json-stream.
  Usage:
    (insert-file client \"http://s3.in/folder/file.exe\" \"/usr/shared\")"
  [client image url path]
  (let [params {:url url, :path path}
        {:keys [status body]
         :as resp} @(dc/stream-post client
                                    (str "/images/" image "/insert")
                                    {:query-params params})]
    (case status
      200 (dc/parse-stream body)
      500 (throw+ (:server-error exceptions))
      (throw+ (merge
                (:unspecified exceptions)
                (:response resp))))))

(defn inspect
  "Returns low-level information on the image name
  Arguments:
    client  - an initialized docker's client
    image   - the name of image
  Response:
    plain Clojure map
  Usage:
    (inspect client \"lapax/tiny-haproxy\")"
  [client image]
  (let [{:keys [status body]} (dc/rpc-get client
                                          (str "/images/" image "/json"))]
    (case status
      200 (dc/parse-json body)
      404 (throw+ (:no-image exceptions))
      500 (throw+ (:server-error exceptions))
      (throw+ (:unspecified exceptions)))))

(defn history
  "Returns the history of the image
  Arguments:
    client  - the initialized client of docker
    image   - the name of the image
  Response:
    a list of maps of history item
  Usage:
    (history client \"lapax/tiny-haproxy\")"
  [client image]
  (let [{:keys [status body]} (dc/rpc-get client
                                          (str "/images/" image "/history" ))]
    (case status
      200 (dc/parse-json body)
      404 (throw+ (:no-image exceptions))
      500 (throw+ (:server-error exceptions))
      (throw+ (:unspecified exceptions)))))

(defn push
  "Push the image on the registry
  Arguments:
    client  -  the initialized client for docker api
    image   -  the name of the image
  Optional arguments:
    :registry - the url of your registry
  Response:
    a lazy-seq of a parsed json stream
  Usage:
    (push client \"lapax/tiny-haproxy\")
    (push client \"lapax/tiny-haproxy\" :registry \"http://url\")"
  [client image & {:keys [registry]}]
  (let [params {:registry registry}
        {:keys [status body]} @(dc/stream-post client
                                             (str "/images/" image "/push")
                                             (when-not (nil? registry)
                                               {:query-params params}))]
    (case status
      200 (dc/parse-stream body)
      404 (throw+ (:no-image exceptions))
      500 (throw+ (:server-error exceptions))
      (throw+ (:unspecified exceptions)))))

(defn tag
  "Tags the image into a repository
  Arguments:
    client  - the initialized client of docker
    image   - the name of the image
  Optional arguments:
    :repo   - the repository to tag in
    :force  - force the changes and ignore errors
  Returns:
    true if successful, otherwise raises exceptions
  Usage:
    (tag client \"base\" :repo \"default\")
    (tag client \"base\" :repo \"default\" :force true)"
  [client image & {:keys [repo force]
                   :or {repo "default", force false}}]
  (let [params {:repo repo, :force force}
        {:keys [status body]} (dc/rpc-get client
                                          (str "/images/" image "/tag")
                                          params)]
    (case status
      201 true
      400 (throw+ (:bad-parameter exceptions))
      404 (throw+ (:no-image exceptions))
      409 (throw+ (:image-conflict exceptions))
      500 (throw+ (:server-error exceptions))
      (throw+ (:unspecified exceptions)))))

(defn search
  "Searches for an image in the docker index.
  Arguments:
    client      -  the initialized client
    search-term -  an search-term an image has to include
  Returns:
    a list with search results
  Usage:
    (search client \"sshd\")"
  [client search-term]
  (let [params {:term search-term}
        {:keys [status body]} (dc/rpc-get client
                                          (str "/images/search")
                                          {:query-params params})]
    (case status
      200 (dc/parse-json body)
      500 (throw+ (:server-error))
      (throw+ (:unspecified exceptions)))))

