(ns docker.container
  (:require [docker.client :as dc]
            [slingshot.slingshot :refer [throw+ try+]]
            [taoensso.timbre :as log]
            [clojure.java.io :as io]))

(def exceptions {:server-error {:type ::server_error
                                :status 500
                                :message "Fatal error in Docker agent."}
                 :unspecified {:type ::unspecified_error
                               :status 418
                               :message "Unspecified error: timeout or bug in source."}
                 :bad-parameter {:type ::bad_parameter
                                 :status 400
                                 :message "Bad parameter - check arguments."}})


(defn show-all
  "lists containers on the host
  Arguments:
    client - initialized client
  Optional arguments:
    :all    - show all the containers, default false
    :limit  - show the n-last created containers, (includes non-running)
    :since  - show only the containers created since <id>, (includes non-running)
    :before - show only the containers created before <id>
    :size   - show the containers size
  Returns:
    a list of map
  Usage:
    (show-all client)
    (show-all client :all true)
    (show-all client :since \"abde1323adsd\" :size false)"
  [client & {:keys [all limit since before size]
             :or {all false, size true}}]
  (let [params (merge {:all all, :size size}
                      (when-not (nil? limit) {:limit limit})
                      (when-not (nil? since) {:since since})
                      (when-not (nil? before) {:before before}))
        {:keys [status body]} (dc/rpc-get client "/containers/json"
                                         {:query-params params})]
    (case status
      200 (dc/parse-json body)
      400 (throw+ (:bad-parameter exceptions))
      (throw+ (:unspecified exceptions)))))
