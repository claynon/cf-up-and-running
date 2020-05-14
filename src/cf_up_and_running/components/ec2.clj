(ns cf-up-and-running.components.ec2
  (:require [cf-up-and-running.protocols.credentials :as protocols.credentials]
            [cf-up-and-running.protocols.ec2 :as protocols.ec2]
            [cf-up-and-running.protocols.security-group :as protocols.security-group]
            [com.stuartsierra.component :as component])
  (:import (java.util Base64)
           (software.amazon.awssdk.regions Region)
           (software.amazon.awssdk.services.ec2 Ec2Client)
           (software.amazon.awssdk.services.ec2.model AuthorizeSecurityGroupIngressRequest CreateSecurityGroupRequest
                                                      CreateTagsRequest DeleteSecurityGroupRequest InstanceType
                                                      IpPermission IpRange RunInstancesRequest Tag
                                                      TerminateInstancesRequest)))

(defrecord Ec2 [ec2-client region credentials]
  component/Lifecycle
  (start [component]
    (if ec2-client
      component
      (assoc component :ec2-client (-> (Ec2Client/builder)
                                       (.region region)
                                       (.credentialsProvider (protocols.credentials/credentials credentials))
                                       .build))))
  (stop [component]
    (assoc component :ec2-client nil))

  protocols.ec2/Ec2Client
  (client [_]
    ec2-client)

  protocols.security-group/SecurityGroup
  (create-sg [_ name]
    (let [create-request (-> (CreateSecurityGroupRequest/builder)
                             (.groupName name)
                             (.description "sg of the example instance")
                             .build)
          ip-range       (-> (IpRange/builder)
                             (.cidrIp "0.0.0.0/0")
                             .build)
          ip-permission  (-> (IpPermission/builder)
                             (.ipProtocol "tcp")
                             (.toPort (int 8080))
                             (.fromPort (int 8080))
                             (.ipRanges [ip-range])
                             .build)
          auth-request   (-> (AuthorizeSecurityGroupIngressRequest/builder)
                             (.groupName name)
                             (.ipPermissions [ip-permission])
                             .build)
          security-group (.createSecurityGroup ec2-client create-request)]
      (.authorizeSecurityGroupIngress ec2-client auth-request)
      security-group))

  (terminate-sg [_ name]
    (let [delete-request (-> (DeleteSecurityGroupRequest/builder)
                             (.groupName name)
                             .build)]
      (.deleteSecurityGroup ec2-client delete-request)))


  protocols.ec2/Ec2Instance
  (create [_ name sg-name user-data]
    (let [request     (-> (RunInstancesRequest/builder)
                          (.imageId "ami-0c55b159cbfafe1f0")
                          (.instanceType InstanceType/T2_MICRO)
                          (.maxCount (int 1))
                          (.minCount (int 1))
                          (.securityGroups [sg-name])
                          (.userData (.encodeToString (Base64/getEncoder) (.getBytes user-data)))
                          .build)
          instances   (->> request
                           (.runInstances ec2-client)
                           .instances)
          instance-id (.instanceId (.get instances 0))
          tag         (-> (Tag/builder)
                          (.key "Name")
                          (.value name)
                          .build)
          tag-request (-> (CreateTagsRequest/builder)
                          (.resources [instance-id])
                          (.tags [tag])
                          .build)]
      (.createTags ec2-client tag-request)
      instance-id))

  (terminate [_ instance-id]
    (let [request (-> (TerminateInstancesRequest/builder)
                      (.instanceIds [instance-id])
                      .build)]
      (.terminateInstances ec2-client request))))

(defn new-ec2-client [region]
  (map->Ec2 {:region region}))

(comment
  (require '[cf-up-and-running.components.credentials :as cred])
  (def system (component/start (component/system-map
                                :credentials (cred/new-credentials)
                                :ec2-client (component/using (new-ec2-client Region/US_EAST_2) [:credentials]))))

  (def sg-name "cf-example-sg")
  (def sg (protocols.security-group/create-sg (:ec2-client system) sg-name))

  (require '[clojure.java.io :as io])
  (def user-data (slurp (io/resource "user_data.sh")))
  (def instance-id (protocols.ec2/create (:ec2-client system) "cf-example-instance" sg-name user-data))

  (protocols.ec2/terminate (:ec2-client system) instance-id)
  (protocols.security-group/terminate-sg (:ec2-client system) sg-name))
