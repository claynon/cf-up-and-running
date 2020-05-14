(ns cf-up-and-running.components.network
  (:require [cf-up-and-running.protocols.ec2 :as protocols.ec2]
            [cf-up-and-running.protocols.network :as protocols.network]
            [com.stuartsierra.component :as component])
  (:import (software.amazon.awssdk.services.ec2.model DescribeSubnetsRequest DescribeVpcsRequest Filter)))

(defrecord Network [ec2-client]
  component/Lifecycle
  (start [component]
    component)
  (stop [component]
    component)

  protocols.network/Network
  (vpc-id [component]
    (let [vpc-request (-> (DescribeVpcsRequest/builder)
                          (.filters [(-> (Filter/builder)
                                         (.name "isDefault")
                                         (.values ["true"])
                                         .build)])
                          .build)]
      (-> (.describeVpcs (protocols.ec2/client ec2-client) vpc-request)
          .vpcs
          first
          .vpcId)))

  (subnet-ids [component vpc-id]
    (let [subnet-request (-> (DescribeSubnetsRequest/builder)
                             (.filters [(-> (Filter/builder)
                                            (.name "vpc-id")
                                            (.values [vpc-id])
                                            .build)])
                             .build)]
      (->> (.describeSubnets (protocols.ec2/client ec2-client) subnet-request)
           .subnets
           (map #(.subnetId %))))))

(defn new-network []
  (map->Network {}))

(comment
  (require '[cf-up-and-running.components.credentials :as cred])
  (require '[cf-up-and-running.components.ec2 :as ec2])
  (import (software.amazon.awssdk.regions Region))

  (def region Region/US_EAST_2)

  (def system (component/start (component/system-map
                                :network (component/using (new-network) [:ec2-client])
                                :credentials (cred/new-credentials)
                                :ec2-client (component/using (ec2/new-ec2-client region) [:credentials]))))

  (def vpc-id (protocols.network/vpc-id (:network system)))
  (def subnet-ids (protocols.network/subnet-ids (:network system) vpc-id)))
