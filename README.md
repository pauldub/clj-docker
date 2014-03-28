# Docker

[![Build Status](https://travis-ci.org/tauho/clj-docker.svg?branch=master)](https://travis-ci.org/tauho/clj-docker)
[![Dependency Status](https://www.versioneye.com/user/projects/5335a8fc7bae4bff0f000858/badge.png)](https://www.versioneye.com/user/projects/5335a8fc7bae4bff0f000858)

**NB!** work in progress. Current status it has 95% methods; still needs refactoring and better support for streamed data and with attaching outputs;


A low-level Clojure client of Docker's remote API. Low-level means here:
 
 * that a client doesnt take a care of threading, you should use core.async or highlevel functions for that;, 
 * returns parsed value if request were successful, otherwise it wires slingshot exceptions
 * it's not SDK and doesnt provide sugarcoated DSL.



## Quickstart

```
(require '[docker.core :as docker])

;; get an information of docker agent 
(def client (make-client "http://10.0.1.2:4243"))
(docker/version client)
(docker/info client)

;; manage docker images
(require '[docker.image :as image])
(image/show-all client)
(image/search client "tiny-haproxy")
(image/create client "lapax/tiny-haproxy")
(image/inspect client "lapax/tiny-haproxy")

;; manage docker containers
(require '[docker.container :as container])
(container/show-all client)
(println container/default-container-config);; which keywords are supported
(container/create client "docker1" {:Hostname "test-docker", :Memory 0})
(container/inspect client "container-id")
(container/start client "container-id")
(container/top client "container-id") 
(container/stop client "container-id")

```


## TODO

- remove TODOs in code
- Write additional tests for v0.9
- Write doc.
- doc with func references
- doc for contributors
- doc how to set up test-machines and over nginx