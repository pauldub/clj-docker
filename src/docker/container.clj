(ns docker.container
  (:refer-clojure :exclude [remove])
  (:require [docker.client :as dc]
            [slingshot.slingshot :refer [throw+ try+]]
            [taoensso.timbre :as log]
            [cheshire.core :refer [generate-string]]
            [clojure.java.io :as io]))

(def exceptions
  {400 {:type ::bad_parameter
        :message "Bad parameter - check arguments."}
   404 {:type ::no_container
        :message "Container or baseimage doesnt exists"}
   406 {:type ::not_attached
        :message "Container not running - cant attach."}
   500 {:type ::server_error
        :message "Fatal error in Docker agent."}
   :uf {:type ::unspecified_error
        :message "Unspecified error: timeout."}})


(def response-handler (dc/make-response-handler exceptions))

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
                      (when-not (nil? before) {:before before}))]

    (response-handler
      (dc/rpc-get client
        "/containers/json"
        {:query-params params})
      dc/parse-json)))

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

;;TODO:add validators ~ prismatic's schema?
(defn create-config
  [user-configs]
    (merge default-container-config user-configs))

(defn create
  "creates new container
  Arguments:
    client          - initialized dockers client
    container-name  - a name of container (only alphanum & _)
    user-configs    - map of configurations user wants to change
                      on default-container-config (optional)
  Usage:
    (create client) ;; just use default settings
    (create client {:Hostname \"conta2\", :Memory 1024})
    (create client {} \"container-123a\")"
  ([client] (create client nil {}))
  ([client user-configs] (create client user-configs nil))
  ([client user-configs container-name]
    (let [request-data (create-config user-configs)]
      (response-handler
        (dc/rpc-post client
          "/containers/create"
          (merge
            {:body (generate-string request-data)}
            ;; if requested, add container name into query-params
            (when-not (empty? container-name)
              {:query-params {:name container-name}})))
        dc/parse-json))))

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
  (response-handler
    (dc/rpc-get client
      (str "/containers/" container-id  "/json"))
    dc/parse-json))

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
  (let [params {:ps_args ps_args}]
    (response-handler
      (dc/rpc-get client
        (str "/containers/" container-id "/top")
        {:query-params params})
      dc/parse-json)))

(defn changes
  "shows changes on container id's filesystem
  Arguments:
    client        - a initialized client
    container-id  - the id of the container
  Usage:
    (changes client \"abc123\")"
  [client container-id]
  (response-handler
    (dc/rpc-get client
      (str "/containers/" container-id "/changes"))
    dc/parse-json))

(defn export
  "export the container into local file,
   which name will be docker id
  Arguments:
    client  - an initialized client
    id      - the id of the container
    path    - a fullpath to  the directory
  Returns:
    a fullpath as string
  Usage:
    (export client \"container-1\" \"/usr/shared\")"
  [client id file-path]
  (response-handler
    @(dc/stream-get client
      (str "/containers/" id "/export"))
    (fn [body] (dc/save-stream body (str file-path "/" id)))))

(def default-start-configuration
  {:Binds ["/tmp:/tmp"]
   :LxcConf {:lxc.utcname "docker"}
   :PortBindings {"22/tcp" [{:HostPort 11022}]}
   :PublishAllPorts false
   :Privileged false})

(defn start
  "starts  the container with the id
  Arguments:
    client  - an initialized dockers client
    id      - the id of the container
  Optional arguments:
    config  - a clojure map with host settings,
              check default-container-configs for supported keywords
    :host-config - the container's host configuration
  Usage:
    (start client \"container-1\")
    (start client \"container-2\" {:Memory 1024})
    (start client \"container-3\"
                  {:Memory 1024}
                  :host-config {:SomeValue 101})"
  ([client id] (start client id nil))
  ([client id config & {:keys [host-config]}]
    (let [config_ (merge default-start-configuration config)]
      (response-handler
        (dc/rpc-post client
          (str "/containers/" id "/start")
          (merge
            {:body (generate-string config_)}
            (when-not (nil? host-config)
              {:query-params {:hostConfig host-config}})))
        (fn [body] true)))))

(defn stop
  "stops the container with id
  Arguments:
    client  - an initialized dockers client
    id      - the id of the container
  Optional arguments:
    timeout - number of seconds to wait before killing the container.
  Usage:
    (stop client \"abc123\")
    (stop client \"abc123\" 5)"
  ([client id] (stop client id nil))
  ([client id timeout]
    (response-handler
      (dc/rpc-post client
        (str "/containers/" id "/stop")
        (merge
          {}
          (when-not (nil? timeout)
            {:query-params {:t timeout}})))
      (fn [body] true))))

(defn restart
  "restarts the container with id
  Arguments:
    client  - an initialized dockers client
    id      - the id of the container
  Optional arguments:
    timeout - number of seconds to wait before restarting the container.
  Usage:
    (restart client \"abc123\")
    (restart client \"abc123\" 5)"
  ([client id] (restart client id nil))
  ([client id timeout]
    (response-handler
      (dc/rpc-post client
        (str "/containers/" id "/restart")
        (merge
          {}
          (when-not (nil? timeout)
            {:query-params {:t timeout}})))
      (fn [body] true))))

(defn kill
  "kills the container with id
  Arguments:
    client  - an initialized dockers client
    id      - the id of the container
  Usage:
    (kill client \"abc123\")
    (kill client \"abc123\" 5)"
  [client id]
  (response-handler
    (dc/rpc-post client
      (str "/containers/" id "/kill")
      {})
    (fn [body] true)))

(defn wait
  "Blocks until container stops, then returns the exit code
  Arguments:
    client  - an initialized docker client
    id      - the id of the container
  Usage:
    (wait client \"abc123\")"
  [client id]
  (response-handler
    (dc/rpc-post client
      (str "/containers/" id "/wait")
      {})
    dc/parse-json))

(defn remove
  "remove the container from the filesystem
  Arguments:
    client  - an initialized dockers client
    id      - the id of the container
  Optional arguments:
    :volumes  - remove the volumes associated to container
    :force    - removes container even if it's running
  Usage:
    (remove client \"abc123\")
    (remove client \"abc123\" :force true)
    (remove client \"abc123\" :volumes true)"
  [client id & {:keys [volumes force] :or {volumes false, force false}}]
  (response-handler
    (dc/rpc-delete client
      (str "/containers/" id)
      {:query-params {:v volumes, :force force}})
    (fn [body] true)))

;;TODO: how to handle multiple files and directories
(defn copy-file
  "Copies file from the container into the target-dir on the client side.
  Arguments:
    client     - an initialized client of docker's remote api
    id         - the id of the container
    src-path   - fullpath to the source file
    target-dir - the target directory
  Returns:
   a string of fullpath of the file
  Usage:
    (copy-file client \"container1\" \"/tmp/test.txt\" \"/tmp\")"
  [client id src-path target-dir]
  (let [file-name (-> src-path (clojure.string/split #"\/") last)
        file-fullpath (str target-dir "/" file-name)
        request {:Resource src-path}]
    (response-handler
      @(dc/stream-post client
        (str "/containers/" id "/copy")
        {:body (generate-string request)})
      (fn [body] (dc/save-stream body file-fullpath)))))

(defn attach
  "attach to the container's feeds as stdio,stderr and logs.
  Arguments:
    client  -  an initialized dockers client
    id      - the id of the container
  Optional arguments:
    :logs   - adds logs into stream, default false
    :stream - returns stream (raw data from process PTY + stdin),
              default false
    :stdin  - if stream = true, attach to stdin, default false
    :stdout - if logs=true, returns stdout log, default false
    :stderr - if logs=true, returns stderr log, default false
  Returns:
    BufferedReader object to read streams.
  Usage:
    (attach client \"abc123\")
    (attach client \"abc123\" :stderr true)"
  [client id & {:keys [logs, stream, stdin, stdout, stderr]
                :or {logs false, stream false, stdin false,
                     stdout false, stderr false}}]
  (let [params {:logs logs
                :stream stream
                :stdin stdin
                :stdout stdout
                :stderr stderr}]
    (response-handler
      @(dc/stream-post client
        (str "/containers/" id "/attach")
        {:query-params params})
      (fn [body] (clojure.java.io/reader body)))))

(comment
  ;; workflow to start using attach
  (require '[docker.client :as dc])
  (require '[docker.image :as image])
  (require '[docker.container :as container])


  (def client (dc/make-client "10.0.100.2:4243"))

  ;; pull base image
  (image/create client "lapax/tiny-haproxy")

  ;; create running container with activated TTY
  (def box (container/create client
                             {:Tty true,
                              :Image "lapax/tiny-haproxy"
                              :Cmd ["/bin/sh", "-l"]}))

  (container/inspect client (:Id box))

  ;; attach to container
  (attach client (:Id box) :logs true :stdout true)
  (attach-ws client (:Id box) :logs true :stdout true)
)


