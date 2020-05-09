(ns cloud-formation
  (:require [clojure.java.io :as io])
  (:import (software.amazon.awssdk.auth.credentials ProfileCredentialsProvider)
           (software.amazon.awssdk.regions Region)
           (software.amazon.awssdk.services.cloudformation CloudFormationClient)
           (software.amazon.awssdk.services.cloudformation.model CreateStackRequest DeleteStackRequest DescribeStacksRequest UpdateStackRequest)))

;; template
(defn sample-template-json []
  (slurp (io/resource "cf_template.json")))

;; component
(def credentials (-> (ProfileCredentialsProvider/builder)
                     (.profileName "default")
                     .build))

(def cf-client (-> (CloudFormationClient/builder)
                   (.credentialsProvider credentials)
                   (.region Region/US_EAST_2)
                   .build))

(def stack-name "example-stack")

;; create stack
(do
  (def example-stack (-> (CreateStackRequest/builder)
                         (.stackName stack-name)
                         (.templateBody (sample-template-json))
                         .build))
  (.createStack cf-client example-stack))

;; update stack
(do
  (def update-stack (-> (UpdateStackRequest/builder)
                        (.stackName stack-name)
                        (.templateBody (sample-template-json))
                        .build))
  (.updateStack cf-client update-stack))

;; outputs
(do
  (def stacks-req (-> (DescribeStacksRequest/builder)
                      (.stackName stack-name)
                      .build))
  (->> stacks-req
       (.describeStacks cf-client)
       .stacks
       first
       .outputs
       (map (fn [output] {(.outputKey output) (.outputValue output)}))))


;; clean up
(do
  (def delete-stack-req (-> (DeleteStackRequest/builder)
                            (.stackName stack-name)
                            .build))
  (.deleteStack cf-client delete-stack-req))
