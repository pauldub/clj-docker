(ns docker.image
  (:require [docker.client :as dc]
            [slingshot.slingshot :refer [throw+ try+]]
            [taoensso.timbre :refer [debug warn error]]
            [cheshire.core :refer [parse-string parse-stream]]))

(def exceptions {:server-error {:type ::server_error
                                :status 500
                                :message "Fatal error in Docker agent."}
                 :unspecified  {:type ::unspecified_error
                                :status 418
                                :message "Unspecified error. Probably timeout exception due wrong URl."}})


(defn show-all [client & {:keys [all] :or {all 0}}]
  "Lists all images on Docker host.
  Usage:
    (show-all client)"
  (let [params {:all all}
        {:keys [status body error]} (dc/rpc-get client "/version" {:query-params params})]
    (case status
      200 (dc/parse-json client body)
      500 (throw+ (:server-error exceptions))
      (throw+ (:unspecified exceptions)))))


;;TODO: add support for from-src
(defn create [client image
              & {:keys [from-src repo tag registry]
                 :or {}}]
  "Creates an new image, either by pull it from the registry or importing it.
  Usage:
    (create client \"ubuntu:precise\")
  "
  (let [params {:fromImage image
                :repo repo
                :tag tag
                :registry registry}
        {:keys [status body]} (dc/rpc-post client "/images/create"
                                           {:query-params params
                                            :headers {:TODO "finish authorization"}})]
    (cond status
      200 (dc/parse-json client body)
      500 (throw+ (:server-error exceptions))
      (throw+ (:unspecified exceptions)))))


