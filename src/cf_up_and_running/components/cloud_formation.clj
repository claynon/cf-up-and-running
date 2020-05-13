(ns cf-up-and-running.components.cloud-formation
  (:require [cf-up-and-running.protocols.credentials :as protocols.credentials]
            [cf-up-and-running.protocols.cloud-formation :as protocols.cloud-formation]
            [cf-up-and-running.protocols.ec2 :as protocols.ec2]
            [clojure.string :as string]
            [com.stuartsierra.component :as component])
  (:import(software.amazon.awssdk.regions Region)
           (software.amazon.awssdk.services.cloudformation CloudFormationClient)
           (software.amazon.awssdk.services.cloudformation.model CreateStackRequest DeleteStackRequest
                                                                 DescribeStacksRequest Parameter UpdateStackRequest)
           (software.amazon.awssdk.services.ec2.model DescribeSubnetsRequest DescribeVpcsRequest Filter)))

(defn- parameters [ec2-component]
  (let [vpc-id  (-> (.describeVpcs (:ec2-client ec2-component) (-> (DescribeVpcsRequest/builder)
                                                                   (.filters [(-> (Filter/builder)
                                                                                  (.name "isDefault")
                                                                                  (.values ["true"])
                                                                                  .build)])
                                                                   .build))
                    .vpcs
                    first
                    .vpcId)
        subnets (-> (.describeSubnets (:ec2-client ec2-component) (-> (DescribeSubnetsRequest/builder)
                                                                      (.filters [(-> (Filter/builder)
                                                                                     (.name "vpc-id")
                                                                                     (.values [vpc-id])
                                                                                     .build)])
                                                                      .build))
                    .subnets)];;TODO extract this to a network component
    [(-> (Parameter/builder)
         (.parameterKey "VpcId")
         (.parameterValue vpc-id)
         .build)
     (-> (Parameter/builder)
         (.parameterKey "Subnets")
         (.parameterValue (string/join "," (map #(.subnetId %) subnets)))
         .build)]))

(defrecord CloudFormation [cf-client region credentials ec2-client]
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
  (create [_ stack-name template]
    (let [parameters (parameters ec2-client)
          request    (-> (CreateStackRequest/builder)
                         (.stackName stack-name)
                         (.templateBody template)
                         (.parameters parameters)
                         .build)]
      (.createStack cf-client request)))

  (update [_ stack-name template]
    (let [parameters (parameters ec2-client)
          request    (-> (UpdateStackRequest/builder)
                         (.stackName stack-name)
                         (.templateBody template)
                         (.parameters parameters)
                         .build)]
      (.updateStack cf-client request)))

  (delete [_ stack-name]
    (let [request (-> (DeleteStackRequest/builder)
                      (.stackName stack-name)
                      .build)]
      (.deleteStack cf-client request)))

  (outputs [_ stack-name]
    (let [stacks-req (-> (DescribeStacksRequest/builder)
                         (.stackName stack-name)
                         .build)]
      (->> stacks-req
           (.describeStacks cf-client)
           .stacks
           first
           .outputs
           (map (fn [output] {(.outputKey output) (.outputValue output)}))))))

(defn new-cf-client [region credentials ec2-client] ;;TODO move cred and ec2-c as dependencies
  (map->CloudFormation {:region      region
                        :credentials credentials
                        :ec2-client  ec2-client}))

(comment
  (require '[cf-up-and-running.components.credentials :as cred])
  (require '[cf-up-and-running.components.ec2 :as ec2])
  (require '[clojure.java.io :as io])
  (def region Region/US_EAST_2)
  (def stack-name "example-stack")

  (def credentials (component/start (cred/new-credentials)))
  (def ec2-client (component/start (ec2/new-ec2-client credentials region)))
  (def cf-client (component/start (new-cf-client region credentials ec2-client)))

  (protocols.cloud-formation/create cf-client stack-name (slurp (io/resource "cf_template.json")))
  (protocols.cloud-formation/update cf-client stack-name (slurp (io/resource "cf_template.json")))
  (protocols.cloud-formation/delete cf-client stack-name)

  (protocols.cloud-formation/outputs cf-client stack-name))
