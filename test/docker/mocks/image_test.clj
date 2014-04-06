(ns docker.mocks.image-test
  (:require [midje.sweet :refer :all]
            [midje.config :as config]
            [org.httpkit.client :as http]
            [org.httpkit.fake :refer [with-fake-http]]
            [clojure.java.io :as io]
            [cheshire.core :refer [generate-string generate-stream]]
            [docker.client :refer [make-client]]
            [docker.image :refer :all]))

(def default-host "http://10.0.1.2:4243")

(facts "show-all"
  (let [docker (make-client default-host)
        image1 {:Id "x1"}
        image2 {:Id "x2"}]
    (fact "shows all images on docker's host."
      (with-fake-http [#"/images/json$" (generate-string [image1 image2])]
        (let [images (show-all docker)]
          (count images) => 2
          (-> images first :Id) => "x1"
          (-> images second :Id) => "x2")))
    (fact "shows only n latest items"
      (with-fake-http [#"/images/json" (generate-string [image1])]
        (let [images (show-all docker :all 1)]
          (count images) => 1
          (-> images first :Id) => "x1")))))

(facts "creates new image by downloading it from registry"
  (let [docker (make-client default-host)
        response-file "/tmp/docker-create-image"
        resp1 (generate-stream [{:status "Pulling"}
                                {:status "Pulling"}
                                {:status "Done"}]
                               (io/writer response-file))]
    (fact "using plain settings imports correct data"
      (with-fake-http [#"/images/create$" {:status 200
                                           :body (.getBytes (slurp response-file) "utf8")}]
        (let [msgs (create docker "test/image")]
          (first msgs)  => {:status "Pulling"}
          (second msgs) => {:status "Pulling"}
          (nth msgs 2)  => {:status "Done"})))

    (fact "raises correct exception when server fails"
      (with-fake-http [#"/images/create$" {:status 500
                                           :body "server down"}]
        (create docker "test/kaputt") => (throws Exception)))))

(facts "creates a new image by uploading it"
  (let [client (make-client default-host)
        response-file "/tmp/docker-create-image-response"
        resp1 (generate-stream [{:status "Uploading"}
                                {:status "Uploading", :progress "1/100"}
                                {:status "Done"}]
                               (io/writer response-file))
        test-file "/tmp/docker-create-image"]
    ;; add some content into test file
    (spit test-file "kikka kukka")
    (fact "gets correct response after successful upload"
      (with-fake-http [#"/images/create" {:status 200
                                          :body (.getBytes (slurp response-file) "utf8")}]
        (let [msgs (create-from-src client test-file)]
          (first msgs)  => {:status "Uploading"}
          (second msgs) => {:status "Uploading" :progress "1/100"}
          (nth msgs 2)  => {:status "Done"})))
    (fact "raises exception if server returns failure code"
      (with-fake-http [#"/images/create" {:status 500}]
        (create-from-src client test-file) => (throws Exception)))))

(facts "delete an image"
  (let [docker (make-client default-host)]
    (fact "deletes successfully existing image"
      (with-fake-http [#"/images/the_image" {:status 200 :body "OK"}]
        (delete docker "the_image") => true ))
    (fact "deletes existing image when user is using :force"
      (with-fake-http [#"/images/the_image" {:status 200 :body "OK"}]
        (delete docker "the_image" :force true) => true))
    (fact "raises exception when image doesnt exists"
      (with-fake-http [#"/images/the_image" {:status 404}]
        (delete docker "the_image") => (throws Exception)))
    (fact "raises exception when status code is smt else than 200"
      (with-fake-http [#"/images/the_image" {:status 418}]
        (delete docker "the_image") => (throws Exception)))))

(facts "insert-file"
  (let [docker (make-client default-host)
        test-file "/tmp/docker-image-insert-file"
        content (generate-stream [{:status "Inserting..."}
                                  {:status "Inserting", :progress "1"}]
                                 (io/writer test-file))]
    (fact "adds file from url into image"
      (with-fake-http [#"/images/test/insert" {:status 200
                                               :body (.getBytes (slurp test-file) "utf8")}]
        (let [msgs (insert-file docker "test" "file://url" "/image/folder")]
          (first msgs) => {:status "Inserting..."}
          (second msgs) => {:status "Inserting", :progress "1"})))
    (fact "raises exception when server fails"
      (with-fake-http [#"/images/test/insert" {:status 500}]
        (insert-file docker "test"
                     "file://not/exists"
                     "/image/folder") => (throws Exception)))))

(facts "inspect an image"
  (let [docker (make-client default-host)
        msg {:id "1234"
             :container "abba"}]
    (fact "returns a information of the image"
      (with-fake-http [#"/images/test/json" {:status 200
                                             :body (generate-string msg)}]
        (inspect docker "test") => {:id "1234", :container "abba"}))
    (fact "raises exceptions when image doesnt exists"
      (with-fake-http [#"/images/test/json" {:status 404}]
        (inspect docker "test") => (throws Exception)))))

(facts "get a history of the image"
  (let [docker (make-client default-host)
        msg [{:Id "123", :Created 123}]]
    (fact "returns a correct response"
      (with-fake-http [#"/images/test/history" {:status 200
                                                :body (generate-string msg)}]
      (history docker "test") => [{:Id "123", :Created 123}]))
    (fact "raises exception when image doesnt exists"
      (with-fake-http [#"/images/test/history" {:status 404}]
        (history docker "test") => (throws Exception)))))

(facts "pushes the image on the registry"
  (let [docker (make-client default-host)
        test-file "/tmp/docker-image-push"
        data (generate-stream [{:status "Pushing..."}
                               {:status "Pushing", :progress "1/?"}]
                              (io/writer test-file))]

    (fact "returns proper response stream"
      (with-fake-http [#"/images/test/push" {:status 200
                                             :body (.getBytes (slurp test-file) "utf8")}]
        (let [resp (push docker "test")]
          (first resp) => {:status "Pushing..."}
          (second resp) => {:status "Pushing", :progress "1/?"})))

    (fact "returns proper response when using authorized client"
      (let [authorized-client (assoc docker :auth-token "base64-token")]
        (with-fake-http [#"/images/test/push" {:status 200
                                               :body (.getBytes (slurp test-file) "utf8")}]
          (let [resp (push authorized-client "test")]
            (first resp) => {:status "Pushing..."}
            (second resp) => {:status "Pushing", :progress "1/?"}))))
    (fact "raises excpetions when image doesnt exists"
      (with-fake-http [#"/images/test/push" {:status 404}]
        (push docker "test") => (throws Exception)))))

(facts "tags the image"
  (let [docker (make-client default-host)]
    (fact "adding new tag is successful"
      (with-fake-http [#"/images/test/tag" {:status 201}]
        (tag docker "test") => true
        (tag docker "test" :repo "repo_name") => true
        (tag docker "test" :force true => true)))
    (fact "raises exception when the image doesnt exists"
      (with-fake-http [#"/images/test/tag" {:status 404}]
        (tag docker "test") => (throws Exception)))))

(facts "searches images from index"
  (let [docker (make-client default-host)
        data [{:name "img1"}, {:name "img2"}]]
    (fact "returns right content if it had success"
      (with-fake-http [#"/images/search" {:status 200
                                          :body (generate-string data)}]
        (let [resp (search docker "img")]
          (first resp) => {:name "img1"}
          (second resp) => {:name "img2"})))
    (fact "raises exceptions if server responds with 500"
      (with-fake-http [#"/images/search" {:status 500}]
        (search docker "img") => (throws Exception)))))

