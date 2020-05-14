(ns cf-up-and-running.components.cloud-formation
  (:require [cf-up-and-running.protocols.credentials :as protocols.credentials]
            [cf-up-and-running.protocols.cloud-formation :as protocols.cloud-formation]
            [cf-up-and-running.protocols.network :as protocols.network]
            [clojure.string :as string]
            [com.stuartsierra.component :as component])
  (:import (software.amazon.awssdk.regions Region)
           (software.amazon.awssdk.services.cloudformation CloudFormationClient)
           (software.amazon.awssdk.services.cloudformation.model CloudFormationException CreateStackRequest
                                                                 DeleteStackRequest DescribeStacksRequest Parameter
                                                                 UpdateStackRequest)))

(defn- parameters [network]
  (let [vpc-id     (protocols.network/vpc-id network)
        subnet-ids (protocols.network/subnet-ids network vpc-id)]
    [(-> (Parameter/builder)
         (.parameterKey "VpcId")
         (.parameterValue vpc-id)
         .build)
     (-> (Parameter/builder)
         (.parameterKey "Subnets")
         (.parameterValue (string/join "," subnet-ids))
         .build)]))

(defn- stack [cf-client stack-name]
  (let [stacks-req (-> (DescribeStacksRequest/builder)
                       (.stackName stack-name)
                       .build)]
    (->> stacks-req
         (.describeStacks cf-client)
         .stacks
         first)))

(defn- stack-exists? [cf-client stack-name]
  (try
    (stack cf-client stack-name)
    true
    (catch CloudFormationException _
      false)))

(defrecord CloudFormation [region cf-client credentials ec2-client network]
  component/Lifecycle
  (start [component]
    (if cf-client
      component
      (assoc component :cf-client (-> (CloudFormationClient/builder)
                                      (.credentialsProvider (protocols.credentials/credentials credentials))
                                      (.region region)
                                      .build))))

  (stop [component]
    (assoc component :cf-client nil))

  protocols.cloud-formation/CloudFormationStack
  (upsert [_ stack-name template]
    (let [parameters      (parameters network)
          stack-exists?   (stack-exists? cf-client stack-name)
          request-builder (if stack-exists? (UpdateStackRequest/builder) (CreateStackRequest/builder))
          request         (-> request-builder
                              (.stackName stack-name)
                              (.templateBody template)
                              (.parameters parameters)
                              .build)]
      (if stack-exists?
        (.updateStack cf-client request)
        (.createStack cf-client request))))

  (delete [_ stack-name]
    (let [request (-> (DeleteStackRequest/builder)
                      (.stackName stack-name)
                      .build)]
      (.deleteStack cf-client request)))

  (outputs [_ stack-name]
    (->> (stack cf-client stack-name)
         .outputs
         (map (fn [output] [(.outputKey output) (.outputValue output)]))
         (into {}))))

(defn new-cf-client [region]
  (map->CloudFormation {:region region}))

(comment
  (require '[cf-up-and-running.components.credentials :as cred])
  (require '[cf-up-and-running.components.ec2 :as ec2])
  (require '[cf-up-and-running.components.network :as network])
  (require '[clojure.java.io :as io])
  (def region Region/US_EAST_2)
  (def stack-name "example-stack")

  (def system (component/start (component/system-map
                                :cf-client (component/using (new-cf-client region) [:credentials :ec2-client :network])
                                :credentials (cred/new-credentials)
                                :ec2-client (component/using (ec2/new-ec2-client region) [:credentials])
                                :network (component/using (network/new-network) [:ec2-client]))))
  (def cf-client (:cf-client system))

  (protocols.cloud-formation/upsert cf-client stack-name (slurp (io/resource "cf_template.json")))
  (protocols.cloud-formation/delete cf-client stack-name)

  (protocols.cloud-formation/outputs cf-client stack-name))
