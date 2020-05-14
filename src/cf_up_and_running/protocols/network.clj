(ns cf-up-and-running.protocols.network)

(defprotocol Network
  (vpc-id [component])
  (subnet-ids [component vpc-id]))
