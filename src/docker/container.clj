(ns docker.container
  (:require [docker.client :as dc]
            [slingshot.slingshot :refer [throw+ try+]]
            [taoensso.timbre :as log]
            [cheshire.core :refer [generate-string]]
            [clojure.java.io :as io]))

;;TODO: experiment response-handlers as multimethods not select-case
;; defmultu container-response-handler [status body error] ...
;;

(def exceptions {:server-error {:type ::server_error
                                :status 500
                                :message "Fatal error in Docker agent."}
                 :unspecified {:type ::unspecified_error
                               :status 418
                               :message "Unspecified error: timeout or bug in source."}
                 :bad-parameter {:type ::bad_parameter
                                 :status 400
                                 :message "Bad parameter - check arguments."}
                 :no-container {:type ::no_container
                                :status 404
                                :message "Container doesnt exists"}
                 :not-attached {:type ::not_attached
                                :status 406
                                :message "Container not running - cant attache."}})


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

(def default-container-config
  {:Hostname ""
   :User ""
   :Memory 0
   :MemorySwap 0
   :AttachStdin false
   :AttachStdout true
   :AttachSterr true
   :PortSpecs nil
   :Tty false
   :OpenStdin false
   :StdinOnce false
   :Env nil
   :Cmd ["date"]
   :Dns nil
   :Image "base"
   :Volumes {"/tmp" {}}
   :VolumesFrom ""
   :WorkingDir ""
   :ExposedPorts {"22/tcp" {}}})

;;TODO:add validators
(defn create-config
  [user-configs]
    (merge default-container-config user-configs))


(defn create
  "creates new container
  Arguments:
    client          - initialized dockers client
    container-name  - a name of container (only alphanum & _)
    user-configs    - map of configurations user wants to change on default-container-config (optional)
  Usage:
    (create client \"conta\")
    (create client \"conta2\" {:Hostname \"conta2\", :Memory 1024})"
  ([client container-name] (create client container-name {}))
  ([client container-name user-configs]
    (let [request-data (create-config user-configs)
          {:keys [status body]} (dc/rpc-post client "/containers/create"
                                             {:query-params {:name container-name}
                                              :body (generate-string request-data)})]
      (case status
        201 (dc/parse-json body)
        404 (throw+ (:no-container exceptions))
        406 (throw+ (:not-attached exceptions))
        500 (throw+ (:server-error exceptions))
        (throw+ (:unspecified exceptions))))))

(defn inspect
  "returns low-level information on the container id
  Arguments:
    client        - the initialized docker's client
    container-id  - the id of the container
  Returns:
    a clojure map
  Usage:
    (inspect client \"aabcdef1234\")"

  [client container-id]
  (let [{:keys [status body]} (dc/rpc-get client
                                          (str "/containers/" container-id  "/json"))]
    (case status
      200 (dc/parse-json body)
      404 (throw+ (:no-container exceptions))
      500 (throw+ (:server-error exceptions))
      (throw+ (:unspecified exceptions)))))

(defn top
  "lists processes running inside the container id.
  Arguments:
    client        - a initialized docker's client
    container-id  - the id of the container
  Optional arguments:
    :ps_args - ps arguments to use (e.g 'aux')
  Usage:
    (top client \"abse123\")"
  [client container-id & {:keys [ps_args]}]
  (let [{:keys [status body]} (dc/rpc-get client
                                          (str "/containers/" container-id "/top")
                                          {:query-params {:ps_args ps_args}})]
    (case status
      200 (dc/parse-json body)
      404 (throw+ (:no-container exceptions))
      500 (throw+ (:server-error exceptions))
      (throw+ (:unspecified exceptions)))))

(defn changes
  "shows changes on container id's filesystem
  Arguments:
    client        - a initialized client
    container-id  - the id of the container
  Usage:
    (changes client \"abc123\")"
  [client container-id]
  (let [{:keys [status body]} (dc/rpc-get client
                                          (str "/containers/" container-id "/changes"))]
    (case status
      200 (dc/parse-json body)
      404 (throw+ (:no-container exceptions))
      500 (throw+ (:server-error exceptions))
      (throw+ (:unspecified exceptions)))))

;;TODO: continue here
(defn export
  ""
  [client])


