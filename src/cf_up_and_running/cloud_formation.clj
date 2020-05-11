(ns cf-up-and-running.cloud-formation
  (:require [cf-up-and-running.ec2 :as ec2]
            [clojure.java.io :as io]
            [clojure.string :as string])
  (:import (software.amazon.awssdk.auth.credentials ProfileCredentialsProvider)
           (software.amazon.awssdk.regions Region)
           (software.amazon.awssdk.services.cloudformation CloudFormationClient)
           (software.amazon.awssdk.services.cloudformation.model CreateStackRequest DeleteStackRequest
                                                                 DescribeStacksRequest Parameter UpdateStackRequest)
           (software.amazon.awssdk.services.ec2.model DescribeSubnetsRequest DescribeVpcsRequest Filter)))

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

(def default-vpc (-> (.describeVpcs ec2/ec2-client (-> (DescribeVpcsRequest/builder)
                                                       (.filters [(-> (Filter/builder)
                                                                      (.name "isDefault")
                                                                      (.values ["true"])
                                                                      .build)])
                                                       .build))
                       .vpcs
                       first))

(def vpc-id (.vpcId default-vpc))

(def subnets (-> (.describeSubnets ec2/ec2-client (-> (DescribeSubnetsRequest/builder)
                                                      (.filters [(-> (Filter/builder)
                                                                     (.name "vpc-id")
                                                                     (.values [vpc-id])
                                                                     .build)])
                                                      .build))
                 .subnets))

(def parameters [(-> (Parameter/builder)
                     (.parameterKey "VpcId")
                     (.parameterValue vpc-id)
                     .build)
                 (-> (Parameter/builder)
                     (.parameterKey "Subnets")
                     (.parameterValue (string/join "," (map #(.subnetId %) subnets)))
                     .build)])

;; create stack
(do
  (def example-stack (-> (CreateStackRequest/builder)
                         (.stackName stack-name)
                         (.templateBody (sample-template-json))
                         (.parameters parameters)
                         .build))
  (.createStack cf-client example-stack))

;; update stack
(do
  (def update-stack (-> (UpdateStackRequest/builder)
                        (.stackName stack-name)
                        (.templateBody (sample-template-json))
                        (.parameters parameters)
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
