(ns docker.image
  (:require [docker.client :as dc]
            [slingshot.slingshot :refer [throw+ try+]]
            [taoensso.timbre :refer [debug warn error] :as log]
            [cheshire.core :refer [parse-string parse-stream]]
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
                                  :message "Conflicts while removing images on the filesystem."}})


(defn show-all [client & {:keys [all] :or {all 0}}]
  "Lists all images on Docker host.
  Usage:
    (show-all client)"
  (let [params {:all all}
        {:keys [status body error]} (dc/rpc-get client "/images/json"
                                                {:query-params params})]
    (case status
      200 (dc/parse-json client body)
      500 (throw+ (:server-error exceptions))
      (throw+ (:unspecified exceptions)))))


;;TODO: finish authorization
;;TODO: add support for from-src
(defn create [client image
              & {:keys [from-src repo tag registry]}]
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
  (let [params {:fromImage image
                :repo repo
                :tag tag
                :registry registry}
        stream (dc/rpc-post client "/images/create"
                                 {:query-params params, :as :stream})
        status (:status @stream)]
    (cond status
      200 (parse-stream (io/reader (:body @stream)) true)
      500 (throw+ (:server-error exceptions))
      (throw+ (:unspecified exceptions)))))

(defn delete [client image-name & {:keys [force] :or {force false}}]
  "Remove image from filesystem
  Arguments:
    client - initialized docker client
    image-name - name of image
  Optional arguments:
    :force - ignore possible errors while removing it, default false.
  Usage:
    (delete client \"registry\")"
  (let [{:keys [status body]} (dc/rpc-delete client (str "/images/" image-name)
                                             {:query-params {:force force}})]
    (cond status
      200 (dc/parse-json client body)
      404 (throw+ (:no-image exceptions))
      409 (throw+ (:image-conflict exceptions))
      500 (throw+ (:server-error exceptions))
      (throw+ (:unspecified exceptions)))))


