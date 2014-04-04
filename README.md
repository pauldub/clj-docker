# Docker

[![Build Status](https://travis-ci.org/tauho/clj-docker.svg?branch=master)](https://travis-ci.org/tauho/clj-docker)
[![Dependency Status](https://www.versioneye.com/user/projects/5335a8fc7bae4bff0f000858/badge.png)](https://www.versioneye.com/user/projects/5335a8fc7bae4bff0f000858)


An API client for [Docker Remote api](http://docs.docker.io/en/latest/reference/api/docker_remote_api/) v.1.10 written in Clojure.  You can find more comprehensive api reference on this url [http://tauho.github.io/clj-docker](http://tauho.github.io/clj-docker) .


## Install

add additional line into project dependencies in your `project.clj` : [docker "0.2.0"] and run a command `lein deps`

## Quickstart

```
(require '[docker.core :as docker])

;; get an information of docker agent 
(def client (make-client "10.0.1.2:4243"))
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
(container/create client {:Hostname "test-docker", :Memory "1g"})
(container/inspect client "container-id")
(container/start client "container-id")
(container/top client "container-id") 
(container/stop client "container-id")

```


## Testing

Project has 2 kinds of tests : 

* docker.mocks.* - tests with stubbed responses. It requires [http-kit-fake ">0.2.2"] which have a workaround to stub webrequests for `httpkit.client/request` function. run these specs by command `$> lein with-profile dev midje docker.mocks.*`  

* docker.v0x90   - will include tests running against real docker v0.9, it requires that you've installed vagrant and ansible. 

## License

It's Released under the MIT license. See [LICENSE](https://github.com/pauldub/clj-docker/blob/master/LICENSE) for the full license.

	
## TODO

- Write additional tests for docker v0.9
- Write docs for human as [ClojureWerkz](http://clojurewerkz.org/) has.
- Test on battlefield.
- add [stuartsierra/component](https://github.com/stuartsierra/component) pattern for containers(?)
