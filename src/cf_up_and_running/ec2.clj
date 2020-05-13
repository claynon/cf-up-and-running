(ns cf-up-and-running.ec2
  (:require [cf-up-and-running.credentials :as credentials]
            [com.stuartsierra.component :as component])
  (:import (java.util Base64)
           (software.amazon.awssdk.regions Region)
           (software.amazon.awssdk.services.ec2 Ec2Client)
           (software.amazon.awssdk.services.ec2.model AuthorizeSecurityGroupIngressRequest CreateSecurityGroupRequest
                                                      CreateTagsRequest DeleteSecurityGroupRequest InstanceType
                                                      IpPermission IpRange RunInstancesRequest Tag
                                                      TerminateInstancesRequest)))


(defprotocol Ec2Instance
  (create [component name sg-name user-data])
  (terminate [component instance-id]))

(defprotocol SecurityGroup
  (create-sg [component name])
  (terminate-sg [component name]))

;;TODO move to protocols namespace and renameprotocl methods

(defrecord Ec2ClientABC [credentials region ec2-client]
  component/Lifecycle
  (start [component]
    (if ec2-client
      component
      (assoc component :ec2-client (-> (Ec2Client/builder)
                                       (.region region)
                                       (.credentialsProvider (:credential credentials)) ;;TODO change this :credential to a protocol
                                       .build))))
  (stop [component]
    (if (not ec2-client)
      component
      (assoc component :ec2-client nil)))

  SecurityGroup
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


  Ec2Instance
  (create [component name sg-name user-data]
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

  (terminate [component instance-id]
    (let [request (-> (TerminateInstancesRequest/builder)
                      (.instanceIds [instance-id])
                      .build)]
      (.terminateInstances ec2-client request))))

(defn new-ec2-client [credentials region] ;;TODO move credentials to be dependency
  (map->Ec2ClientABC {:credentials credentials
                      :region      region}))

(comment
  (def credentials (component/start (credentials/new-credentials)))
  (def ec2-client (component/start (new-ec2-client credentials Region/US_EAST_2)))

  (def sg-name "cf-example-sg")
  (def sg (create-sg ec2-client sg-name))

  (require '[clojure.java.io :as io])
  (def user-data (slurp (io/resource "user_data.sh")))
  (def instance-id (create ec2-client "cf-example-instance" sg-name user-data))

  (terminate ec2-client instance-id)
  (terminate-sg ec2-client sg-name))
