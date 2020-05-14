(ns cf-up-and-running.components.cloud-formation
  (:require [cf-up-and-running.protocols.credentials :as protocols.credentials]
            [cf-up-and-running.protocols.cloud-formation :as protocols.cloud-formation]
            [cf-up-and-running.protocols.network :as protocols.network]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [com.stuartsierra.component :as component])
  (:import (software.amazon.awssdk.regions Region)
           (software.amazon.awssdk.services.cloudformation CloudFormationClient)
           (software.amazon.awssdk.services.cloudformation.model CloudFormationException CreateStackRequest
                                                                 DeleteStackRequest DescribeStacksRequest Parameter
                                                                 UpdateStackRequest)))

(defn- parameters [parameters-map]
  (map (fn [[k v]] (-> (Parameter/builder)
                       (.parameterKey k)
                       (.parameterValue v)
                       .build))
       parameters-map))

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

(let [oi "abc"]
  (string/replace "alo ${abd} asdf" (str "${" oi "}") "alo"))

(defn- interpolate-user-data [user-data params]
  (reduce (fn [user-data' [k v]]
            (string/replace user-data' (str "${" k "}") v))
          user-data
          params))

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
  (upsert [_ stack-name template parameters-map]
    (let [user-data       (interpolate-user-data (slurp (io/resource "user_data_cf.sh")) parameters-map)
          params          (parameters (assoc parameters-map "UserData" user-data))
          stack-exists?   (stack-exists? cf-client stack-name)
          request-builder (if stack-exists? (UpdateStackRequest/builder) (CreateStackRequest/builder))
          request         (-> request-builder
                              (.stackName stack-name)
                              (.templateBody template)
                              (.parameters params)
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
  (def region Region/US_EAST_2)
  (def stack-name "example-stack")

  (def system (component/start (component/system-map
                                :cf-client (component/using (new-cf-client region) [:credentials :ec2-client :network])
                                :credentials (cred/new-credentials)
                                :ec2-client (component/using (ec2/new-ec2-client region) [:credentials])
                                :network (component/using (network/new-network) [:ec2-client]))))
  (def cf-client (:cf-client system))

  (let [vpc-id     (protocols.network/vpc-id (:network system))
        subnet-ids (protocols.network/subnet-ids (:network system) vpc-id)]
    (protocols.cloud-formation/upsert cf-client
                                      stack-name
                                      (slurp (io/resource "cf_template.json"))
                                      {"VpcId"         vpc-id
                                       "Subnets"       (string/join "," subnet-ids)
                                       "WebServerPort" "8080"}))
  (protocols.cloud-formation/delete cf-client stack-name)

  (protocols.cloud-formation/outputs cf-client stack-name))
