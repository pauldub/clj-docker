(ns docker.image
  (:require [docker.client :as dc]
            [slingshot.slingshot :refer [throw+ try+]]
            [taoensso.timbre :as log]
            [clojure.java.io :as io]))

(def exceptions
  {400 {:type ::bad_parameter
        :message "Invalid parameter - check input data;"}
   404 {:type ::no_image
         :message "Image doesnt exist."}
   409 {:type ::image_conflict
        :message "Conflicts while "}
   500 {:type ::server_error
        :message "Fatal error in Docker agent."}
   :uf {:type ::unspecified_error
        :message "Unspecified error. Probably timeout exception due wrong URl."}})

(def response-handler (dc/make-response-handler exceptions))

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
  (response-handler
    (dc/rpc-get client "/images/json" {:query-params {:all all}})
    dc/parse-json))

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
                :registry registry}]
    (response-handler
      @(dc/stream-post client
          "/images/create"
          (merge
            {:query-params params}
            (when (contains? client :auth-token)
              {:headers {"X-Registry-Auth" (:auth-token client)}})))
      dc/parse-stream)))

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
  (let [params {:force force}]
    (response-handler
      (dc/rpc-delete client
        (str "/images/" image-name)
        {:query-params params})
      (fn [body] true))))

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
  (let [params {:url url, :path path}]
    (response-handler
      @(dc/stream-post client
         (str "/images/" image "/insert")
         {:query-params params})
      dc/parse-stream)))

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
  (response-handler
    (dc/rpc-get client
      (str "/images/" image "/json"))
    dc/parse-json))

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
  (response-handler
    (dc/rpc-get client
      (str "/images/" image "/history"))
    dc/parse-json))

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
  (let [params {:registry registry}]
    (response-handler
      @(dc/stream-post client
        (str "/images/" image "/push")
        (merge {}
          ;; add registry only if it was given
          (when-not (nil? registry)
            {:query-params params})
          ;; add authorization key iff it exists
          (when (contains? client :auth-token)
            {:headers {"X-Registry-Auth" (:auth-token client)}})))
      dc/parse-stream)))

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
                   :or {repo "", force false}}]
  (let [params {:repo repo, :force force}]
    (response-handler
      (dc/rpc-get client
        (str "/images/" image "/tag")
        {:query-params params})
      (fn [body] true))))

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
  (let [params {:term search-term}]
    (response-handler
      (dc/rpc-get client
        (str "/images/search")
        {:query-params params})
      dc/parse-json)))
